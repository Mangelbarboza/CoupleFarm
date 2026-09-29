package com.example.couplefarm.game

import androidx.compose.ui.graphics.Color

data class FarmerStyle(val name: String = "Alex", val skinIndex: Int = 1, val shirtIndex: Int = 0, val hairIndex: Int = 0)

val SkinColors = listOf(Color(0xFFFFD0A6), Color(0xFFD99562), Color(0xFF825236), Color(0xFF4A3027))
val ShirtColors = listOf(Color(0xFF4A90E2), Color(0xFFE85D75), Color(0xFF7B61A8), Color(0xFFF0A442))
val HairColors = listOf(Color(0xFF5A3825), Color(0xFFE8BC62), Color(0xFF2F2928), Color(0xFFB85C38))

enum class AppScreen { MENU, CUSTOMIZE, FARM, JOIN_LAN }
data class Chicken(val x: Float, val y: Float, val phase: Float)
data class Egg(val x: Float, val y: Float)
