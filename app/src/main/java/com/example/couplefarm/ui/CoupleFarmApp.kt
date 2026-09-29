package com.example.couplefarm.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.couplefarm.game.AppScreen
import com.example.couplefarm.game.FarmerStyle

@Composable
fun CoupleFarmApp(farmerStyle: FarmerStyle, onFarmerChange: (FarmerStyle) -> Unit) {
    var screenName by rememberSaveable { mutableStateOf(AppScreen.MENU.name) }
    var lanJoinHost by rememberSaveable { mutableStateOf<String?>(null) }
    var lanJoinPort by rememberSaveable { mutableStateOf(8888) }
    var onlineRoomCode by rememberSaveable { mutableStateOf<String?>(null) }

    AnimatedContent(
        targetState = AppScreen.valueOf(screenName),
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
        label = "screen"
    ) { destination ->
        when (destination) {
            AppScreen.MENU -> MainMenu(
                farmerStyle = farmerStyle,
                onPlay = {
                    lanJoinHost = null
                    onlineRoomCode = null
                    screenName = AppScreen.FARM.name
                },
                onJoinFriend = { screenName = AppScreen.JOIN_LAN.name },
                onCustomize = { screenName = AppScreen.CUSTOMIZE.name },
            )
            AppScreen.CUSTOMIZE -> FarmerCreator(farmerStyle, onFarmerChange) { screenName = AppScreen.MENU.name }
            AppScreen.JOIN_LAN -> JoinLanScreen(
                farmerStyle = farmerStyle,
                onJoinLan = { hostIp, hostPort ->
                    lanJoinHost = hostIp
                    lanJoinPort = hostPort
                    onlineRoomCode = null
                    screenName = AppScreen.FARM.name
                },
                onJoinOnline = { code ->
                    onlineRoomCode = code
                    lanJoinHost = null
                    screenName = AppScreen.FARM.name
                },
                onBack = { screenName = AppScreen.MENU.name }
            )
            AppScreen.FARM -> FarmScreen(
                farmerStyle = farmerStyle,
                lanJoinHost = lanJoinHost,
                lanJoinPort = lanJoinPort,
                onlineRoomCode = onlineRoomCode,
                onBack = {
                    lanJoinHost = null
                    onlineRoomCode = null
                    screenName = AppScreen.MENU.name
                }
            )
        }
    }
}
