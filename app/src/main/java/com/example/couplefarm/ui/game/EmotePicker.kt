package com.example.couplefarm.ui.game

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.couplefarm.R

/** Reacciones breves que el granjero puede mostrar sobre su cabeza. */
enum class FarmEmote(
    val displayName: String,
    @param:DrawableRes val spriteRes: Int,
) {
    HEART("Corazón", R.drawable.emote_heart_v4),
    SMILE("Sonrisa", R.drawable.emote_smile_v4),
    WAVE("Saludo", R.drawable.emote_wave_v4),
    SURPRISE("Sorpresa", R.drawable.emote_surprise_v4),
}

/**
 * Botón de emotes autocontenido. Abre una paleta compacta y devuelve la
 * reacción escogida; la duración y posición del globo las decide la pantalla.
 */
@Composable
fun EmotePicker(
    onEmoteSelected: (FarmEmote) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut() + scaleOut(transformOrigin = TransformOrigin(1f, 1f)),
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xF7FFF1C5), RoundedCornerShape(14.dp))
                    .border(3.dp, Color(0xFF573E28), RoundedCornerShape(14.dp))
                    .padding(7.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FarmEmote.entries.forEach { emote ->
                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .background(Color(0xFFFFE9A8), RoundedCornerShape(10.dp))
                            .border(2.dp, Color(0xFFA7652B), RoundedCornerShape(10.dp))
                            .clickable(enabled = enabled) {
                                expanded = false
                                onEmoteSelected(emote)
                            }
                            .semantics { contentDescription = "Emote: ${emote.displayName}" }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmoteSprite(emote)
                    }
                }
            }
        }

        Spacer(Modifier.height(7.dp))

        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    if (enabled) Color(0xFFF4DA8B) else Color(0xFFA79D83),
                    CircleShape,
                )
                .border(3.dp, Color(0xFF573E28), CircleShape)
                .clickable(enabled = enabled) { expanded = !expanded }
                .semantics {
                    contentDescription = if (expanded) "Cerrar emotes" else "Abrir emotes"
                }
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmoteSprite(FarmEmote.SMILE)
        }
    }
}

@Composable
private fun EmoteSprite(emote: FarmEmote, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(emote.spriteRes),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(34.dp),
    )
}
