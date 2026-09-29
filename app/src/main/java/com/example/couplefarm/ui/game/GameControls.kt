package com.example.couplefarm.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.sqrt
import com.example.couplefarm.R

/** Dirección normalizada del joystick. Ambos ejes están siempre entre -1 y 1. */
data class JoystickVector(val x: Float, val y: Float) {
    companion object {
        val Zero = JoystickVector(0f, 0f)
    }
}

/** Herramientas disponibles en el control de acción inicial de la granja. */
enum class FarmTool(val displayName: String) {
    HAND("Mano"),
    FISHING_ROD("Caña"),
    AXE("Hacha"),
    PICKAXE("Pico"),
    MACHETE("Machete"),
    PET_BALL("Pelota"),
    PATH("Pala"),
    FENCE("Valla"),
    HOE("Azada"),
    WATERING_CAN("Regadera"),
    SEEDS("Semillas"),
    MILK_PAIL("Tarro"),
    ARCHITECT_PENCIL("Lápiz"),
    BLUEPRINT_DAIRY("Plano Lácteos"),
    BLUEPRINT_SLAUGHTERHOUSE("Plano Matadero"),
    BLUEPRINT_EGG_PACKER("Plano Huevos"),
    BLUEPRINT_VEGGIE_PACKER("Plano Verduras"),
    BLUEPRINT_BAKERY("Plano Panadería"),
    BLUEPRINT_FISH_PROCESSOR("Plano Fileteadora"),
    GATE("Puerta"),
}

/** The quick wheel is intentionally tools-only; consumables live in the backpack. */
private val PRIMARY_TOOL_BELT = listOf(
    FarmTool.HAND,
    FarmTool.FISHING_ROD,
    FarmTool.AXE,
    FarmTool.PICKAXE,
    FarmTool.MACHETE,
    FarmTool.HOE,
    FarmTool.WATERING_CAN,
    FarmTool.ARCHITECT_PENCIL,
)

/**
 * Capa de controles para colocar encima del mundo del juego.
 *
 * [onMove] recibe continuamente una dirección normalizada mientras el dedo toca
 * el joystick y (0, 0) al soltarlo. La velocidad final queda en manos del motor.
 */
@Composable
fun GameControls(
    selectedTool: FarmTool,
    onToolSelected: (FarmTool) -> Unit,
    onMove: (x: Float, y: Float) -> Unit,
    onAction: (FarmTool) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    durability: Int? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        AnalogJoystick(
            modifier = Modifier.align(Alignment.BottomStart),
            enabled = enabled,
            onDirectionChanged = { onMove(it.x, it.y) }
        )

        ToolActionControl(
            selectedTool = selectedTool,
            onToolSelected = onToolSelected,
            onAction = { onAction(selectedTool) },
            modifier = Modifier.align(Alignment.BottomEnd),
            enabled = enabled,
            durability = durability,
        )
    }
}

/** Joystick analógico táctil, radial y sin saltos discretos. */
@Composable
fun AnalogJoystick(
    onDirectionChanged: (JoystickVector) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 126.dp,
    enabled: Boolean = true
) {
    var direction by remember { mutableStateOf(JoystickVector.Zero) }
    val currentCallback by rememberUpdatedState(onDirectionChanged)

    fun publish(next: JoystickVector) {
        direction = next
        currentCallback(next)
    }

    LaunchedEffect(enabled) {
        if (!enabled && direction != JoystickVector.Zero) publish(JoystickVector.Zero)
    }

    Canvas(
        modifier = modifier
            .size(diameter)
            .semantics { contentDescription = "Joystick de movimiento" }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    fun updateFrom(position: Offset) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxTravel = min(size.width, size.height) * .31f
                        val dx = position.x - center.x
                        val dy = position.y - center.y
                        val length = sqrt(dx * dx + dy * dy)
                        val scale = if (length > maxTravel && length > 0f) maxTravel / length else 1f
                        val normalizedX = (dx * scale / maxTravel).coerceIn(-1f, 1f)
                        val normalizedY = (dy * scale / maxTravel).coerceIn(-1f, 1f)
                        publish(JoystickVector(normalizedX, normalizedY))
                    }

                    updateFrom(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        updateFrom(change.position)
                        change.consume()
                    }
                    publish(JoystickVector.Zero)
                }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * .47f
        val knobRadius = size.minDimension * .205f
        val travel = size.minDimension * .31f
        val knobCenter = center + Offset(direction.x * travel, direction.y * travel)

        // Sombra dura y doble borde para conservar la estética de sprite.
        drawCircle(Color(0x5522341F), baseRadius, center + Offset(0f, 7f))
        drawCircle(Color(0xEEDFC681), baseRadius, center)
        drawCircle(Color(0xFF533D27), baseRadius, center, style = Stroke(width = 7f))
        drawCircle(Color(0x88FFF2BE), baseRadius * .76f, center)
        drawCircle(Color(0x66533D27), baseRadius * .76f, center, style = Stroke(width = 3f))

        val tickColor = Color(0x88533D27)
        drawRect(tickColor, Offset(center.x - 3f, center.y - baseRadius * .66f), Size(6f, 12f))
        drawRect(tickColor, Offset(center.x - 3f, center.y + baseRadius * .66f - 12f), Size(6f, 12f))
        drawRect(tickColor, Offset(center.x - baseRadius * .66f, center.y - 3f), Size(12f, 6f))
        drawRect(tickColor, Offset(center.x + baseRadius * .66f - 12f, center.y - 3f), Size(12f, 6f))

        drawCircle(Color(0x5531261C), knobRadius, knobCenter + Offset(0f, 5f))
        drawCircle(if (enabled) Color(0xFF658C46) else Color(0xFF8A8A74), knobRadius, knobCenter)
        drawCircle(Color(0xFF314529), knobRadius, knobCenter, style = Stroke(width = 6f))
        drawCircle(Color(0x558FBC67), knobRadius * .60f, knobCenter - Offset(3f, 4f))
    }
}

/** Botón principal de acción más un panel compacto para escoger herramienta. */
@Composable
fun ToolActionControl(
    selectedTool: FarmTool,
    onToolSelected: (FarmTool) -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tools: List<FarmTool> = PRIMARY_TOOL_BELT,
    durability: Int? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
            exit = fadeOut() + scaleOut(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f))
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState())
                    .background(Color(0xF2F7E7B5), RoundedCornerShape(12.dp))
                    .border(3.dp, Color(0xFF573E28), RoundedCornerShape(12.dp))
                    .padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                tools.forEach { tool ->
                    ToolChoice(
                        tool = tool,
                        selected = tool == selectedTool,
                        onClick = {
                            onToolSelected(tool)
                            expanded = false
                        },
                        enabled = enabled
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                modifier = Modifier
                    .size(43.dp)
                    .background(Color(0xFFF0D88F), CircleShape)
                    .border(3.dp, Color(0xFF533D27), CircleShape)
                    .clickable(enabled = enabled) { expanded = !expanded }
                    .semantics { contentDescription = "Abrir herramientas" },
                contentAlignment = Alignment.Center
            ) {
                // Cuatro ranuras dibujadas como píxeles, sin depender de iconos externos.
                Canvas(Modifier.size(22.dp)) {
                    val cell = size.minDimension * .36f
                    val gap = size.minDimension * .09f
                    val start = (size.minDimension - cell * 2f - gap) / 2f
                    repeat(2) { row ->
                        repeat(2) { column ->
                            drawRect(
                                color = Color(0xFF5F7442),
                                topLeft = Offset(start + column * (cell + gap), start + row * (cell + gap)),
                                size = Size(cell, cell)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(82.dp)
                    .background(if (enabled && (durability == null || durability > 0)) Color(0xFFE2A34C) else Color(0xFF9C927D), CircleShape)
                    .border(5.dp, Color(0xFF573622), CircleShape)
                    .clickable(enabled = enabled, onClick = onAction)
                    .semantics { contentDescription = "Usar ${selectedTool.displayName}" },
                contentAlignment = Alignment.Center
            ) {
                PixelToolIcon(selectedTool, Modifier.size(47.dp))
                if (durability != null) {
                    val isBroken = durability <= 0
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = (-3).dp)
                            .background(if (isBroken) Color(0xFFD32F2F) else Color(0xDD3E2723), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (isBroken) "ROTO" else "$durability",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChoice(
    tool: FarmTool,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .width(142.dp)
            .background(
                if (selected) Color(0xFFFFE89B) else Color(0x00FFFFFF),
                RoundedCornerShape(7.dp)
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) Color(0xFFA7652B) else Color.Transparent,
                shape = RoundedCornerShape(7.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelToolIcon(tool, Modifier.size(29.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = tool.displayName.uppercase(),
            color = Color(0xFF493623),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = 11.sp
        )
    }
}

/** Iconos vectoriales deliberadamente cuadrados para mezclarse con sprites pixel art. */
@Composable
private fun PixelToolIcon(tool: FarmTool, modifier: Modifier = Modifier) {
    val resource = when (tool) {
        FarmTool.FISHING_ROD -> R.drawable.tool_rod_v3
        FarmTool.AXE -> R.drawable.tool_axe_v3
        FarmTool.PICKAXE -> R.drawable.tool_pickaxe_v4
        FarmTool.MACHETE -> R.drawable.item_machete_v5
        FarmTool.PET_BALL -> R.drawable.item_ball_v5
        FarmTool.PATH -> null
        FarmTool.FENCE -> R.drawable.fence_center_post_v8
        FarmTool.HOE -> R.drawable.tool_hoe_v3
        FarmTool.WATERING_CAN -> R.drawable.tool_watering_v3
        FarmTool.SEEDS -> R.drawable.tool_seeds_v3
        FarmTool.MILK_PAIL -> R.drawable.tool_milk_pail_v1
        FarmTool.ARCHITECT_PENCIL -> R.drawable.tool_pencil_v1
        FarmTool.BLUEPRINT_DAIRY -> R.drawable.workshop_dairy_v2
        FarmTool.BLUEPRINT_SLAUGHTERHOUSE -> R.drawable.workshop_slaughterhouse_v2
        FarmTool.BLUEPRINT_EGG_PACKER -> R.drawable.workshop_egg_packer_v2
        FarmTool.BLUEPRINT_VEGGIE_PACKER -> R.drawable.workshop_veggie_packer_v2
        FarmTool.BLUEPRINT_BAKERY -> R.drawable.workshop_bakery_v2
        FarmTool.BLUEPRINT_FISH_PROCESSOR -> R.drawable.workshop_fish_processor_v2
        FarmTool.GATE -> R.drawable.fence_gate_closed_v8
        else -> null
    }
    if (resource != null) {
        Image(
            painter = painterResource(resource),
            contentDescription = tool.displayName,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
        return
    }
    Canvas(modifier) {
        val u = size.minDimension / 16f
        translate((size.width - 16f * u) / 2f, (size.height - 16f * u) / 2f) {
            when (tool) {
                FarmTool.HAND -> {
                    val outline = Color(0xFF5A3422)
                    drawRect(outline, Offset(3f * u, 4f * u), Size(10f * u, 8f * u))
                    drawRect(outline, Offset(5f * u, 2f * u), Size(2f * u, 4f * u))
                    drawRect(outline, Offset(8f * u, 1f * u), Size(2f * u, 5f * u))
                    drawRect(outline, Offset(11f * u, 3f * u), Size(2f * u, 4f * u))
                    drawRect(Color(0xFFFFC982), Offset(4f * u, 5f * u), Size(8f * u, 6f * u))
                    drawRect(Color(0xFFFFC982), Offset(6f * u, 3f * u), Size(u, 3f * u))
                    drawRect(Color(0xFFFFC982), Offset(9f * u, 2f * u), Size(u, 4f * u))
                    drawRect(Color(0xFFFFC982), Offset(12f * u, 4f * u), Size(u, 3f * u))
                    drawRect(Color(0xFFE89C64), Offset(4f * u, 9f * u), Size(8f * u, 2f * u))
                }
                FarmTool.PATH -> {
                    val outline = Color(0xFF3E2A1D)
                    drawLine(outline, Offset(4f * u, 2f * u), Offset(11f * u, 11f * u), 3.2f * u, StrokeCap.Square)
                    drawLine(Color(0xFF9B6336), Offset(4f * u, 2f * u), Offset(11f * u, 11f * u), 1.7f * u, StrokeCap.Square)
                    val blade = Path().apply {
                        moveTo(8.2f * u, 10f * u)
                        lineTo(13.8f * u, 9.8f * u)
                        lineTo(14.5f * u, 14f * u)
                        lineTo(10.8f * u, 14.8f * u)
                        close()
                    }
                    drawPath(blade, Color(0xFFB8C4C6))
                    drawPath(blade, outline, style = Stroke(0.8f * u))
                }
                FarmTool.MILK_PAIL -> {
                    val steel = Color(0xFFECEFF1)
                    val steelDark = Color(0xFF78909C)
                    val outline = Color(0xFF37474F)
                    drawRect(outline, Offset(4f * u, 4f * u), Size(8f * u, 10f * u))
                    drawRect(steel, Offset(5f * u, 5f * u), Size(6f * u, 8f * u))
                    drawRect(steelDark, Offset(6f * u, 2f * u), Size(4f * u, 3f * u))
                    drawRect(outline, Offset(5f * u, 1f * u), Size(6f * u, 2f * u))
                }
                FarmTool.ARCHITECT_PENCIL -> {
                    drawRect(Color(0xFFFBC02D), Offset(5f * u, 4f * u), Size(6f * u, 8f * u))
                    drawRect(Color(0xFFE53935), Offset(5f * u, 1f * u), Size(6f * u, 3f * u))
                    drawRect(Color(0xFFB0BEC5), Offset(5f * u, 4f * u), Size(6f * u, u))
                    drawRect(Color(0xFF424242), Offset(6f * u, 12f * u), Size(4f * u, 3f * u))
                    drawRect(Color(0xFF212121), Offset(7f * u, 14f * u), Size(2f * u, 2f * u))
                }
                FarmTool.BLUEPRINT_DAIRY,
                FarmTool.BLUEPRINT_SLAUGHTERHOUSE,
                FarmTool.BLUEPRINT_EGG_PACKER,
                FarmTool.BLUEPRINT_VEGGIE_PACKER,
                FarmTool.BLUEPRINT_BAKERY -> {
                    drawRoundRect(Color(0xFF0277BD), Offset(2f * u, 2f * u), Size(12f * u, 12f * u), CornerRadius(2f * u, 2f * u))
                    drawRoundRect(Color(0xFFB3E5FC), Offset(2f * u, 2f * u), Size(12f * u, 12f * u), CornerRadius(2f * u, 2f * u), style = Stroke(u))
                    val badgeColor = when (tool) {
                        FarmTool.BLUEPRINT_DAIRY -> Color(0xFFFFF59D)
                        FarmTool.BLUEPRINT_SLAUGHTERHOUSE -> Color(0xFFEF5350)
                        FarmTool.BLUEPRINT_EGG_PACKER -> Color(0xFFFFEE58)
                        FarmTool.BLUEPRINT_VEGGIE_PACKER -> Color(0xFFFF7043)
                        FarmTool.BLUEPRINT_BAKERY -> Color(0xFFFFB300)
                        else -> Color.White
                    }
                    drawCircle(badgeColor, 3.2f * u, Offset(8f * u, 8f * u))
                    drawCircle(Color.White, 3.2f * u, Offset(8f * u, 8f * u), style = Stroke(0.8f * u))
                }
                else -> Unit
            }
        }
    }
}
