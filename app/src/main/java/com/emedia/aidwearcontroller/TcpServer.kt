package com.emedia.aidwearcontroller

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class TcpServer(private val port: Int) {

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    // Counter for outgoing messages (Unsigned 32-bit)
    private var messageCounter: UInt = 0u

    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages: SharedFlow<String> = _incomingMessages

    private val _isHandshakeComplete = MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val isHandshakeComplete: SharedFlow<Boolean> = _isHandshakeComplete

    private val _serverLogs = MutableSharedFlow<String>(replay = 50, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val serverLogs: SharedFlow<String> = _serverLogs

    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private suspend fun log(message: String) {
        println("TCP SERVER: $message")
        _serverLogs.emit(message)
    }

    fun start() {
        if (serverJob?.isActive == true) return

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                serverSocket?.reuseAddress = true
                log("Listening on port $port")

                while (isActive) {
                    try {
                        log("Waiting for connection...")
                        val socket = serverSocket?.accept() ?: break
                        handleClient(socket)
                    } catch (e: SocketException) {
                        if (isActive) log("Socket error: ${e.message}")
                    } catch (e: Exception) {
                        log("Error accepting connection: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                log("Could not start server: ${e.message}")
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        withContext(Dispatchers.IO) {
            clientSocket = socket
            outputStream = socket.getOutputStream()
            inputStream = socket.getInputStream()

            log("Client connected: ${socket.inetAddress.hostAddress}")

            try {
                // Reset counter on new connection
                messageCounter = 0u
                
                // Handshake: Wait for "GO"
                val buffer = ByteArray(1024)
                val bytesRead = inputStream?.read(buffer) ?: -1
                if (bytesRead != -1) {
                    val message = String(buffer, 0, bytesRead, Charsets.UTF_8).trim()
                    if (message == "GO") {
                        log("Handshake successful (Received GO)")
                        _isHandshakeComplete.emit(true)
                        
                        while (isActive) {
                            val nextBytes = inputStream?.read(buffer) ?: -1
                            if (nextBytes == -1) break
                            val nextMsg = String(buffer, 0, nextBytes, Charsets.UTF_8).trim()
                            _incomingMessages.emit(nextMsg)
                        }
                    } else {
                        log("Handshake failed (Received: $message)")
                    }
                }
            } catch (e: Exception) {
                log("Connection error: ${e.message}")
            } finally {
                log("Client disconnected")
                cleanupClient()
                _isHandshakeComplete.emit(false)
            }
        }
    }

    suspend fun sendBinaryMessage(type: Byte, value: Byte): Boolean = withContext(Dispatchers.IO) {
        try {
            val out = outputStream
            if (out != null) {
                // Increment counter
                messageCounter++

                // Capture system time (milliseconds since epoch)
                val timestamp = System.currentTimeMillis()
                
                // Construct 14-byte message: [Type, Value, Counter(4), Timestamp(8)]
                val data = ByteArray(14)
                data[0] = type
                data[1] = value
                
                // Pack 32-bit UInt Counter (Big Endian)
                data[2] = (messageCounter shr 24).toByte()
                data[3] = (messageCounter shr 16).toByte()
                data[4] = (messageCounter shr 8).toByte()
                data[5] = (messageCounter).toByte()

                // Pack 64-bit Long Timestamp (Big Endian)
                data[6] = (timestamp shr 56).toByte()
                data[7] = (timestamp shr 48).toByte()
                data[8] = (timestamp shr 40).toByte()
                data[9] = (timestamp shr 32).toByte()
                data[10] = (timestamp shr 24).toByte()
                data[11] = (timestamp shr 16).toByte()
                data[12] = (timestamp shr 8).toByte()
                data[13] = (timestamp).toByte()
                
                out.write(data)
                out.flush()
                log("Sent binary: [Type: $type, Value: $value, Counter: $messageCounter, Time: $timestamp]")
                return@withContext true
            } else {
                log("Cannot send, no client connected")
                return@withContext false
            }
        } catch (e: Exception) {
            log("Send error: ${e.message}")
            return@withContext false
        }
    }

    private fun cleanupClient() {
        try {
            outputStream?.close()
            inputStream?.close()
            clientSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        outputStream = null
        inputStream = null
        clientSocket = null
    }

    fun stop() {
        serverJob?.cancel()
        cleanupClient()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        println("TCP SERVER: Stopped")
    }
}
