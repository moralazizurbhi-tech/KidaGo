package com.afede.kidago.ui.scan

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afede.kidago.R
import com.afede.kidago.data.formatPrice
import com.afede.kidago.ui.groupedBarcode
import com.afede.kidago.ui.theme.KidaGoColors
import com.afede.kidago.ui.theme.KidaGoShapes
import com.afede.kidago.ui.theme.Manrope

// The result card ("ficha") of FEAT-002 / FEAT-006 UI Definition (redesign validated 2026-11-21, Figma «Precio (propuesta v3)»).

enum class ResultKind { Found, NotFound, Error }

private val LabelBarcodeFont = FontFamily(Font(R.font.libre_barcode_128))
private val CardShadow = 3.dp
private val Muted = KidaGoColors.Text.copy(alpha = 0.6f)

/**
 * White card: status row (icon, title, subtitle), then, when there is a [code], a divider and the data row: the code as
 * a label (small bars over the grouped digits) and, when [showPrice], the price ("Sin precio" when [priceCents] is
 * null). [priceBlock] puts the price in the pink block that only Consultar precio uses. [subtitle] is replaced by
 * [subtitleContent] when given (the rescan warning).
 */
@Composable
fun ResultCard(
    kind: ResultKind,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    code: String? = null,
    showPrice: Boolean = false,
    priceCents: Long? = null,
    priceBlock: Boolean = false,
    subtitleContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth()
            .shadow(CardShadow, KidaGoShapes.Card)
            .background(KidaGoColors.Surface, KidaGoShapes.Card)
            .border(1.dp, KidaGoColors.Border, KidaGoShapes.Card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatusIcon(kind)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp), color = if (kind == ResultKind.Found) KidaGoColors.SuccessText else KidaGoColors.Text)
                subtitleContent?.invoke() ?: Text(subtitle, style = TextStyle(fontFamily = Manrope, fontSize = 18.sp, lineHeight = 24.sp), color = Muted)
            }
        }
        if (code != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(KidaGoColors.Text.copy(alpha = 0.1f)))
            Row(Modifier.fillMaxWidth(), verticalAlignment = if (priceBlock) Alignment.CenterVertically else Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                CodeLabel(code)
                if (showPrice) Price(priceCents, priceBlock)
            }
        }
    }
}

/** The scanned code as a product label: small barcode bars over the grouped digits. */
@Composable
private fun CodeLabel(code: String) {
    val density = LocalDensity.current
    Column(Modifier.testTag("result-code")) {
        // Fixed size like the artwork: it must not follow the user's font scale.
        Text("ainhoa", style = TextStyle(fontFamily = LabelBarcodeFont, fontSize = with(density) { 58.dp.toSp() }, lineHeight = with(density) { 46.dp.toSp() }, color = KidaGoColors.Text), maxLines = 1, softWrap = false)
        Text(groupedBarcode(code), style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp), color = KidaGoColors.Text)
    }
}

@Composable
private fun Price(cents: Long?, block: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.testTag("result-price").then(if (block) Modifier.background(KidaGoColors.Accent.copy(alpha = 0.07f), shape).padding(horizontal = 16.dp, vertical = 8.dp) else Modifier),
        horizontalAlignment = Alignment.End,
    ) {
        Text("Precio", style = TextStyle(fontFamily = Manrope, fontSize = 14.sp, lineHeight = 18.sp), color = Muted)
        if (cents != null) {
            val size = if (block) 38.sp else 32.sp
            Text(formatPrice(cents), style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = size, lineHeight = size * 1.15f), color = KidaGoColors.Text)
        } else {
            Text("Sin precio", style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 30.sp), color = Muted)
        }
    }
}

@Composable
fun StatusIcon(kind: ResultKind) {
    when (kind) {
        ResultKind.Found -> Box(Modifier.size(44.dp).background(KidaGoColors.SuccessIcon, CircleShape).testTag("scan-badge"), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, null, tint = KidaGoColors.Surface, modifier = Modifier.size(28.dp))
        }
        ResultKind.NotFound -> Box(Modifier.size(44.dp).background(KidaGoColors.Text.copy(alpha = 0.45f), CircleShape).testTag("scan-badge"), contentAlignment = Alignment.Center) {
            Exclamation(KidaGoColors.Surface)
        }
        ResultKind.Error -> TriangleBadge()
    }
}

/** The plain white "!" the Not Found and Error icons carry (a bar over a dot). */
@Composable
private fun Exclamation(tint: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(width = 4.dp, height = 13.dp).background(tint, RoundedCornerShape(2.dp)))
        Box(Modifier.size(4.dp).background(tint, CircleShape))
    }
}

/** The Error icon: a red triangle with rounded corners, deliberately not a circle. */
@Composable
private fun TriangleBadge() {
    Box(Modifier.size(width = 44.dp, height = 40.dp).testTag("scan-badge"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = 4.dp.toPx() // corner radius
            val a = Offset(size.width / 2f, 0f); val b = Offset(size.width, size.height); val c = Offset(0f, size.height)
            // A rounded triangle is a smaller triangle (inset by r) drawn with a round-joined stroke 2r wide.
            val la = (b - c).getDistance(); val lb = (a - c).getDistance(); val lc = (a - b).getDistance()
            val centre = Offset((la * a.x + lb * b.x + lc * c.x) / (la + lb + lc), (la * a.y + lb * b.y + lc * c.y) / (la + lb + lc))
            val inradius = (size.width * size.height / 2f) / ((la + lb + lc) / 2f)
            val k = (inradius - r) / inradius
            fun inset(p: Offset) = centre + (p - centre) * k
            val path = Path().apply { moveTo(inset(a).x, inset(a).y); lineTo(inset(b).x, inset(b).y); lineTo(inset(c).x, inset(c).y); close() }
            drawPath(path, KidaGoColors.Accent, style = Fill)
            drawPath(path, KidaGoColors.Accent, style = Stroke(width = 2 * r, join = StrokeJoin.Round))
        }
        Exclamation(KidaGoColors.Surface, Modifier.offset(y = 4.dp))
    }
}

/** Compact pill above the card naming the screen: white for Escanear, red for Consultar precio. Results only. */
@Composable
fun ScreenLabel(text: String, @DrawableRes icon: Int, red: Boolean, modifier: Modifier = Modifier) {
    val fg = if (red) KidaGoColors.Surface else KidaGoColors.Text
    Row(
        modifier.height(40.dp)
            .shadow(2.dp, KidaGoShapes.Pill)
            .background(if (red) KidaGoColors.Accent else KidaGoColors.Surface, KidaGoShapes.Pill)
            .then(if (red) Modifier else Modifier.border(1.dp, KidaGoColors.Border, KidaGoShapes.Pill))
            .padding(start = 14.dp, end = 18.dp)
            .testTag("screen-label"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Text(text, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp), color = fg)
    }
}

/**
 * The labelled pill that closes a result and returns to the screen's base: "Listo" in Escanear, "Volver" in Consultar
 * precio. White, input border and shadow; pressed, the accent at about 6%.
 */
@Composable
fun ActionPill(text: String, icon: Painter, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = if (pressed) KidaGoColors.Accent.copy(alpha = 0.06f).compositeOver(KidaGoColors.Surface) else KidaGoColors.Surface
    Row(
        modifier.height(64.dp)
            .shadow(3.dp, KidaGoShapes.Pill)
            .background(fill, KidaGoShapes.Pill)
            .border(1.dp, KidaGoColors.Border, KidaGoShapes.Pill)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 24.dp, end = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, contentDescription = null, tint = KidaGoColors.Text, modifier = Modifier.size(26.dp))
        Text(text, style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp), color = KidaGoColors.Text)
    }
}

@Composable
fun checkPainter(): Painter = rememberVectorPainter(Icons.Filled.Check)
