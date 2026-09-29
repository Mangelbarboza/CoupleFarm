package com.example.couplefarm.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.couplefarm.R
import com.example.couplefarm.game.FarmerStyle
import com.example.couplefarm.network.DEFAULT_LAN_PORT
import com.example.couplefarm.network.DiscoveredFarm
import com.example.couplefarm.network.LanScanner
import com.example.couplefarm.network.getLocalIpAddress
import com.example.couplefarm.network.normalizeRoomCode

private val Cream = Color(0xFFFFF4D6)
private val Golden = Color(0xFFF3B83E)
private val DarkGreenBg = Color(0xDD1B3322)
private val BorderGold = Color(0xAAFFE7A0)
private val HudBrown = Color(0xFF5A3E26)
private val TabActive = Color(0xFFE2C485)
private val TabInactive = Color(0x661B3322)

private enum class JoinTab {
    ONLINE,
    LAN,
}

@Composable
fun JoinLanScreen(
    farmerStyle: FarmerStyle,
    onJoinLan: (hostIp: String, hostPort: Int) -> Unit,
    onJoinOnline: (roomCode: String) -> Unit,
    onBack: () -> Unit,
) {
    var currentTab by remember { mutableStateOf(JoinTab.ONLINE) }
    var onlineCodeInput by remember { mutableStateOf("") }

    var discoveredFarms by remember { mutableStateOf<List<DiscoveredFarm>>(emptyList()) }
    var manualIp by remember {
        val local = getLocalIpAddress()
        val prefix = if (local != null && local.contains(".")) {
            local.substring(0, local.lastIndexOf('.') + 1)
        } else {
            "192.168.1."
        }
        mutableStateOf(prefix)
    }

    DisposableEffect(currentTab) {
        if (currentTab == JoinTab.LAN) {
            val scanner = LanScanner { farms ->
                discoveredFarms = farms
            }
            scanner.start()
            onDispose {
                scanner.close()
            }
        } else {
            onDispose {}
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painterResource(R.drawable.farm_menu_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x55000000), Color.Transparent, Color(0xAA0D261A))))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "MULTIJUGADOR",
                    color = Cream,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "UNIRSE A AMIGO",
                    color = Golden,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(8.dp))

                // Tab selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x55000000), RoundedCornerShape(12.dp))
                        .border(2.dp, BorderGold, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TabButton(
                        text = "🌐 ONLINE (CÓDIGO)",
                        isSelected = currentTab == JoinTab.ONLINE,
                        onClick = { currentTab = JoinTab.ONLINE },
                        modifier = Modifier.weight(1f),
                    )
                    TabButton(
                        text = "📡 RED LOCAL (WI-FI)",
                        isSelected = currentTab == JoinTab.LAN,
                        onClick = { currentTab = JoinTab.LAN },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Central tab content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 10.dp)
                    .background(DarkGreenBg, RoundedCornerShape(18.dp))
                    .border(2.dp, BorderGold, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
                    label = "tabContent",
                ) { tab ->
                    when (tab) {
                        JoinTab.ONLINE -> {
                            OnlineCodeJoinContent(
                                code = onlineCodeInput,
                                onCodeChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) onlineCodeInput = it },
                                onConnect = {
                                    val clean = normalizeRoomCode(onlineCodeInput)
                                    if (clean.length == 4) {
                                        onJoinOnline(clean)
                                    }
                                },
                            )
                        }
                        JoinTab.LAN -> {
                            LanScannerContent(
                                discoveredFarms = discoveredFarms,
                                manualIp = manualIp,
                                onManualIpChange = { manualIp = it },
                                onJoin = onJoinLan,
                            )
                        }
                    }
                }
            }

            // Back button
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x66000000),
                    contentColor = Cream,
                ),
            ) {
                Text("‹ VOLVER AL MENÚ PRINCIPAL", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .background(
                if (isSelected) TabActive else TabInactive,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isSelected) HudBrown else Cream.copy(alpha = 0.8f),
            fontWeight = FontWeight.Black,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun OnlineCodeJoinContent(
    code: String,
    onCodeChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "INGRESA EL CÓDIGO DE SALA",
                color = Golden,
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Pídele a tu amigo el código de 4 dígitos que aparece en Ajustes (⚙) de su granja",
                color = Cream.copy(alpha = 0.85f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))

            // 4-box display
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 4) {
                    val digit = code.getOrNull(i)?.toString() ?: ""
                    Box(
                        modifier = Modifier
                            .size(54.dp, 60.dp)
                            .background(Color(0x55000000), RoundedCornerShape(10.dp))
                            .border(
                                width = if (i == code.length) 2.5.dp else 1.5.dp,
                                color = if (i == code.length) Golden else BorderGold.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = digit,
                            color = Golden,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Hidden / Compact real text field for keyboard support
            TextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier
                    .width(220.dp)
                    .height(40.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    color = Cream,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0x33000000),
                    unfocusedContainerColor = Color(0x22000000),
                    focusedIndicatorColor = Golden,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                placeholder = {
                    Text(
                        "O toca aquí para teclado",
                        color = Cream.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }

        // On-screen numeric keypad for instant touch-friendliness
        Column(
            modifier = Modifier.fillMaxWidth(0.9f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "⌫"),
            )

            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (key in row) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .background(Color(0x44FFFFFF), RoundedCornerShape(8.dp))
                                .border(1.dp, BorderGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .clickable {
                                    when (key) {
                                        "C" -> onCodeChange("")
                                        "⌫" -> if (code.isNotEmpty()) onCodeChange(code.dropLast(1))
                                        else -> if (code.length < 4) onCodeChange(code + key)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = key,
                                color = Cream,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }

        // Connect button
        Button(
            onClick = onConnect,
            enabled = code.length == 4,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Golden,
                contentColor = Color(0xFF3D2A13),
                disabledContainerColor = Color(0x44FFFFFF),
                disabledContentColor = Color(0x66FFFFFF),
            ),
        ) {
            Text(
                if (code.length == 4) "🚀 ENTRAR A LA GRANJA" else "INGRESA LOS 4 DÍGITOS",
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun LanScannerContent(
    discoveredFarms: List<DiscoveredFarm>,
    manualIp: String,
    onManualIpChange: (String) -> Unit,
    onJoin: (hostIp: String, hostPort: Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "GRANJAS EN TU WI-FI",
                color = Golden,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                if (discoveredFarms.isEmpty()) "Buscando..." else "${discoveredFarms.size} encontrada(s)",
                color = Cream.copy(alpha = 0.8f),
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        if (discoveredFarms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0x33000000), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "📡 Buscando señales en la red local...",
                        color = Cream,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pídele a tu amigo que abra su granja, vaya a Ajustes (⚙) y active \"Juego LAN\"",
                        color = Cream.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(discoveredFarms) { farm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xDD3B5E3C), RoundedCornerShape(12.dp))
                            .border(2.dp, Golden, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🌾 ${farm.name}",
                                color = Cream,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                            )
                            Text(
                                "IP: ${farm.hostAddress} · Día ${farm.day}",
                                color = Cream.copy(alpha = 0.75f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Button(
                            onClick = { onJoin(farm.hostAddress, farm.port) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Golden,
                                contentColor = Color(0xFF3D2A13),
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("UNIRSE", fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Manual IP connection
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x44000000), RoundedCornerShape(12.dp))
                .padding(10.dp),
        ) {
            Text(
                "O CONEXIÓN DIRECTA POR IP:",
                color = Color(0xFFCDE2C7),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = manualIp,
                    onValueChange = onManualIpChange,
                    modifier = Modifier.weight(1f).height(46.dp),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Cream,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0x66000000),
                        unfocusedContainerColor = Color(0x44000000),
                        focusedIndicatorColor = Golden,
                        unfocusedIndicatorColor = Color(0x66FFFFFF),
                    ),
                    placeholder = { Text("192.168.x.x", color = Color.Gray, fontSize = 11.sp) },
                )
                Button(
                    onClick = {
                        if (manualIp.isNotBlank()) {
                            onJoin(manualIp, DEFAULT_LAN_PORT)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF557A55),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.height(46.dp),
                ) {
                    Text("ENTRAR", fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }
    }
}
