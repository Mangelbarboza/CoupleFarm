package com.example.couplefarm.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Minijuego de pesca autocontenido inspirado en la barra vertical de los juegos de granja.
 * Mantener pulsada la barra eleva la zona verde; soltarla permite que caiga.
 */
@Composable
fun FishingMiniGame(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    difficulty: Float = 1f
) {
    var holding by remember { mutableStateOf(false) }
    var fishPosition by remember { mutableFloatStateOf(.38f) }
    var catchPosition by remember { mutableFloatStateOf(.72f) }
    var catchVelocity by remember { mutableFloatStateOf(0f) }
    var progress by remember { mutableFloatStateOf(.10f) }
    var completed by remember { mutableStateOf(false) }
    val randomPhase = remember { Random.nextFloat() * 7f }
    val currentSuccess by rememberUpdatedState(onSuccess)
    val clampedDifficulty = difficulty.coerceIn(.55f, 2f)
    val zoneSize = (0.25f / clampedDifficulty).coerceIn(.14f, .33f)

    LaunchedEffect(clampedDifficulty) {
        var lastFrame = withFrameNanos { it }
        var elapsed = 0f

        while (!completed) {
            val frame = withFrameNanos { it }
            val dt = ((frame - lastFrame) / 1_000_000_000f).coerceIn(0f, .05f)
            lastFrame = frame
            elapsed += dt

            // Dos ondas hacen que el pez cambie de ritmo sin movimientos bruscos.
            fishPosition = (
                .5f +
                    sin(elapsed * (1.55f + clampedDifficulty * .42f) + randomPhase) * .29f +
                    sin(elapsed * 4.1f + randomPhase * .37f) * (.045f * clampedDifficulty)
                ).coerceIn(.075f, .925f)

            val acceleration = if (holding) -2.48f else 1.88f
            catchVelocity += acceleration * dt
            catchVelocity *= exp(-1.35f * dt)
            catchPosition += catchVelocity * dt

            val halfZone = zoneSize / 2f
            if (catchPosition < halfZone) {
                catchPosition = halfZone
                catchVelocity = 0f
            } else if (catchPosition > 1f - halfZone) {
                catchPosition = 1f - halfZone
                catchVelocity = 0f
            }

            val touchingFish = abs(fishPosition - catchPosition) <= halfZone + .025f
            val gain = .24f / clampedDifficulty
            progress = (progress + if (touchingFish) gain * dt else -.16f * dt).coerceIn(0f, 1f)

            if (progress >= 1f && !completed) {
                completed = true
                currentSuccess()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xB8253023))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(260.dp)
                .background(Color(0xFFF5E5AE), RoundedCornerShape(14.dp))
                .border(4.dp, Color(0xFF513822), RoundedCornerShape(14.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "¡PESCANDO!",
                color = Color(0xFF3F5E32),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                fontSize = 21.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (holding) "MANTÉN • SUBIR" else "SUELTA • BAJAR",
                color = Color(0xFF76543A),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FishingBar(
                    fishPosition = fishPosition,
                    catchPosition = catchPosition,
                    zoneSize = zoneSize,
                    holding = holding,
                    onHoldingChanged = { holding = it }
                )
                Spacer(Modifier.width(13.dp))
                CatchProgress(progress = progress, modifier = Modifier.height(326.dp).width(26.dp))
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "Mantén pulsada la barra para seguir al pez",
                color = Color(0xFF654B34),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFFD66B4E), RoundedCornerShape(7.dp))
                    .border(3.dp, Color(0xFF653827), RoundedCornerShape(7.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 24.dp, vertical = 9.dp)
                    .semantics { contentDescription = "Cancelar pesca" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "CANCELAR",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun FishingBar(
    fishPosition: Float,
    catchPosition: Float,
    zoneSize: Float,
    holding: Boolean,
    onHoldingChanged: (Boolean) -> Unit
) {
    Canvas(
        modifier = Modifier
            .size(width = 108.dp, height = 326.dp)
            .semantics { contentDescription = "Barra de pesca, mantener para subir" }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHoldingChanged(true)
                        try {
                            tryAwaitRelease()
                        } finally {
                            onHoldingChanged(false)
                        }
                    }
                )
            }
    ) {
        val border = 6f
        val trackLeft = 13f
        val trackRight = size.width - 13f
        val trackTop = 12f
        val trackBottom = size.height - 12f
        val trackHeight = trackBottom - trackTop

        drawRect(Color(0xFF4D3827), Offset(trackLeft - border, trackTop - border), Size(trackRight - trackLeft + border * 2f, trackHeight + border * 2f))
        drawRect(Color(0xFF75B9D1), Offset(trackLeft, trackTop), Size(trackRight - trackLeft, trackHeight))

        // Pequeñas franjas de agua animadas visualmente por la posición del pez.
        val waterLine = Color(0x6659A0BC)
        repeat(8) { index ->
            val y = trackTop + (index + .6f) * trackHeight / 8f
            val inset = if (index % 2 == 0) 9f else 18f
            drawRect(waterLine, Offset(trackLeft + inset, y), Size(trackRight - trackLeft - inset * 2f, 3f))
        }

        val zoneHeight = trackHeight * zoneSize
        val zoneCenter = trackTop + trackHeight * catchPosition
        val zoneTop = (zoneCenter - zoneHeight / 2f).coerceIn(trackTop, trackBottom - zoneHeight)
        val zoneColor = if (holding) Color(0xCCB6E06D) else Color(0xB894C85B)
        drawRect(zoneColor, Offset(trackLeft + 3f, zoneTop), Size(trackRight - trackLeft - 6f, zoneHeight))
        drawRect(Color(0xFF476B34), Offset(trackLeft + 3f, zoneTop), Size(trackRight - trackLeft - 6f, zoneHeight), style = Stroke(width = 4f))

        val fishY = trackTop + trackHeight * fishPosition
        val fishX = size.width / 2f
        val fish = Path().apply {
            moveTo(fishX - 24f, fishY)
            lineTo(fishX - 36f, fishY - 10f)
            lineTo(fishX - 36f, fishY + 10f)
            close()
        }
        drawPath(fish, Color(0xFFF0A13F))
        drawOval(Color(0xFFFFC85A), Offset(fishX - 25f, fishY - 11f), Size(42f, 22f))
        drawRect(Color(0xFF53372A), Offset(fishX + 8f, fishY - 5f), Size(4f, 4f))
        drawRect(Color(0xFFFFE6A0), Offset(fishX - 13f, fishY - 8f), Size(11f, 4f))
    }
}

@Composable
private fun CatchProgress(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val border = 5f
        drawRect(Color(0xFF4D3827), Offset.Zero, size)
        drawRect(Color(0xFFEEE3B6), Offset(border, border), Size(size.width - border * 2f, size.height - border * 2f))

        val availableHeight = size.height - border * 2f
        val filledHeight = availableHeight * progress.coerceIn(0f, 1f)
        val fillColor = when {
            progress > .72f -> Color(0xFF76BB4C)
            progress > .35f -> Color(0xFFE5B94A)
            else -> Color(0xFFD76A4D)
        }
        drawRect(
            fillColor,
            Offset(border, size.height - border - filledHeight),
            Size(size.width - border * 2f, filledHeight)
        )

        repeat(5) { index ->
            val y = border + availableHeight * index / 5f
            drawRect(Color(0x554D3827), Offset(border, y), Size(size.width - border * 2f, 2f))
        }
    }
}
