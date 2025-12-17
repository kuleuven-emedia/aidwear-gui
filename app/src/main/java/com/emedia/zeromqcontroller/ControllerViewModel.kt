package com.emedia.zeromqcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.zeromq.SocketType
import org.zeromq.ZMQ

// Helper to find modes by name
sealed class ControllerMode(val protocolName: String) {
    object Transparent : ControllerMode("Transparent")
    object Walking : ControllerMode("Walking")
    object SitToStance : ControllerMode("Sit to Stance")
    object StairAscent : ControllerMode("Stair Ascent")
    object StairDescent : ControllerMode("Stair Descent")

    companion object {
        fun fromProtocolName(name: String): ControllerMode? {
            return when(name) {
                Transparent.protocolName -> Transparent
                Walking.protocolName -> Walking
                SitToStance.protocolName -> SitToStance
                StairAscent.protocolName -> StairAscent
                StairDescent.protocolName -> StairDescent
                else -> null
            }
        }
    }
}

class ControllerViewModel(application: Application) : AndroidViewModel(application) {

    private var zmqClient: ZmqClient? = null

    private val _response = MutableStateFlow("Ready")
    val response: StateFlow<String> = _response.asStateFlow()

    private val _mode = MutableStateFlow<ControllerMode>(ControllerMode.Transparent)
    val mode: StateFlow<ControllerMode> = _mode.asStateFlow()

    private val _assistiveLevel = MutableStateFlow(0)
    val assistiveLevel: StateFlow<Int> = _assistiveLevel.asStateFlow()

    private val _pendingModeSwitch = MutableStateFlow<ControllerMode?>(null)
    val pendingModeSwitch: StateFlow<ControllerMode?> = _pendingModeSwitch.asStateFlow()

    private val _pendingFatigueSwitch = MutableStateFlow<Int?>(null)
    val pendingFatigueSwitch: StateFlow<Int?> = _pendingFatigueSwitch.asStateFlow()

    private var listenerJob: Job? = null

    // -------------------------------------------------------
    // NEW: VIBRATION HELPER
    // -------------------------------------------------------
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
                // Vibrate for 500 milliseconds (half a second)
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            println("Vibration failed: ${e.message}")
        }
    }

    fun configureNetwork(address: String) {
        _response.value = "Configured: $address"

        // Init the Client (Sends commands)
        viewModelScope.launch {
            zmqClient = ZmqClient(address)
            // connect() here just opens the local socket. It does NOT wait for the server.
            zmqClient?.connect()
        }

        // Init the Listener (Receives updates)
        startPassiveListener(address)
    }
    fun startPassiveListener(ipAddress: String) {
        // If we are already listening to this IP, don't restart
        if (listenerJob?.isActive == true) return

        listenerJob?.cancel()

        listenerJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Logic to get the correct listening port
                val cleanIp = if (ipAddress.startsWith("tcp://")) ipAddress.substring(6) else ipAddress
                val baseIp = cleanIp.split(":")[0]
                val subAddress = "tcp://$baseIp:${ZmqConfig.LISTENING_PORT}"

                println("LISTENER: Starting background listener on $subAddress...")

                val context = ZMQ.context(1)
                val subscriber = context.socket(SocketType.SUB)
                subscriber.connect(subAddress)
                subscriber.subscribe("")

                while (isActive) {
                    val message = subscriber.recvStr(ZMQ.DONTWAIT)
                    if (message != null) {
                        println("LISTENER: Received -> $message")
                        handleIncomingCommand(message)
                    }
                    kotlinx.coroutines.delay(100)
                }
                subscriber.close()
                context.term()
            } catch (e: Exception) {
                println("LISTENER: Failed -> ${e.message}")
            }
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
        }
        else if (command.startsWith("REQUEST_FATIGUE ")) {
            try {
                val newLevel = command.removePrefix("REQUEST_FATIGUE ").trim().toInt()
                if (newLevel != _assistiveLevel.value) {
                    _pendingFatigueSwitch.value = newLevel
                    shouldVibrate = true
                }
            } catch (e: Exception) {}
        }

        // TRIGGER VIBRATION IF A POPUP APPEARED
        if (shouldVibrate) {
            triggerVibration()
        }
    }

    fun acceptFatigueSwitch() {
        _pendingFatigueSwitch.value?.let { newLevel ->
            // Update the main Assistance Level
            _assistiveLevel.value = newLevel
            // Tell the robot we updated the level
            sendMessage("SET_ASSIST_LEVEL $newLevel")
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
        sendMessage("SET_MODE ${newMode.protocolName}")
    }

    fun changeAssistiveLevel(delta: Int) {
        val newLevel = (_assistiveLevel.value + delta).coerceIn(0, 10)
        _assistiveLevel.value = newLevel
        sendMessage("SET_ASSIST_LEVEL $newLevel")
    }

    fun sendMessage(command: String) {
        // We no longer check "if (isConnected)". We just try to send.
        viewModelScope.launch {
            // zmqClient might be null if configureNetwork wasn't called yet
            if (zmqClient == null) {
                _response.value = "Error: No IP Configured"
                return@launch
            }

            val reply = zmqClient?.sendMessage(command)

            if (reply != null) {
                _response.value = "Sent: $command (Ack)"
            } else {
                _response.value = "Timeout: Server not responding"
            }
        }
    }

    override fun onCleared() {
        zmqClient?.close()
        super.onCleared()
    }
}