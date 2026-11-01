package com.example.drivoice.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private const val ROLLING_SLOTS = 8

@Composable
fun Text(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    BasicText(text, modifier, style.copy(color = color, textAlign = align))
}

@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    fill: Color = Color.Transparent,
    role: Role = Role.Button,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalPalette.current
    val still = LocalStill.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !still) Motion.PRESSED_SCALE else 1f,
        animationSpec = if (pressed) tween(Motion.SHORT1, easing = Motion.easing) else spring(0.55f, Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    val background by animateColorAsState(if (pressed) palette.pressed else fill, tween(Motion.SHORT1), label = "pressFill")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(background)
            .clickable(source, indication = null, role = role, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Space.hairline).background(LocalPalette.current.line))
}

@Composable
fun RollingNumber(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier, prefix: String = "", fromZero: Boolean = false) {
    val still = LocalStill.current
    var shown by remember { mutableIntStateOf(if (fromZero && !still) 0 else value) }
    LaunchedEffect(value) {
        if (shown != value && !still) delay(Motion.MEDIUM1.toLong())
        shown = value
    }
    val text = prefix + NumberFormat.getIntegerInstance(Locale.KOREA).format(shown)
    Row(modifier) {
        for (slot in ROLLING_SLOTS downTo 1) {
            val char = text.getOrNull(text.length - slot)?.toString().orEmpty()
            key(slot) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        if (still) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val delay = (slot - 1) * Motion.STAGGER
                            (slideInVertically(tween(Motion.LONG2, delay, Motion.easing)) { it } + fadeIn(tween(Motion.MEDIUM1, delay))) togetherWith
                                (slideOutVertically(tween(Motion.LONG2, delay, Motion.easing)) { -it } + fadeOut(tween(Motion.SHORT2, delay)))
                        } using SizeTransform(clip = true) { _, _ -> tween(Motion.LONG2, easing = Motion.easing) }
                    },
                    label = "digit",
                ) { Text(it, style, color) }
            }
        }
    }
}

@Composable
fun Modifier.enter(index: Int): Modifier {
    if (LocalStill.current) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * Motion.STAGGER.toLong())
        progress.animateTo(1f, tween(Motion.LONG2, easing = Motion.easing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

@Composable
fun Segments(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, itemWidth: Dp = 176.dp) {
    val palette = LocalPalette.current
    val inset = 6.dp
    val offset by animateDpAsState(itemWidth * selected, spring(0.8f, Spring.StiffnessMediumLow), label = "segment")
    Box(
        modifier
            .width(itemWidth * items.size + inset * 2)
            .height(Space.touch)
            .clip(RoundedCornerShape(28.dp))
            .background(palette.surface)
            .padding(inset),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(22.dp))
                .background(palette.text),
        )
        Row {
            items.forEachIndexed { index, label ->
                val active = index == selected
                val color by animateColorAsState(if (active) palette.background else palette.textSub, tween(Motion.SHORT2), label = "segmentText")
                Pressable(
                    onClick = { onSelect(index) },
                    modifier = Modifier.width(itemWidth).fillMaxHeight().semantics { this.selected = active },
                    shape = RoundedCornerShape(22.dp),
                    role = Role.Tab,
                ) {
                    Text(label, if (active) Type.bodyStrong else Type.body, color)
                }
            }
        }
    }
}

@Composable
fun Toggle(checked: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val thumb by animateDpAsState(if (checked) 44.dp else 0.dp, spring(0.7f, Spring.StiffnessMediumLow), label = "toggleThumb")
    val track by animateColorAsState(if (checked) palette.accent else palette.line, tween(Motion.SHORT2), label = "toggleTrack")
    Box(
        modifier
            .size(width = 100.dp, height = 56.dp)
            .clip(CircleShape)
            .background(track)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .offset(x = thumb)
                .size(44.dp)
                .clip(CircleShape)
                .background(if (checked) palette.onAccent else palette.text),
        )
    }
}

@Composable
fun StepButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Pressable(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.size(Space.touch).semantics { contentDescription = description },
        shape = CircleShape,
        fill = palette.surface,
    ) {
        Text(glyph, Type.title, if (enabled) palette.text else palette.line)
    }
}

@Composable
fun Mark(on: Boolean, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Box(
        modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(if (on) palette.accent else palette.line),
    )
}

@Composable
fun AdaptiveColumns(wideFrom: Dp, wide: @Composable () -> Unit, narrow: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= wideFrom) wide() else narrow()
    }
}

@Composable
fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    val palette = LocalPalette.current
    Pressable(
        onClick = onClick,
        modifier = modifier.height(Space.touch).widthIn(min = 200.dp),
        shape = RoundedCornerShape(22.dp),
        fill = if (primary) palette.text else palette.surface,
    ) {
        Text(label, Type.bodyStrong, if (primary) palette.background else palette.text, Modifier.padding(horizontal = Space.l))
    }
}
