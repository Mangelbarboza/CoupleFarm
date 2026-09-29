package com.example.couplefarm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.couplefarm.R
import com.example.couplefarm.game.FarmerStyle

private val Cream = Color(0xFFFFF4D6)
private val Golden = Color(0xFFF3B83E)

@Composable
fun MainMenu(farmerStyle: FarmerStyle, onPlay: () -> Unit, onJoinFriend: () -> Unit, onCustomize: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.farm_menu_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color.Transparent, Color(0x990D261A)))))
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("COUPLE", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 7.sp)
                Text("FARM", color = Golden, fontSize = 58.sp, lineHeight = 58.sp, fontWeight = FontWeight.Black)
                Text("Una granja. Dos historias.", color = Cream, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(
                Modifier.fillMaxWidth().background(Color(0xDD213A2A), RoundedCornerShape(22.dp))
                    .border(2.dp, Color(0xAAFFE7A0), RoundedCornerShape(22.dp)).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelFarmer(farmerStyle, Modifier.size(70.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("La granja de", color = Color(0xFFCDE2C7), fontSize = 13.sp)
                        Text(farmerStyle.name.ifBlank { "Granjero" }, color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                FarmButton("ENTRAR A LA GRANJA", onPlay, true)
                Spacer(Modifier.height(8.dp))
                FarmButton("🌾 UNIRSE A AMIGO", onJoinFriend, false)
                Spacer(Modifier.height(8.dp))
                FarmButton("PERSONALIZAR GRANJERO", onCustomize, false)
            }
            Text("Tu progreso quedará listo para sincronizar con tu pareja", color = Color.White.copy(.84f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FarmButton(label: String, onClick: () -> Unit, primary: Boolean) {
    Button(
        onClick, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(if (primary) Golden else Color(0xFF557A55), if (primary) Color(0xFF3D2A13) else Color.White)
    ) { Text(label, fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
}
