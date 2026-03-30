package com.emedia.revalexotcpcontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : ComponentActivity() {
    private val viewModel: ControllerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- FULLSCREEN LOGIC ---
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        // ------------------------

        setContent {
            TcpControllerApp(viewModel = viewModel)
        }
    }
}

fun getLocalIpAddress(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in Collections.list(interfaces)) {
            val addrs = intf.inetAddresses
            for (addr in Collections.list(addrs)) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress ?: "Unknown"
                }
            }
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
    return "No Network"
}

@Composable
fun TcpControllerApp(viewModel: ControllerViewModel) {
    var currentStrings by remember { mutableStateOf<AppStrings>(EnStrings) }
    val isReady by viewModel.isReady.collectAsState()

    CompositionLocalProvider(LocalAppStrings provides currentStrings) {
        val onLanguageChange: (String) -> Unit = { code ->
            currentStrings = when (code) {
                "nl" -> NlStrings
                "fr" -> FrStrings
                else -> EnStrings
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            ControllerScreen(
                viewModel = viewModel,
                onLanguageChange = onLanguageChange
            )

            if (!isReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .zIndex(20f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        "Waiting for Handshake (GO)...",
                        color = Color.White,
                        modifier = Modifier.padding(top = 80.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControllerScreen(
    viewModel: ControllerViewModel,
    onLanguageChange: (String) -> Unit
) {
    val currentMode by viewModel.mode.collectAsState()
    val assistiveLevel by viewModel.assistiveLevel.collectAsState()
    val pendingSwitch by viewModel.pendingModeSwitch.collectAsState()
    val pendingFatigue by viewModel.pendingFatigueSwitch.collectAsState()
    val logs by viewModel.serverLogs.collectAsState()
    val isReady by viewModel.isReady.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showIpDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val modes = listOf(
        ControllerMode.Walking,
        ControllerMode.StairAscent,
        ControllerMode.SitToStance,
        ControllerMode.StairDescent,
        ControllerMode.Transparent
    )

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // --- MAIN CONTENT ---
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // LEFT SIDE (MODES)
                Column(
                    modifier = Modifier.weight(0.65f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigModeButton(modes[0], currentMode, viewModel, isReady, Modifier.weight(1f))
                        BigModeButton(modes[1], currentMode, viewModel, isReady, Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigModeButton(modes[2], currentMode, viewModel, isReady, Modifier.weight(1f))
                        BigModeButton(modes[3], currentMode, viewModel, isReady, Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        BigModeButton(modes[4], currentMode, viewModel, isReady, Modifier.weight(1f))
                    }
                }

                // RIGHT SIDE (CONTROLS)
                Column(
                    modifier = Modifier.weight(0.35f).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Button(
                        onClick = { viewModel.changeAssistiveLevel(1) },
                        enabled = isReady,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        BoxWithConstraints(contentAlignment = Alignment.Center) {
                            val iconSize = with(LocalDensity.current) { (maxHeight.toPx() * 0.6f).toSp() }
                            Text("+", fontSize = iconSize, fontWeight = FontWeight.Bold)
                        }
                    }

                    Column(
                        modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(assistiveLevel.toString(), fontSize = 60.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(" / 10", fontSize = 24.sp, modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { assistiveLevel / 10f },
                            modifier = Modifier.fillMaxWidth(0.9f).height(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                        Text(AppRes.strings.assistiveLevel, fontSize = 16.sp, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = { viewModel.changeAssistiveLevel(-1) },
                        enabled = isReady,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        BoxWithConstraints(contentAlignment = Alignment.Center) {
                            val iconSize = with(LocalDensity.current) { (maxHeight.toPx() * 0.6f).toSp() }
                            Text("-", fontSize = iconSize, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- SETTINGS OVERLAY ---
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp)
            ) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.Settings, "Settings", modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text(AppRes.strings.connectionSettings) }, onClick = { showMenu = false; showIpDialog = true })
                    DropdownMenuItem(text = { Text(AppRes.strings.language) }, onClick = { showMenu = false; showLanguageDialog = true })
                }
            }

            if (pendingSwitch != null) {
                ModeChangeRequestOverlay(
                    currentMode = currentMode,
                    newMode = pendingSwitch!!,
                    onAccept = { viewModel.acceptSwitch() },
                    onReject = { viewModel.rejectSwitch() }
                )
            }
            if (pendingFatigue != null) {
                FatigueChangeRequestOverlay(
                    currentLevel = assistiveLevel,
                    newLevel = pendingFatigue!!,
                    onAccept = { viewModel.acceptFatigueSwitch() },
                    onReject = { viewModel.rejectFatigueSwitch() }
                )
            }
        }
    }

    if (showIpDialog) {
        val myDeviceIp = remember { getLocalIpAddress() }

        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text(AppRes.strings.connectionSettings) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text(text = AppRes.strings.deviceIp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(text = myDeviceIp, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = AppRes.strings.listeningPort, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        Text(text = ServerConfig.SERVER_PORT.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    
                    Text("Server Logs:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                    Box(modifier = Modifier.height(150.dp).fillMaxWidth().background(Color.Black).padding(4.dp)) {
                        LogView(logs = logs, modifier = Modifier.fillMaxSize())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIpDialog = false }) { Text(AppRes.strings.close) }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(AppRes.strings.language) },
            text = {
                Column {
                    TextButton(onClick = { onLanguageChange("en"); showLanguageDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("English") }
                    TextButton(onClick = { onLanguageChange("nl"); showLanguageDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Nederlands") }
                    TextButton(onClick = { onLanguageChange("fr"); showLanguageDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Français") }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text(AppRes.strings.close) } }
        )
    }
}

@Composable
fun LogView(logs: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    LazyColumn(state = listState, modifier = modifier) {
        items(logs) { log ->
            Text(text = log, color = Color.LightGray, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

@Composable
fun BigModeButton(
    mode: ControllerMode,
    currentMode: ControllerMode,
    viewModel: ControllerViewModel,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isSelected = currentMode == mode
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer

    Button(
        onClick = { viewModel.updateMode(mode) },
        enabled = enabled,
        modifier = modifier.fillMaxHeight(),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = textColor,
            disabledContainerColor = bgColor.copy(alpha = 0.5f),
            disabledContentColor = textColor.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(4.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 0.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val dynamicFontSize = with(LocalDensity.current) { (maxHeight.toPx() * 0.25f).toSp() }
            Text(text = AppRes.strings.getModeName(mode), fontSize = dynamicFontSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = dynamicFontSize * 1.1f, maxLines = 2)
        }
    }
}

@Composable
fun ModeChangeRequestOverlay(
    currentMode: ControllerMode,
    newMode: ControllerMode,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).zIndex(10f).padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight(0.95f)) {
            Text(text = AppRes.strings.modeChangeRequest, style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = AppRes.strings.from, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text(text = AppRes.strings.getModeName(currentMode), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Change to", modifier = Modifier.size(48.dp).padding(horizontal = 8.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = AppRes.strings.to, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text(text = AppRes.strings.getModeName(newMode), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Button(onClick = onReject, modifier = Modifier.weight(1f).fillMaxHeight(), shape = MaterialTheme.shapes.extraLarge, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError), elevation = ButtonDefaults.buttonElevation(8.dp)) {
                    Text(text = AppRes.strings.reject, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Button(onClick = onAccept, modifier = Modifier.weight(1f).fillMaxHeight(), shape = MaterialTheme.shapes.extraLarge, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White), elevation = ButtonDefaults.buttonElevation(8.dp)) {
                    Text(text = AppRes.strings.accept, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun FatigueChangeRequestOverlay(
    currentLevel: Int,
    newLevel: Int,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).zIndex(11f).padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxHeight(0.95f)) {
            Text(text = AppRes.strings.fatigueRequest, style = MaterialTheme.typography.headlineMedium, color = Color(0xFFFFC107), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = AppRes.strings.from, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text(text = "$currentLevel", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                    Icon(imageVector = Icons.Default.Warning, contentDescription = "Change to", modifier = Modifier.size(48.dp).padding(horizontal = 8.dp), tint = Color(0xFFFFC107))
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = AppRes.strings.to, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        Text(text = "$newLevel", style = MaterialTheme.typography.displayLarge, color = Color(0xFFFFC107), fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Button(onClick = onReject, modifier = Modifier.weight(1f).fillMaxHeight(), shape = MaterialTheme.shapes.extraLarge, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                    Text(text = AppRes.strings.reject, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onAccept, modifier = Modifier.weight(1f).fillMaxHeight(), shape = MaterialTheme.shapes.extraLarge, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White)) {
                    Text(text = AppRes.strings.accept, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
