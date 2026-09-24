package com.emedia.aidwearcontroller

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Helper to find modes by name
sealed class ControllerMode(val protocolName: String, val id: Byte) {
    object Idle : ControllerMode("Idle", 0)
    object Walking : ControllerMode("Walking", 1)
    object SitToStand : ControllerMode("Sit to Stand", 2)
    object StairAscent : ControllerMode("Stair Ascent", 3)
    object StairDescent : ControllerMode("Stair Descent", 4)
    object Hurdle : ControllerMode("Hurdle", 5)

    companion object {
        fun fromProtocolName(name: String): ControllerMode? {
            return when(name) {
                Idle.protocolName -> Idle
                Walking.protocolName -> Walking
                SitToStand.protocolName -> SitToStand
                StairAscent.protocolName -> StairAscent
                StairDescent.protocolName -> StairDescent
                Hurdle.protocolName -> Hurdle
                else -> null
            }
        }
    }
}

class ControllerViewModel(application: Application) : AndroidViewModel(application) {

    private val tcpServer = TcpServer(ServerConfig.SERVER_PORT)

    private val _response = MutableStateFlow("Waiting for connection...")
    val response: StateFlow<String> = _response.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _mode = MutableStateFlow<ControllerMode>(ControllerMode.Idle)
    val mode: StateFlow<ControllerMode> = _mode.asStateFlow()

    private val _assistiveLevel = MutableStateFlow(0)
    val assistiveLevel: StateFlow<Int> = _assistiveLevel.asStateFlow()

    private val _pendingModeSwitch = MutableStateFlow<ControllerMode?>(null)
    val pendingModeSwitch: StateFlow<ControllerMode?> = _pendingModeSwitch.asStateFlow()

    private val _pendingFatigueSwitch = MutableStateFlow<Int?>(null)
    val pendingFatigueSwitch: StateFlow<Int?> = _pendingFatigueSwitch.asStateFlow()

    // New State for Logs
    private val _serverLogs = MutableStateFlow<List<String>>(emptyList())
    val serverLogs: StateFlow<List<String>> = _serverLogs.asStateFlow()

    init {
        tcpServer.start()

        viewModelScope.launch {
            tcpServer.isHandshakeComplete.collect { ready ->
                _isReady.value = ready
                _response.value = if (ready) "Handshake OK: Client connected" else "Waiting for GO..."
            }
        }

        viewModelScope.launch {
            tcpServer.incomingMessages.collect { message ->
                handleIncomingCommand(message)
            }
        }

        // Collect Logs from TcpServer
        viewModelScope.launch {
            tcpServer.serverLogs.collect { newLog ->
                // Keep the last 50 logs
                _serverLogs.value = (_serverLogs.value + newLog).takeLast(50)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun triggerVibration() {
        try {
            val context = getApplication<Application>().applicationContext
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            println("Vibration failed: ${e.message}")
        }
    }

    private fun handleIncomingCommand(command: String) {
        var shouldVibrate = false
        if (command.startsWith("REQUEST_MODE ")) {
            val modeName = command.removePrefix("REQUEST_MODE ").trim()
            val requestedMode = ControllerMode.fromProtocolName(modeName)
            if (requestedMode != null && requestedMode != _mode.value) {
                _pendingModeSwitch.value = requestedMode
                shouldVibrate = true
            }
        } else if (command.startsWith("REQUEST_FATIGUE ")) {
            try {
                val newLevel = command.removePrefix("REQUEST_FATIGUE ").trim().toInt()
                if (newLevel != _assistiveLevel.value) {
                    _pendingFatigueSwitch.value = newLevel
                    shouldVibrate = true
                }
            } catch (e: Exception) {}
        }

        if (shouldVibrate) triggerVibration()
    }

    fun acceptFatigueSwitch() {
        _pendingFatigueSwitch.value?.let { newLevel ->
            _assistiveLevel.value = newLevel
            sendFatigue(newLevel)
        }
        _pendingFatigueSwitch.value = null
    }

    fun rejectFatigueSwitch() {
        _pendingFatigueSwitch.value = null
    }

    fun acceptSwitch() {
        _pendingModeSwitch.value?.let { newMode ->
            updateMode(newMode)
        }
        _pendingModeSwitch.value = null
    }

    fun rejectSwitch() {
        _pendingModeSwitch.value = null
    }

    fun updateMode(newMode: ControllerMode) {
        _mode.value = newMode
        sendIntent(newMode.id)
    }

    fun changeAssistiveLevel(delta: Int) {
        val newLevel = (_assistiveLevel.value + delta).coerceIn(0, 10)
        _assistiveLevel.value = newLevel
        sendFatigue(newLevel)
    }

    private fun sendIntent(modeId: Byte) {
        viewModelScope.launch {
            val success = tcpServer.sendBinaryMessage(MSG_TYPE_INTENT, modeId)
            if (success) _response.value = "Sent Intent: $modeId"
        }
    }

    private fun sendFatigue(level: Int) {
        viewModelScope.launch {
            val success = tcpServer.sendBinaryMessage(MSG_TYPE_FATIGUE, level.toByte())
            if (success) _response.value = "Sent Fatigue: $level"
        }
    }

    fun triggerSafetyStop() {
        _mode.value = ControllerMode.Idle
        triggerVibration()
        viewModelScope.launch {
            val success = tcpServer.sendBinaryMessage(MSG_TYPE_SAFETY_STOP, 0.toByte())
            if (success) _response.value = "Sent Safety Stop"
        }
    }

    fun calibrateMotors() {
        viewModelScope.launch {
            val success = tcpServer.sendBinaryMessage(MSG_TYPE_CALIBRATE_MOTORS, 0.toByte())
            if (success) _response.value = "Sent Calibrate Motors"
        }
    }

    fun calibrateImus() {
        viewModelScope.launch {
            val success = tcpServer.sendBinaryMessage(MSG_TYPE_CALIBRATE_IMUS, 0.toByte())
            if (success) _response.value = "Sent Calibrate IMUs"
        }
    }

    override fun onCleared() {
        tcpServer.stop()
        super.onCleared()
    }

    companion object {
        const val MSG_TYPE_INTENT: Byte = 0
        const val MSG_TYPE_FATIGUE: Byte = 1
        const val MSG_TYPE_SAFETY_STOP: Byte = 2
        const val MSG_TYPE_CALIBRATE_MOTORS: Byte = 3
        const val MSG_TYPE_CALIBRATE_IMUS: Byte = 4
    }
}
