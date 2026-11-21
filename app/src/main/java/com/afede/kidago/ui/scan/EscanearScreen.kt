package com.afede.kidago.ui.scan

import androidx.annotation.DrawableRes
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
import androidx.lifecycle.compose.LifecycleResumeEffect
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
private val BlockGap = 12.dp // between the label, the result card and the action row
private val IllustrationGap = 32.dp // hero to the input pill (screen base only)
private val FieldHeight = 64.dp

/**
 * Escanear's scanning surface (FEAT-002 UX/UI; redesign validated 2026-11-21, Figma «Precio (propuesta v3)»). The base
 * (Idle, Typing, Searching) is the hero barcode and the white pill field. A result (Found, Not Found, Error) drops the
 * hero and shows the "Escanear" label, the result card (code as a label, price for Found) and its action row: "Cantidad"
 * and "Listo" for Found, "Listo" alone otherwise. The field stays composed in a result as an unseen focus holder.
 * Enter submits both fields; there is no on-screen submit control.
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
    /** Each edit of the "Cantidad" text, so the capture coordinator holds what is typed and not yet submitted. */
    onQuantityEdit: (String) -> Unit = {},
    /** The device has a physical keyboard with digits (PhysicalKeyboardProbe): no field opens the on-screen keyboard. */
    physicalKeyboard: Boolean = true,
    /** Tells the capture side whether this screen is shown and resumed: captures are accepted only then (C21). */
    onScreenActive: (Boolean) -> Unit = {},
) {
    // Leaving the tab disposes this screen; going to the background pauses it. Either way captures stop.
    LifecycleResumeEffect(Unit) {
        onScreenActive(true)
        onPauseOrDispose { onScreenActive(false) }
    }
    if (syncing) {
        SyncingSurface(modifier)
        return
    }
    val focus = remember { FocusRequester() }
    // Found hands the focus to "Cantidad" so a quantity can be typed right after a scan; every other state keeps it on the input.
    LaunchedEffect(state is CaptureState.Found) { if (state !is CaptureState.Found) focus.requestFocus() }
    val outcome = state is CaptureState.Found || state is CaptureState.NotFound || state == CaptureState.Error
    // The group sits in the middle of the area between the status strip and the tab bar, as in the frames.
    NoSoftKeyboard(physicalKeyboard) { Box(modifier.fillMaxSize().testTag("screen-scan"), contentAlignment = Alignment.Center) {
        // Nothing scrolls: every state fits above the tab bar with at least 16 px to spare (UX, Fit).
        Column(
            Modifier.padding(horizontal = SideMargin, vertical = KidaGoSpacing.Gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The illustration belongs to the screen base only; a result shows the code inside its card (redesign 2026-11-21).
            if (!outcome) {
                HeroBarcode()
                Spacer(Modifier.height(IllustrationGap))
            }
            // In a result the field stays composed as an unseen focus holder, so the focus rules do not change.
            CodeField(
                pill = !outcome,
                typed = typed,
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
            ) { shown -> Outcome(shown, onQuantitySubmit, onFinish, onQuantityEdit) }
        }
    } }
}

/**
 * The scanning surface while a catalog sync runs (Project UX Specification, Screen Composition; FEAT-001 UI Definition):
 * disabled, not merely dimmed. The input is not composed at all, so there is no field to focus or type into, and the
 * mascot with the updating message fills the area. When the sync ends the screen composes again and focuses the field.
 */
@Composable
internal fun SyncingSurface(modifier: Modifier) {
    Box(modifier.fillMaxSize().testTag("screen-scan"), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = 20.dp).testTag("scan-disabled"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // The project's mascot outline (the dog face), reused unchanged from the shared empty state.
            Box(Modifier.size(83.dp).testTag("scan-mascot"), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_tab_products), contentDescription = null, modifier = Modifier.size(72.dp))
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
internal fun HeroBarcode() {
    val density = LocalDensity.current
    Box(Modifier.size(HeroWidth, HeroHeight).testTag("scan-hero"), contentAlignment = Alignment.Center) {
        Text(
            HERO_TEXT,
            // Fixed size, like the artwork: it must not follow the user's font scale.
            style = TextStyle(fontFamily = BarcodeFont, fontSize = with(density) { HeroFontSize.toSp() }, letterSpacing = with(density) { HeroLetterSpacing.toSp() }, color = KidaGoColors.Text),
            maxLines = 1,
            softWrap = false,
            // The font draws the bars inside the line box, which is taller than the bars; centre the bars in the hero box.
            modifier = Modifier.offset(y = (-4 * HeroScale).dp),
        )
    }
}

/** Shows the typed digits grouped 1-6-6 (display only). Shared with Consultar precio's field. */
internal object CodeDisplay : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val shown = groupedBarcode(text.text)
        return TransformedText(AnnotatedString(shown), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = when { offset <= 1 -> offset; offset <= 7 -> offset + 1; else -> offset + 2 }.coerceAtMost(shown.length)
            override fun transformedToOriginal(offset: Int) = when { offset <= 1 -> offset; offset <= 8 -> offset - 1; else -> offset - 2 }.coerceIn(0, text.length)
        })
    }
}

@Composable
internal fun CodeField(
    pill: Boolean,
    typed: String,
    onTyped: (String) -> Unit,
    onSubmit: () -> Unit,
    focus: FocusRequester,
    placeholder: String = "Introduce o escanea un código",
    @DrawableRes icon: Int = R.drawable.ic_scan_field,
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
        visualTransformation = CodeDisplay,
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
                    Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(32.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (typed.isEmpty()) Text(placeholder, style = style.copy(letterSpacing = 0.sp, fontSize = 20.sp, lineHeight = 28.sp), color = KidaGoColors.TextMuted)
                        field()
                    }
                    if (typed.isNotEmpty()) ClearButton { onTyped("") }
                }
            } else {
                // A result shows its code in the card; the field keeps focus unseen.
                Box(Modifier.fillMaxWidth().height(1.dp), contentAlignment = Alignment.Center) { field() }
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
private fun Outcome(state: CaptureState, onQuantitySubmit: (String) -> Unit, onFinish: (String) -> Unit, onQuantityEdit: (String) -> Unit) {
    when (state) {
        CaptureState.Searching -> Column(Modifier.padding(top = 55.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CircularProgressIndicator(Modifier.size(36.dp).testTag("scan-spinner"), color = KidaGoColors.Accent, trackColor = KidaGoColors.Accent.copy(alpha = 0.1f), strokeWidth = 4.dp)
            Text("Buscando producto...", style = MaterialTheme.typography.labelLarge, color = KidaGoColors.TextMuted)
        }
        is CaptureState.Found -> {
            var draft by remember(state.occurrence) { mutableStateOf("") } // one per Found, so a Found after a Found starts empty
            Result("scan-outcome-found") {
                ResultCard(
                    ResultKind.Found, "Producto encontrado", "Añadido a la lista",
                    code = state.code, showPrice = true, priceCents = state.priceCents,
                    subtitleContent = if (state.wasExisting) ({ RescanWarning(state.quantity) }) else null,
                )
                FoundActions(draft, state.occurrence, { draft = it; onQuantityEdit(it) }) { onFinish(draft) }
            }
        }
        is CaptureState.NotFound -> Result("scan-outcome-notfound") {
            ResultCard(ResultKind.NotFound, "Producto no encontrado", "Comprueba el código o inténtalo de nuevo", code = state.code)
            ListoPill(Modifier.fillMaxWidth()) { onFinish("") }
        }
        CaptureState.Error -> Result("scan-outcome-error") {
            ResultCard(ResultKind.Error, "Error de lectura", "No se ha podido leer el código. Inténtalo de nuevo")
            ListoPill(Modifier.fillMaxWidth()) { onFinish("") }
        }
        CaptureState.Idle, CaptureState.Typing -> Unit // the input alone carries these states
    }
}

/** A result: the "Escanear" label on the right, then the card and its action row. */
@Composable
private fun Result(tag: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(BlockGap)) {
        ScreenLabel("Escanear", R.drawable.ic_scan_field, red = false, modifier = Modifier.align(Alignment.End))
        content()
    }
}

/** Rescan: alert icon plus text, so the warning never relies on colour (C5). */
@Composable
private fun RescanWarning(total: Int) {
    Row(Modifier.testTag("scan-rescan"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(R.drawable.ic_alert_circle), contentDescription = null, tint = KidaGoColors.Text, modifier = Modifier.size(20.dp))
        Text("Ya escaneado · Total acumulado: $total", style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp), color = KidaGoColors.Text)
    }
}

/** Found's action row: "Cantidad" filling the row, with "Listo" 12 px to its right, aligned to the field. */
@Composable
private fun FoundActions(draft: String, occurrence: Int, onDraft: (String) -> Unit, onFinish: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(BlockGap)) {
        Cantidad(draft, occurrence, onDraft, onFinish, Modifier.weight(1f))
        ListoPill(onClick = onFinish)
    }
}

/**
 * Labelled numeric field: the label (22 px) above a 64 px field with a 6 px gap, ghost `1`, at most two digits. A value
 * outside 1..99 empties back to the ghost with no message.
 */
@Composable
private fun Cantidad(draft: String, occurrence: Int, onDraft: (String) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(occurrence) { focus.requestFocus() } // on every Found, including a Found after a Found
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

/** "Listo" (C20): the labelled pill that finishes the scan; "Listo" is also its accessible name. */
@Composable
private fun ListoPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    ActionPill("Listo", checkPainter(), onClick, modifier.testTag("scan-listo"))
}
