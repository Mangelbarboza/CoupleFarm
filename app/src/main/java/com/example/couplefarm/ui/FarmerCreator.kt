package com.example.couplefarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.couplefarm.R
import com.example.couplefarm.game.*

@Composable
fun FarmerCreator(style: FarmerStyle, onStyleChange: (FarmerStyle) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF8EAC8)).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TU GRANJERO", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color(0xFF294936))
        Text("Haz que se parezca a ti", color = Color(0xFF68806B))
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(180.dp).background(Color(0xFFA9D889), RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
            PixelFarmer(style, Modifier.size(145.dp))
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            style.name, { if (it.length <= 14) onStyleChange(style.copy(name = it)) },
            Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Estilo clásico · pronto agregaremos más apariencias",
            color = Color(0xFF68806B), fontSize = 13.sp
        )
        Spacer(Modifier.weight(1f))
        Button(onBack, Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF376447))) {
            Text("GUARDAR", fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ColorSelector(title: String, colors: List<Color>, selected: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF455D49))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colors.forEachIndexed { i, color ->
                Box(Modifier.size(if (selected == i) 38.dp else 32.dp).background(if (selected == i) Color.White else Color.Transparent, CircleShape)
                    .padding(4.dp).background(color, CircleShape).clickable { onSelected(i) })
            }
        }
    }
}

@Composable
fun PixelFarmer(style: FarmerStyle, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.farmer_front_v2),
        contentDescription = "Granjero ${style.name}",
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}
