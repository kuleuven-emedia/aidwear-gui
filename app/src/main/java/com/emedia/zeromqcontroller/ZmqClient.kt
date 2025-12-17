package com.emedia.zeromqcontroller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.zeromq.SocketType
import org.zeromq.ZMQ
import java.lang.Exception

class ZmqClient(private val rawAddress: String) {

    private val context = ZMQ.context(1)
    private var socket: ZMQ.Socket? = null

    // Mutex makes this Thread-Safe. Only one thread can use the socket at a time.
    private val mutex = Mutex()

    // Ensure we default to COMMAND_PORT (6000), NOT Listening Port (6001)
    private val fullAddress: String
        get() {
            var addr = rawAddress.trim()
            if (!addr.startsWith("tcp://")) addr = "tcp://$addr"

            // Check if user provided a port manually
            if (!addr.contains(":[0-9]+".toRegex())) {
                addr = "$addr:${ZmqConfig.COMMAND_PORT}" // MUST BE 6000
            }
            return addr
        }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            initSocket()
        }
        return@withContext true
    }

    private fun initSocket() {
        try {
            socket?.close()

            println("ZMQ: Connecting to $fullAddress")
            socket = context.socket(SocketType.REQ)
            socket?.linger = 0
            socket?.receiveTimeOut = 2000 // 2s timeout
            socket?.connect(fullAddress)
        } catch (e: Exception) {
            println("ZMQ Init Error: ${e.message}")
        }
    }

    suspend fun sendMessage(message: String): String? = withContext(Dispatchers.IO) {
        // LOCK THE SOCKET (Prevents the crash when clicking fast)
        mutex.withLock {
            if (socket == null) initSocket()

            return@withLock try {
                val currentSocket = socket ?: return@withLock null

                println("ZMQ: Sending '$message' to $fullAddress")

                val sendSuccess = currentSocket.send(message.toByteArray(ZMQ.CHARSET), 0)
                if (!sendSuccess) {
                    println("ZMQ: Send failed, resetting...")
                    initSocket()
                    return@withLock null
                }

                val reply = currentSocket.recv(0)
                if (reply == null) {
                    println("ZMQ Error: Timeout. Server didn't reply on $fullAddress")
                    initSocket()
                    return@withLock null
                }

                val replyStr = String(reply, ZMQ.CHARSET)
                println("ZMQ: Received '$replyStr'")
                replyStr

            } catch (e: Exception) {
                println("ZMQ Exception: ${e.message}")
                initSocket()
                null
            }
        }
    }

    fun close() {
        try {
            socket?.close()
            context.term()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}