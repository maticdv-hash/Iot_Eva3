package com.inacap.appioteva3.viewmodel

import android.app.Application
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inacap.appioteva3.network.NetworkHelper
import com.inacap.appioteva3.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale

class IoTViewModel(application: Application) : AndroidViewModel(application) {
    private val securityManager = SecurityManager(application)

    private val _isLoggedIn = MutableStateFlow(securityManager.isLoggedIn())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _role = MutableStateFlow("Host") // "Host" or "Client"
    val role: StateFlow<String> = _role.asStateFlow()

    private val _port = MutableStateFlow("8080")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _hostIp = MutableStateFlow("192.168.1.100")
    val hostIp: StateFlow<String> = _hostIp.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _temperature = MutableStateFlow("-- °C")
    val temperature: StateFlow<String> = _temperature.asStateFlow()

    private val _humidity = MutableStateFlow("-- %")
    val humidity: StateFlow<String> = _humidity.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _localIp = MutableStateFlow(NetworkHelper.getLocalIpAddress())
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private var networkJob: Job? = null
    private var serverSocket: ServerSocket? = null

    fun login(user: String, pass: String): Boolean {
        if (user.isNotBlank() && pass.isNotBlank()) {
            securityManager.setLoggedIn(true)
            securityManager.saveSessionToken("iot_secure_token_${System.currentTimeMillis()}")
            _isLoggedIn.value = true
            addLog("Login exitoso. Sesión iniciada de forma segura.")
            return true
        }
        return false
    }

    fun logout() {
        stopConnection()
        securityManager.clearSession()
        _isLoggedIn.value = false
        addLog("Sesión cerrada.")
    }

    fun setRole(newRole: String) {
        if (!_isConnected.value) {
            _role.value = newRole
            addLog("Rol cambiado a: $newRole")
        }
    }

    fun updatePort(newPort: String) {
        if (!_isConnected.value) {
            _port.value = newPort
        }
    }

    fun updateHostIp(newIp: String) {
        if (!_isConnected.value) {
            _hostIp.value = newIp
        }
    }

    fun startConnection() {
        if (_role.value == "Host") {
            startServer()
        } else {
            startClient()
        }
    }

    private fun startServer() {
        val portNum = _port.value.toIntOrNull() ?: 8080
        networkJob?.cancel()
        networkJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(portNum)
                _isConnected.value = true
                addLog("Servidor TCP iniciado en puerto $portNum. Esperando conexiones...")

                while (isActive) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        addLog("Cliente conectado desde: ${clientSocket.inetAddress.hostAddress}")
                        
                        launch(Dispatchers.IO) {
                            try {
                                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    line?.let { data ->
                                        addLog("RX: $data")
                                        parseTelemetry(data)
                                    }
                                }
                            } catch (e: Exception) {
                                addLog("Error en flujo de cliente: ${e.localizedMessage}")
                            } finally {
                                clientSocket.close()
                                addLog("Cliente desconectado.")
                            }
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                        addLog("Error aceptando conexión: ${e.localizedMessage}")
                    }
                }
            } catch (e: Exception) {
                addLog("Error iniciando servidor: ${e.localizedMessage}")
                _isConnected.value = false
            }
        }
    }

    private fun startClient() {
        val ip = _hostIp.value.trim()
        val portNum = _port.value.toIntOrNull() ?: 8080
        networkJob?.cancel()
        networkJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                addLog("Conectando a servidor $ip:$portNum...")
                val socket = Socket(ip, portNum)
                _isConnected.value = true
                addLog("Conectado exitosamente al Host.")

                val writer = PrintWriter(socket.getOutputStream(), true)
                var counter = 1
                while (isActive && socket.isConnected) {
                    val simTemp = String.format(Locale.getDefault(), "%.1f", 20.0 + (Math.random() * 10.0))
                    val simHum = String.format(Locale.getDefault(), "%.1f", 40.0 + (Math.random() * 20.0))
                    val telemetry = "TEMP: $simTemp°C | HUM: $simHum% [SEQ: $counter]"
                    
                    writer.println(telemetry)
                    addLog("TX: $telemetry")
                    
                    _temperature.value = "$simTemp °C"
                    _humidity.value = "$simHum %"

                    counter++
                    delay(2000L)
                }
                socket.close()
            } catch (e: Exception) {
                addLog("Error de conexión cliente: ${e.localizedMessage}")
                _isConnected.value = false
            } finally {
                _isConnected.value = false
                addLog("Conexión de cliente detenida.")
            }
        }
    }

    fun stopConnection() {
        networkJob?.cancel()
        networkJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
        _isConnected.value = false
        addLog("Conexión detenida.")
    }

    private fun parseTelemetry(data: String) {
        try {
            if (data.contains("TEMP:") && data.contains("HUM:")) {
                val tempPart = data.substringAfter("TEMP:").substringBefore("|").trim()
                val humPart = data.substringAfter("HUM:").substringBefore("[").trim()
                _temperature.value = tempPart
                _humidity.value = humPart
            }
        } catch (_: Exception) {
            // Ignore parsing errors
        }
    }

    private fun addLog(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val timestamp = DateFormat.format("HH:mm:ss", System.currentTimeMillis())
            val entry = "[$timestamp] $message"
            _logs.value = listOf(entry) + _logs.value.take(49)
        }
    }
}
