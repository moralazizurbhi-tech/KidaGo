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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.afede.kidago.ui.NoSoftKeyboard
import com.afede.kidago.ui.groupedBarcode
import com.afede.kidago.ui.theme.KidaGoColors
import com.afede.kidago.ui.theme.KidaGoShapes
import com.afede.kidago.ui.theme.KidaGoSpacing
import com.afede.kidago.ui.theme.KidaGoTextField
import com.afede.kidago.ui.theme.Manrope

/** Libre Barcode 128 (SIL OFL): the hero barcode in the Figma design is this font set in "ainhoa", black bars only (the Figma wavy line is omitted by decision). */
private val BarcodeFont = FontFamily(Font(R.font.libre_barcode_128))
private const val HERO_TEXT = "ainhoa"

// Design px (1 dp = 1 px, see DesignPixels). The reference illustration is 286x127 with bars of 128 px type (76 high),
// set 7 px apart, 271 wide; here it is 80% of that in every state (FEAT-002 UI Definition, outcome stack): 229x102.
private const val HeroScale = 0.8f
private val HeroFontSize = (128 * HeroScale).dp
private val HeroLetterSpacing = (7 * HeroScale).dp
private val HeroWidth = 229.dp
private val HeroHeight = 102.dp

private val SideMargin = 32.dp
private val BlockGap = 12.dp // between badge, heading, subtext and the action row
private val IllustrationGap = 32.dp // illustration (with its echoed code) to the outcome badge, and hero to the input pill
private val EchoGap = 2.dp // hero to the echoed code (Found, Not Found)
private val EchoHeight = 32.dp
private val ErrorGap = IllustrationGap - 1.dp // Error shows no code: the 1 px unseen focus holder completes the 32 px
private val FieldHeight = 64.dp
private val ListoSize = 64.dp

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
    /** A catalog sync is running: the surface is replaced by the updating message, so nothing can be typed or scanned. */
    syncing: Boolean = false,
    /** "Listo" (C20): finish the scan; receives the "Cantidad" text typed but not submitted. */
    onFinish: (pendingQuantity: String) -> Unit = {},
    /** The device has a physical keyboard with digits (PhysicalKeyboardProbe): no field opens the on-screen keyboard. */
    physicalKeyboard: Boolean = true,
) {
    if (syncing) {
        SyncingSurface(modifier)
        return
    }
    val focus = remember { FocusRequester() }
    // Found hands the focus to "Cantidad" so a quantity can be typed right after a scan; every other state keeps it on the input.
    LaunchedEffect(state is CaptureState.Found) { if (state !is CaptureState.Found) focus.requestFocus() }
    val outcome = state is CaptureState.Found || state is CaptureState.NotFound || state == CaptureState.Error
    val gap by animateDpAsState(
        when { state is CaptureState.Found || state is CaptureState.NotFound -> EchoGap; state == CaptureState.Error -> ErrorGap; else -> IllustrationGap },
        tween(220), label = "hero-gap",
    )
    // The group sits in the middle of the area between the status strip and the tab bar, as in the frames.
    NoSoftKeyboard(physicalKeyboard) { Box(modifier.fillMaxSize().testTag("screen-scan"), contentAlignment = Alignment.Center) {
        // Nothing scrolls: every state fits above the tab bar with at least 16 px to spare (UX, Fit).
        Column(
            Modifier.padding(horizontal = SideMargin, vertical = KidaGoSpacing.Gap),
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
            ) { shown -> Outcome(shown, onQuantitySubmit, onFinish) }
        }
    } }
}

/**
 * The scanning surface while a catalog sync runs (Project UX Specification, Screen Composition; FEAT-001 UI Definition):
 * disabled, not merely dimmed. The input is not composed at all, so there is no field to focus or type into, and the
 * mascot with the updating message fills the area. When the sync ends the screen composes again and focuses the field.
 */
@Composable
private fun SyncingSurface(modifier: Modifier) {
    Box(modifier.fillMaxSize().testTag("screen-scan"), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 20.dp).testTag("scan-disabled"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // The project's mascot outline, reused unchanged from the shared empty state.
            // 83x83 frame (FEAT-001 UI Definition); the artwork keeps its 83x53 visible rows, centred in it.
            Box(Modifier.size(83.dp).testTag("scan-mascot"), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_tab_products), contentDescription = null, modifier = Modifier.size(width = 83.dp, height = 53.dp))
            }
            Text(
                "La base de datos se está actualizando. Esto puede tardar unos minutos.",
                style = MaterialTheme.typography.bodyMedium, // Manrope regular 14 px at 60% opacity
                color = KidaGoColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 248.dp),
            )
        }
    }
}

@Composable
private fun HeroBarcode(dimmed: Boolean) {
    val density = LocalDensity.current
    Box(Modifier.size(HeroWidth, HeroHeight).testTag("scan-hero"), contentAlignment = Alignment.Center) {
        Text(
            HERO_TEXT,
            // Fixed size, like the artwork: it must not follow the user's font scale. The Error variant is the same artwork at half strength.
            style = TextStyle(fontFamily = BarcodeFont, fontSize = with(density) { HeroFontSize.toSp() }, letterSpacing = with(density) { HeroLetterSpacing.toSp() }, color = if (dimmed) KidaGoColors.Text.copy(alpha = 0.5f) else KidaGoColors.Text),
            maxLines = 1,
            softWrap = false,
            // The font draws the bars inside the line box, which is taller than the bars; centre the bars in the hero box.
            modifier = Modifier.offset(y = (-4 * HeroScale).dp),
        )
    }
}

private class CodeDisplay(private val echo: String?) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.isEmpty() && echo != null) {
            // Nothing typed (a hardware scan, or the field was freed after Found): show the captured code instead.
            return TransformedText(AnnotatedString(groupedBarcode(echo)), object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = 0
                override fun transformedToOriginal(offset: Int) = 0
            })
        }
        val shown = groupedBarcode(text.text)
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
        TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 1.sp, color = KidaGoColors.Text, textAlign = TextAlign.Center)
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
                    Modifier.fillMaxWidth().height(FieldHeight)
                        .shadow(3.dp, KidaGoShapes.Pill)
                        .background(KidaGoColors.Surface, KidaGoShapes.Pill)
                        .border(if (focused) 2.dp else 1.dp, if (focused) KidaGoColors.Accent else KidaGoColors.Border, KidaGoShapes.Pill)
                        .padding(start = 20.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Image(painterResource(R.drawable.ic_scan_field), contentDescription = null, modifier = Modifier.size(32.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (typed.isEmpty()) Text("Introduce o escanea un código", style = style.copy(letterSpacing = 0.sp, fontSize = 20.sp, lineHeight = 28.sp), color = KidaGoColors.TextMuted)
                        field()
                    }
                    if (typed.isNotEmpty()) ClearButton { onTyped("") }
                }
            } else {
                // The outcome shows the code itself, with no pill. When there is nothing to show (Error) it keeps focus unseen.
                Box(Modifier.fillMaxWidth().height(if (hidden) 1.dp else EchoHeight), contentAlignment = Alignment.Center) { field() }
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
private fun Outcome(state: CaptureState, onQuantitySubmit: (String) -> Unit, onFinish: (String) -> Unit) {
    when (state) {
        CaptureState.Searching -> Column(Modifier.padding(top = 55.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CircularProgressIndicator(Modifier.size(36.dp).testTag("scan-spinner"), color = KidaGoColors.Accent, trackColor = KidaGoColors.Accent.copy(alpha = 0.1f), strokeWidth = 4.dp)
            Text("Buscando producto...", style = MaterialTheme.typography.labelLarge, color = KidaGoColors.TextMuted)
        }
        is CaptureState.Found -> {
            var draft by remember { mutableStateOf("") } // lives as long as this Found, so "Listo" can apply what was typed
            Block(
            tag = "scan-outcome-found",
            badge = { CircleBadge(KidaGoColors.SuccessIcon) { Icon(Icons.Filled.Check, null, tint = KidaGoColors.Surface, modifier = Modifier.size(32.dp)) } },
            heading = "Producto encontrado",
            headingColor = KidaGoColors.SuccessText,
            subtext = { if (state.wasExisting) RescanWarning(state.quantity) else Subtext("Añadido a la lista") },
            extra = { FoundActions(draft, { draft = it }) { onFinish(draft) } },
            )
        }
        is CaptureState.NotFound -> Block(
            tag = "scan-outcome-notfound",
            badge = { CircleBadge(KidaGoColors.TextMuted) { Exclamation(KidaGoColors.Surface) } },
            heading = "Producto no encontrado",
            subtext = { Subtext("Comprueba el código o inténtalo de nuevo") },
            extra = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ListoButton { onFinish("") } } },
        )
        CaptureState.Error -> Block(
            tag = "scan-outcome-error",
            topGap = 0.dp,
            badge = { TriangleBadge() },
            heading = "Error de lectura",
            subtext = { Subtext("No se ha podido leer el código Inténtalo de nuevo") },
            extra = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { ListoButton { onFinish("") } } },
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
    topGap: Dp = IllustrationGap,
    extra: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(top = topGap).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(BlockGap)) {
        badge()
        Text(heading, style = MaterialTheme.typography.titleLarge, color = headingColor, textAlign = TextAlign.Center)
        subtext()
        extra()
    }
}

@Composable
private fun CircleBadge(color: Color, content: @Composable () -> Unit) {
    Box(Modifier.size(54.dp).background(color, CircleShape).testTag("scan-badge"), contentAlignment = Alignment.Center) { content() }
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
    Box(Modifier.size(width = 52.dp, height = 46.dp).testTag("scan-badge"), contentAlignment = Alignment.Center) {
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

/** Found's action row: "Cantidad" (340 wide) with "Listo" 12 px to its right, aligned to the field (416 in all). */
@Composable
private fun FoundActions(draft: String, onDraft: (String) -> Unit, onFinish: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(BlockGap)) {
        Cantidad(draft, onDraft, onFinish, Modifier.weight(1f))
        ListoButton(onFinish)
    }
}

/**
 * Labelled numeric field: the label (22 px) above a 64 px field with a 6 px gap, ghost `1`, at most two digits. A value
 * outside 1..99 empties back to the ghost with no message.
 */
@Composable
private fun Cantidad(draft: String, onDraft: (String) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Cantidad", style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp), color = KidaGoColors.Text)
        KidaGoTextField(
            value = draft,
            onValueChange = { onDraft(it.filter { c -> c in '0'..'9' }.take(2)) },
            modifier = Modifier.fillMaxWidth().height(FieldHeight).shadow(3.dp, KidaGoShapes.Pill).focusRequester(focus).testTag("scan-cantidad"),
            placeholder = "1",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }), // Enter does what "Listo" does: applies the typed quantity and returns to Idle
        )
    }
}

/**
 * "Listo" (FEAT-002 UI): a 64x64 circular icon-only control, named "Listo" for assistive technology, in the input's
 * border and surface; pressed, it takes the accent at about 6%.
 */
@Composable
private fun ListoButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = if (pressed) KidaGoColors.Accent.copy(alpha = 0.06f).compositeOver(KidaGoColors.Surface) else KidaGoColors.Surface
    Box(
        Modifier.size(ListoSize)
            .background(fill, CircleShape)
            .border(1.dp, KidaGoColors.Border, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Listo" }
            .testTag("scan-listo"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_return), contentDescription = null, tint = KidaGoColors.Text, modifier = Modifier.size(24.dp))
    }
}
