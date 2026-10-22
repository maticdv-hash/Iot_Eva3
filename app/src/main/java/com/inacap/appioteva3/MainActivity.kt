package com.inacap.appioteva3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.inacap.appioteva3.ui.screens.DashboardScreen
import com.inacap.appioteva3.ui.screens.LoginScreen
import com.inacap.appioteva3.ui.theme.APPIotEVA3Theme
import com.inacap.appioteva3.viewmodel.IoTViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: IoTViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            APPIotEVA3Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
                    val role by viewModel.role.collectAsState()
                    val port by viewModel.port.collectAsState()
                    val hostIp by viewModel.hostIp.collectAsState()
                    val isConnected by viewModel.isConnected.collectAsState()
                    val temperature by viewModel.temperature.collectAsState()
                    val humidity by viewModel.humidity.collectAsState()
                    val logs by viewModel.logs.collectAsState()
                    val localIp by viewModel.localIp.collectAsState()

                    if (!isLoggedIn) {
                        LoginScreen(
                            onLoginSuccess = { user, pass ->
                                viewModel.login(user, pass)
                            }
                        )
                    } else {
                        DashboardScreen(
                            role = role,
                            port = port,
                            hostIp = hostIp,
                            isConnected = isConnected,
                            temperature = temperature,
                            humidity = humidity,
                            logs = logs,
                            localIp = localIp,
                            onRoleChanged = { viewModel.setRole(it) },
                            onPortChanged = { viewModel.updatePort(it) },
                            onHostIpChanged = { viewModel.updateHostIp(it) },
                            onStartConnection = { viewModel.startConnection() },
                            onStopConnection = { viewModel.stopConnection() },
                            onLogout = { viewModel.logout() }
                        )
                    }
                }
            }
        }
    }
}
