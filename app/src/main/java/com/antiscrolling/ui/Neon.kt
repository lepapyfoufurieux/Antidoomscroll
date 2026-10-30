package com.antiscrolling.ui

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.antiscrolling.domain.Rule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bleu = normal, magenta = alerte (limite proche, focus), jaune = repères et réussites. */
object Neon {
    val Cyan = Color(0xFF00E5FF)
    val Magenta = Color(0xFFFF2BD6)
    val Pink = Color(0xFFFF8BE8) // petit texte sur une carte magenta, plus lisible
    val Yellow = Color(0xFFFCEE0A)
    val Bg = Color(0xFF07070F)
    val Panel = Color(0xFF0A0E20)
    val PanelMagenta = Color(0xFF14081C)
    val PanelYellow = Color(0xFF181604)
    val Text = Color(0xFFE6E9F5)
    val Soft = Color(0xFFB9BED6)
    val Muted = Color(0xFF8A90B0)
    val Off = Color(0x40FFFFFF)
    val Mono = FontFamily.Monospace
}

/** Carte en alerte : limite atteinte ou dans les 5 dernières minutes. */
fun nearLimit(rule: Rule?, usedMs: Long): Boolean = rule != null && (rule.quotaSoon(usedMs) || rule.quotaReached(usedMs))

/** Bleu à la 1re ouverture du jour, magenta à partir de la 5e. */
fun pressureColor(opensToday: Int): Color = lerp(Neon.Cyan, Neon.Magenta, (opensToday / 4f).coerceIn(0f, 1f))

fun glow(color: Color, radius: Float = 18f) = Shadow(color.copy(alpha = 0.75f), Offset.Zero, radius)

/** Cadre néon : fond, bordure (pleine ou en pointillés) et halo éventuel. */
fun Modifier.neonFrame(color: Color, fill: Color = Neon.Panel, glow: Boolean = false, dashed: Boolean = false): Modifier =
    // Fond opaque sous le halo : sinon l'ombre transparaît sous un fond translucide.
    (if (glow) shadow(10.dp, RectangleShape, clip = false, ambientColor = color, spotColor = color).background(Neon.Bg) else this)
        .background(fill)
        .then(
            if (dashed) {
                Modifier.drawBehind {
                    drawRect(color, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
                }
            } else {
                Modifier.border(1.dp, color)
            },
        )

/** Fond quadrillé des écrans principaux. */
fun Modifier.neonGrid(color: Color): Modifier = drawBehind {
    drawRect(Neon.Bg)
    val step = 26.dp.toPx()
    val line = color.copy(alpha = 0.06f)
    var x = 0f
    while (x < size.width) {
        drawLine(line, Offset(x, 0f), Offset(x, size.height))
        x += step
    }
    var y = 0f
    while (y < size.height) {
        drawLine(line, Offset(0f, y), Offset(size.width, y))
        y += step
    }
}

/** Lignes d'écran cathodique, pour l'écran de blocage. */
fun Modifier.scanlines(): Modifier = drawBehind {
    drawRect(Neon.Bg)
    val step = 4.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(Neon.Magenta.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y))
        y += step
    }
}

@Composable
fun Tag(text: String, color: Color = Neon.Yellow) {
    Text("// ${text.uppercase()}", color = color, fontFamily = Neon.Mono, fontSize = 11.sp, letterSpacing = 3.sp)
}

@Composable
fun Caption(text: String, color: Color = Neon.Muted) {
    Text(text.uppercase(), color = color, fontSize = 12.sp, letterSpacing = 2.sp)
}

@Composable
fun ScreenHeader(tag: String, title: String, color: Color = Neon.Cyan) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Tag(tag)
        Text(
            title.uppercase(),
            color = color,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            style = TextStyle(shadow = glow(color)),
        )
    }
}

/** Bouton néon : contour coloré, ou plein aux coins coupés pour l'action principale. */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Neon.Cyan,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    val frame = if (filled && enabled) {
        Modifier.clip(CutCornerShape(topEnd = 16.dp, bottomStart = 16.dp)).background(color)
    } else {
        Modifier.border(1.dp, if (enabled) color else color.copy(alpha = 0.45f))
    }
    Box(
        modifier
            .heightIn(min = 52.dp)
            .then(frame)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            color = when {
                filled && enabled -> Neon.Bg
                enabled -> color
                else -> color.copy(alpha = 0.7f)
            },
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Case d'une grille de choix : allumée quand elle est choisie. */
@Composable
fun ChoiceCell(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Neon.Cyan,
    enabled: Boolean = true,
    mono: Boolean = true,
    role: Role = Role.RadioButton,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .then(if (selected) Modifier.neonFrame(color, color.copy(alpha = 0.16f), glow = true) else Modifier.neonFrame(Neon.Off, Color.Transparent))
            .selectable(selected = selected, enabled = enabled, role = role, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val textColor = if (selected) color else Neon.Soft
        Text(
            text,
            color = if (enabled) textColor else textColor.copy(alpha = 0.4f),
            fontFamily = if (mono) Neon.Mono else null,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun <T> ChoiceGrid(
    choices: List<T>,
    selected: T?,
    columns: Int,
    label: (T) -> String,
    color: Color = Neon.Cyan,
    enabled: Boolean = true,
    mono: Boolean = true,
    role: Role = Role.RadioButton,
    onPick: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { choice ->
                    ChoiceCell(label(choice), choice == selected, Modifier.weight(1f), color, enabled, mono, role) { onPick(choice) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Interrupteur carré. Sans [onChange], il ne fait qu'afficher l'état : c'est la ligne entière qui bascule. */
@Composable
fun NeonSwitch(checked: Boolean, color: Color = Neon.Cyan, label: String? = null, onChange: ((Boolean) -> Unit)? = null) {
    val toggle = if (onChange != null) {
        Modifier
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .semantics { label?.let { contentDescription = it } }
    } else {
        Modifier
    }
    Box(
        Modifier
            .size(width = 52.dp, height = 30.dp)
            .then(toggle)
            .then(if (checked) Modifier.neonFrame(color, color.copy(alpha = 0.22f), glow = true) else Modifier.neonFrame(Neon.Off, Color.Transparent))
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(22.dp).background(if (checked) color else Neon.Muted))
    }
}

/** Barre de proportion, de 0 à 1. */
@Composable
fun UsageBar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).background(Color.White.copy(alpha = 0.08f))) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .shadow(6.dp, RectangleShape, clip = false, ambientColor = color, spotColor = color)
                .background(color),
        )
    }
}

/** Anneau lumineux : un cercle discret et un arc de [progress] (0 à 1). */
@Composable
fun NeonRing(color: Color, progress: Float, size: Dp, dashed: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .size(size)
            .shadow(18.dp, CircleShape, clip = false, ambientColor = color, spotColor = color)
            .background(Neon.Bg, CircleShape)
            .drawBehind {
                val stroke = 3.dp.toPx()
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                if (dashed) {
                    drawArc(color.copy(alpha = 0.6f), 0f, 360f, false, topLeft, arcSize,
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                } else {
                    drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Square))
                }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Icône de l'appli en néon, dans un cadre de la même couleur. Une case vide tant qu'elle charge ou si l'appli a disparu. */
@Composable
fun NeonIcon(packageName: String, color: Color, size: Dp = 40.dp) {
    val pm = LocalContext.current.packageManager
    @SuppressLint("ProduceStateDoesNotAssignValue") // assignée plus bas, faux positif de lint
    val glyph by produceState<Glyph?>(null, packageName) {
        value = withContext(Dispatchers.IO) { runCatching { loadGlyph(pm, packageName) }.getOrNull() }
    }
    Box(Modifier.size(size).neonFrame(color, Color.Transparent), contentAlignment = Alignment.Center) {
        glyph?.let {
            Image(
                it.bitmap,
                contentDescription = null,
                modifier = Modifier.size(size * 0.62f),
                colorFilter = if (it.monochrome) ColorFilter.tint(color) else greyTint(color),
            )
        }
    }
}

private class Glyph(val bitmap: ImageBitmap, val monochrome: Boolean)

private const val GLYPH_PX = 96

/** Android 13+ : le calque monochrome des icônes à thème. Sinon l'icône normale, passée en gris puis teintée. */
private fun loadGlyph(pm: PackageManager, packageName: String): Glyph {
    val icon = pm.getApplicationIcon(packageName)
    val mono = if (Build.VERSION.SDK_INT >= 33) (icon as? AdaptiveIconDrawable)?.monochrome else null
    if (mono == null) return Glyph(icon.toBitmap(GLYPH_PX, GLYPH_PX).asImageBitmap(), monochrome = false)
    val bitmap = Bitmap.createBitmap(GLYPH_PX, GLYPH_PX, Bitmap.Config.ARGB_8888)
    // Le calque garde la marge des icônes adaptatives (seuls 66 dp sur 108 sont visibles) : on l'agrandit pour remplir la case.
    val inset = (GLYPH_PX * 0.3f).toInt()
    mono.setBounds(-inset, -inset, GLYPH_PX + inset, GLYPH_PX + inset)
    mono.draw(Canvas(bitmap))
    return Glyph(bitmap.asImageBitmap(), monochrome = true)
}

private fun greyTint(color: Color): ColorFilter = ColorFilter.colorMatrix(
    // Appliqué de droite à gauche : gris d'abord, puis la couleur.
    ColorMatrix().apply {
        setToScale(color.red, color.green, color.blue, 1f)
        timesAssign(ColorMatrix().apply { setToSaturation(0f) })
    },
)
