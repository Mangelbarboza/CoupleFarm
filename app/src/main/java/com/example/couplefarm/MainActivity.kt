package com.example.couplefarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.couplefarm.game.FarmerStyle
import com.example.couplefarm.ui.CoupleFarmApp
import com.example.couplefarm.ui.theme.CoupleFarmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CoupleFarmTheme(darkTheme = false) {
                var farmerName by rememberSaveable { mutableStateOf("Alex") }
                var skinIndex by rememberSaveable { mutableStateOf(1) }
                var shirtIndex by rememberSaveable { mutableStateOf(0) }
                var hairIndex by rememberSaveable { mutableStateOf(0) }
                CoupleFarmApp(
                    FarmerStyle(farmerName, skinIndex, shirtIndex, hairIndex),
                    onFarmerChange = {
                        farmerName = it.name
                        skinIndex = it.skinIndex
                        shirtIndex = it.shirtIndex
                        hairIndex = it.hairIndex
                    }
                )
            }
        }
    }
}
