package com.afede.kidago.ui.scan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afede.kidago.R
import com.afede.kidago.ui.theme.KidaGoColors
import com.afede.kidago.ui.theme.KidaGoShapes
import com.afede.kidago.ui.theme.KidaGoSpacing
import com.afede.kidago.ui.theme.KidaGoTextField
import com.afede.kidago.ui.theme.Manrope

/** Libre Barcode 128 (SIL OFL): the hero barcode in the Figma design is this font set in "ainhoa". */
private val BarcodeFont = FontFamily(Font(R.font.libre_barcode_128))
private const val HERO_TEXT = "ainhoa"

// Figma "KidaGo" frames 480x800, dp = Figma px: the bars are 128 px type (76 high), set 7 px apart, 271 wide.
private val HeroFontSize = 128.dp
private val HeroLetterSpacing = 7.dp
private val HeroHeight = 76.dp

private val InputGap = 60.dp // hero to the input pill (Idle, Searching) and to the Error badge
private val EchoGap = 24.dp // hero to the echoed code (Found, Not Found)

/**
 * Escanear's scanning surface (FEAT-002 UX/UI, Figma "Diseño final"): the hero barcode, then one text field that keeps
 * keyboard focus in every state. It is a white pill in Idle, Typing and Searching and, once an outcome shows, the
 * code itself in large bold type (a scan's code is echoed there). Below it sit the outcome block and, for Found only,
 * the "Cantidad" input. Enter submits both fields; there is no on-screen submit control.
 */
@Composable
fun EscanearScreen(
    state: CaptureState,
    typed: String,
    onTyped: (String) -> Unit,
    onSubmit: () -> Unit,
    onQuantitySubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val outcome = state is CaptureState.Found || state is CaptureState.NotFound || state == CaptureState.Error
    val gap by animateDpAsState(if (state is CaptureState.Found || state is CaptureState.NotFound) EchoGap else InputGap, tween(220), label = "hero-gap")
    // The group sits in the middle of the area between the status strip and the tab bar, as in the frames.
    Box(modifier.fillMaxSize().testTag("screen-scan"), contentAlignment = Alignment.Center) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = KidaGoSpacing.Gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroBarcode(dimmed = state == CaptureState.Error)
            Spacer(Modifier.height(gap))
            CodeField(
                pill = !outcome,
                hidden = state == CaptureState.Error && typed.isEmpty(),
                typed = typed,
                echo = (state as? CaptureState.Found)?.code ?: (state as? CaptureState.NotFound)?.code,
                onTyped = onTyped,
                onSubmit = onSubmit,
                focus = focus,
            )
            // One smooth cross-fade per state change; a new capture supersedes the outcome, so exit and entry coincide (C9).
            // Keyed by kind of state so a quantity change within Found does not replay it or reset "Cantidad".
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                label = "capture-outcome",
            ) { shown -> Outcome(shown, onQuantitySubmit) }
        }
    }
}

@Composable
private fun HeroBarcode(dimmed: Boolean) {
    val density = LocalDensity.current
    Box(Modifier.height(HeroHeight).testTag("scan-hero"), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_scan_hero_wave), contentDescription = null, modifier = Modifier.offset(y = (-9).dp).size(323.dp, 49.dp))
        Text(
            HERO_TEXT,
            // Fixed size, like the artwork: it must not follow the user's font scale. The Error variant is the same artwork at half strength.
            style = TextStyle(fontFamily = BarcodeFont, fontSize = with(density) { HeroFontSize.toSp() }, letterSpacing = with(density) { HeroLetterSpacing.toSp() }, color = if (dimmed) KidaGoColors.Text.copy(alpha = 0.5f) else KidaGoColors.Text),
            maxLines = 1,
            softWrap = false,
            // The font draws the bars inside the line box, which is taller than the bars; centre the bars on the wave.
            modifier = Modifier.offset(y = (-4).dp),
        )
    }
}

/** The code with its EAN-13 digit grouping (1-6-6) for display only; the value itself is never altered. */
private fun grouped(digits: String) = buildString { digits.forEachIndexed { i, c -> if (i == 1 || i == 7) append(' '); append(c) } }

private class CodeDisplay(private val echo: String?) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.isEmpty() && echo != null) {
            // Nothing typed (a hardware scan, or the field was freed after Found): show the captured code instead.
            return TransformedText(AnnotatedString(grouped(echo)), object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = 0
                override fun transformedToOriginal(offset: Int) = 0
            })
        }
        val shown = grouped(text.text)
        return TransformedText(AnnotatedString(shown), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = when { offset <= 1 -> offset; offset <= 7 -> offset + 1; else -> offset + 2 }.coerceAtMost(shown.length)
            override fun transformedToOriginal(offset: Int) = when { offset <= 1 -> offset; offset <= 8 -> offset - 1; else -> offset - 2 }.coerceIn(0, text.length)
        })
    }
}

@Composable
private fun CodeField(
    pill: Boolean,
    hidden: Boolean,
    typed: String,
    echo: String?,
    onTyped: (String) -> Unit,
    onSubmit: () -> Unit,
    focus: FocusRequester,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val style = if (pill) {
        TextStyle(fontFamily = Manrope, fontSize = 20.sp, lineHeight = 28.sp, letterSpacing = 1.sp, color = KidaGoColors.Text)
    } else {
        TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 33.sp, letterSpacing = 1.sp, color = KidaGoColors.Text, textAlign = TextAlign.Center)
    }
    BasicTextField(
        value = typed,
        onValueChange = onTyped,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("scan-input"),
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(KidaGoColors.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        visualTransformation = CodeDisplay(echo),
        interactionSource = interaction,
        decorationBox = { field ->
            if (pill) {
                Row(
                    Modifier.fillMaxWidth().height(64.dp)
                        .shadow(3.dp, KidaGoShapes.Pill)
                        .background(KidaGoColors.Surface, KidaGoShapes.Pill)
                        .border(if (focused) 2.dp else 1.dp, if (focused) KidaGoColors.Accent else KidaGoColors.Border, KidaGoShapes.Pill)
                        .padding(start = 20.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Image(painterResource(R.drawable.ic_scan_field), contentDescription = null, modifier = Modifier.size(32.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (typed.isEmpty()) Text("Introduce o escanea un código", style = style.copy(letterSpacing = 0.sp, fontSize = 16.sp, lineHeight = 24.sp), color = KidaGoColors.TextMuted)
                        field()
                    }
                    if (typed.isNotEmpty()) ClearButton { onTyped("") }
                }
            } else {
                // The outcome shows the code itself, with no pill. When there is nothing to show (Error) it keeps focus unseen.
                Box(Modifier.fillMaxWidth().height(if (hidden) 1.dp else 33.dp), contentAlignment = Alignment.Center) { field() }
            }
        },
    )
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    Box(Modifier.size(24.dp).background(KidaGoColors.TextMuted, CircleShape).clickable(onClick = onClick).testTag("scan-clear"), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Close, contentDescription = "Borrar", tint = KidaGoColors.Surface, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun Outcome(state: CaptureState, onQuantitySubmit: (String) -> Unit) {
    when (state) {
        CaptureState.Searching -> Column(Modifier.padding(top = 55.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CircularProgressIndicator(Modifier.size(36.dp).testTag("scan-spinner"), color = KidaGoColors.Accent, trackColor = KidaGoColors.Accent.copy(alpha = 0.1f), strokeWidth = 4.dp)
            Text("Buscando producto...", style = MaterialTheme.typography.labelLarge, color = KidaGoColors.TextMuted)
        }
        is CaptureState.Found -> Block(
            tag = "scan-outcome-found",
            badge = { CircleBadge(KidaGoColors.SuccessIcon) { Icon(Icons.Filled.Check, null, tint = KidaGoColors.Surface, modifier = Modifier.size(32.dp)) } },
            heading = "Producto encontrado",
            headingColor = KidaGoColors.SuccessText,
            subtext = { if (state.wasExisting) RescanWarning(state.quantity) else Subtext("Añadido a la lista") },
            extra = { Cantidad(onQuantitySubmit) },
        )
        is CaptureState.NotFound -> Block(
            tag = "scan-outcome-notfound",
            badge = { CircleBadge(KidaGoColors.TextMuted) { Exclamation(KidaGoColors.Surface) } },
            heading = "Producto no encontrado",
            subtext = { Subtext("Comprueba el código o inténtalo de nuevo") },
        )
        CaptureState.Error -> Block(
            tag = "scan-outcome-error",
            topGap = 0.dp,
            badge = { TriangleBadge() },
            heading = "Error de lectura",
            subtext = { Subtext("No se ha podido leer el código Inténtalo de nuevo") },
        )
        CaptureState.Idle, CaptureState.Typing -> Unit // the input alone carries these states
    }
}

@Composable
private fun Block(
    tag: String,
    badge: @Composable () -> Unit,
    heading: String,
    subtext: @Composable () -> Unit,
    headingColor: Color = KidaGoColors.Text,
    topGap: Dp = 50.dp,
    extra: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(top = topGap).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        badge()
        Text(heading, style = MaterialTheme.typography.titleLarge, color = headingColor, textAlign = TextAlign.Center)
        subtext()
        extra()
    }
}

@Composable
private fun CircleBadge(color: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(54.dp).background(color, CircleShape), contentAlignment = Alignment.Center) { content() }
}

/** The plain white "!" the Not Found and Error badges carry (a bar over a dot). */
@Composable
private fun Exclamation(tint: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(width = 4.dp, height = 15.dp).background(tint, RoundedCornerShape(2.dp)))
        Box(Modifier.size(4.dp).background(tint, CircleShape))
    }
}

/** The Error badge: a red triangle with rounded corners, deliberately not a circle (about 52 x 46 as rendered). */
@Composable
private fun TriangleBadge() {
    Box(Modifier.size(width = 52.dp, height = 46.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = 5.dp.toPx() // corner radius
            val a = Offset(size.width / 2f, 0f); val b = Offset(size.width, size.height); val c = Offset(0f, size.height)
            // A rounded triangle is a smaller triangle (inset by r) drawn with a round-joined stroke 2r wide.
            val la = (b - c).getDistance(); val lb = (a - c).getDistance(); val lc = (a - b).getDistance()
            val centre = Offset((la * a.x + lb * b.x + lc * c.x) / (la + lb + lc), (la * a.y + lb * b.y + lc * c.y) / (la + lb + lc))
            val area = size.width * size.height / 2f
            val inradius = area / ((la + lb + lc) / 2f)
            val k = (inradius - r) / inradius
            fun inset(p: Offset) = centre + (p - centre) * k
            val path = Path().apply { moveTo(inset(a).x, inset(a).y); lineTo(inset(b).x, inset(b).y); lineTo(inset(c).x, inset(c).y); close() }
            drawPath(path, KidaGoColors.Accent, style = Fill)
            drawPath(path, KidaGoColors.Accent, style = Stroke(width = 2 * r, join = StrokeJoin.Round))
        }
        Exclamation(KidaGoColors.Surface, Modifier.offset(y = 4.dp))
    }
}

@Composable
private fun Subtext(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = KidaGoColors.Text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 205.dp))
}

/** Rescan: alert icon plus text, so the warning never relies on colour (C5). */
@Composable
private fun RescanWarning(total: Int) {
    Row(Modifier.testTag("scan-rescan"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_alert_circle), contentDescription = null, tint = KidaGoColors.Text, modifier = Modifier.size(20.dp))
        Text("Ya escaneado · Total acumulado: $total", style = MaterialTheme.typography.labelLarge, color = KidaGoColors.Text)
    }
}

/**
 * Labelled numeric field in the 200 x 64 pill the Figma stepper had, ghost `1`, at most two digits. A value outside
 * 1..99 empties back to the ghost with no message.
 */
@Composable
private fun Cantidad(onSubmit: (String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(Modifier.widthIn(max = 200.dp).fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cantidad", style = MaterialTheme.typography.bodyLarge, color = KidaGoColors.Text)
        KidaGoTextField(
            value = draft,
            onValueChange = { draft = it.filter { c -> c in '0'..'9' }.take(2) },
            modifier = Modifier.fillMaxWidth().height(64.dp).shadow(3.dp, KidaGoShapes.Pill).testTag("scan-cantidad"),
            placeholder = "1",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onSubmit(draft)
                if (draft.toIntOrNull()?.let { it in 1..99 } != true) draft = ""
            }),
        )
    }
}
