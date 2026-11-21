package com.afede.kidago.ui.price

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.afede.kidago.R
import com.afede.kidago.ui.NoSoftKeyboard
import com.afede.kidago.ui.scan.ActionPill
import com.afede.kidago.ui.scan.CodeField
import com.afede.kidago.ui.scan.HeroBarcode
import com.afede.kidago.ui.scan.ResultCard
import com.afede.kidago.ui.scan.ResultKind
import com.afede.kidago.ui.scan.ScreenLabel
import com.afede.kidago.ui.scan.SyncingSurface
import com.afede.kidago.ui.theme.KidaGoColors
import com.afede.kidago.ui.theme.KidaGoSpacing

/**
 * Consultar precio (FEAT-006 UI Definition): the same base as Escanear (illustration and field, with the euro icon), and
 * for a result the red "Consultar precio" label, the result card with the price in its pink block, and "Volver".
 */
@Composable
fun PrecioScreen(
    state: PriceState,
    typed: String,
    onTyped: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    syncing: Boolean = false,
    physicalKeyboard: Boolean = true,
    onScreenActive: (Boolean) -> Unit = {},
) {
    LifecycleResumeEffect(Unit) {
        onScreenActive(true)
        onPauseOrDispose { onScreenActive(false) }
    }
    val result = state is PriceState.Found || state is PriceState.NotFound || state == PriceState.Error
    BackHandler(enabled = result, onBack = onBack) // the system back key does what "Volver" does
    if (syncing) {
        SyncingSurface(modifier)
        return
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    NoSoftKeyboard(physicalKeyboard) { Box(modifier.fillMaxSize().testTag("screen-price"), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(horizontal = KidaGoSpacing.Outer, vertical = KidaGoSpacing.Gap), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!result) {
                HeroBarcode()
                Spacer(Modifier.height(32.dp))
            }
            CodeField(
                pill = !result,
                typed = typed,
                onTyped = onTyped,
                onSubmit = onSubmit,
                focus = focus,
                placeholder = "Escanea para ver el precio",
                icon = R.drawable.ic_euro,
            )
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
                label = "price-outcome",
            ) { shown -> Outcome(shown, onBack) }
        }
    } }
}

@Composable
private fun Outcome(state: PriceState, onBack: () -> Unit) {
    when (state) {
        PriceState.Searching -> Column(Modifier.padding(top = 55.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CircularProgressIndicator(Modifier.size(36.dp).testTag("price-spinner"), color = KidaGoColors.Accent, trackColor = KidaGoColors.Accent.copy(alpha = 0.1f), strokeWidth = 4.dp)
            Text("Buscando producto...", style = MaterialTheme.typography.labelLarge, color = KidaGoColors.TextMuted)
        }
        is PriceState.Found -> Result("price-outcome-found", onBack) {
            ResultCard(ResultKind.Found, "Producto encontrado", "Escanea otro código para consultar", code = state.code, showPrice = true, priceCents = state.priceCents, priceBlock = true)
        }
        is PriceState.NotFound -> Result("price-outcome-notfound", onBack) {
            ResultCard(ResultKind.NotFound, "Producto no encontrado", "Comprueba el código o inténtalo de nuevo", code = state.code)
        }
        PriceState.Error -> Result("price-outcome-error", onBack) {
            ResultCard(ResultKind.Error, "Error de lectura", "No se ha podido leer el código. Inténtalo de nuevo")
        }
        PriceState.Idle -> Unit
    }
}

/** A result: the red label on the right, the card, and "Volver" at full width. */
@Composable
private fun Result(tag: String, onBack: () -> Unit, card: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenLabel("Consultar precio", R.drawable.ic_euro, red = true, modifier = Modifier.align(Alignment.End))
        card()
        ActionPill("Volver", painterResource(R.drawable.ic_arrow_back), onBack, Modifier.fillMaxWidth().testTag("price-back"))
    }
}
