package com.afede.kidago.ui.catalog

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.afede.kidago.R
import com.afede.kidago.data.RecordDiagnostic
import com.afede.kidago.data.SyncState
import com.afede.kidago.ui.theme.KidaGoColors
import com.afede.kidago.ui.theme.KidaGoShapes
import com.afede.kidago.ui.theme.KidaGoSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Strip copy (FEAT-001 UX/UI). Diagnostic wording is a provisional proposal: the UX leaves it an open asset decision.
private const val NO_ACCESS_TEXT = "Falta el permiso de archivos - toca para concederlo"
private const val ERROR_TEXT = "Error al sincronizar - toca para ver detalles"
private const val ERROR_WARNING = "Puede que tengas que volver a hacer Importar."

private enum class StripIcon { Dot, Spinner, Error, Lock }

private val IconSize = 16.dp
private val Time = DateTimeFormatter.ofPattern("HH:mm")
private val DateAndTime = DateTimeFormatter.ofPattern("dd/MM HH:mm")

/**
 * The catalog status strip for the shared header (FEAT-001 UX/UI): Up To Date, Syncing, Error (tap to expand the
 * diagnostic panel in place) and No File Access (tap to reopen the settings screen). There is no manual sync action.
 */
@Composable
fun CatalogStatusStrip(
    state: SyncState,
    onNoFileAccessTap: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        when (state) {
            is SyncState.UpToDate -> StripRow(StripIcon.Dot, upToDateText(state.completedAt, zone), "strip-uptodate")
            is SyncState.Syncing -> {
                val fraction = state.progress.coerceIn(0f, 1f)
                Column(Modifier.testTag("strip-syncing")) {
                    StripRow(StripIcon.Spinner, "Sincronizando productos... ${(fraction * 100).toInt()}%")
                    ProgressBar(fraction)
                }
            }
            is SyncState.Error -> ErrorStrip(state)
            SyncState.NoFileAccess -> StripRow(StripIcon.Lock, NO_ACCESS_TEXT, "strip-noaccess", onClick = onNoFileAccessTap, role = Role.Button)
        }
    }
}

private fun upToDateText(completedAt: Instant?, zone: ZoneId): String {
    if (completedAt == null) return "Datos actualizados" // nothing imported yet, so there is no time to show
    val at = completedAt.atZone(zone)
    return if (at.toLocalDate() == LocalDate.now(zone)) "Datos actualizados - hoy ${Time.format(at)}"
    else "Datos actualizados - ${DateAndTime.format(at)}"
}

@Composable
private fun StripRow(icon: StripIcon, text: String, tag: String? = null, onClick: (() -> Unit)? = null, role: Role? = null) {
    var row = Modifier.fillMaxWidth()
    if (tag != null) row = row.testTag(tag)
    if (onClick != null) row = row.clickable(role = role, onClick = onClick)
    Row(row.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KidaGoSpacing.Gap)) {
        StatusIcon(icon)
        Text(text, style = MaterialTheme.typography.labelMedium, color = KidaGoColors.Text)
    }
}

/** The icon slot. Only a finished sync animates: the spinner resolves once into the dot or the error triangle. */
@Composable
private fun StatusIcon(icon: StripIcon) {
    AnimatedContent(
        targetState = icon,
        transitionSpec = {
            if (initialState == StripIcon.Spinner) fadeIn(tween(350)) togetherWith fadeOut(tween(350))
            else EnterTransition.None togetherWith ExitTransition.None
        },
        label = "strip-icon",
    ) { kind ->
        Box(Modifier.size(IconSize), contentAlignment = Alignment.Center) {
            when (kind) {
                StripIcon.Dot -> Box(Modifier.size(12.dp).clip(KidaGoShapes.Pill).background(KidaGoColors.SuccessText).iconDescription("Actualizado"))
                StripIcon.Spinner -> CircularProgressIndicator(
                    modifier = Modifier.size(IconSize).iconDescription("Sincronizando"),
                    color = KidaGoColors.Accent,
                    strokeWidth = 2.dp,
                )
                StripIcon.Error -> DrawableIcon(R.drawable.ic_status_error, "Error")
                StripIcon.Lock -> DrawableIcon(R.drawable.ic_status_lock, "Sin permiso de archivos")
            }
        }
    }
}

@Composable
private fun DrawableIcon(@DrawableRes id: Int, description: String) {
    androidx.compose.foundation.Image(painterResource(id), contentDescription = description, modifier = Modifier.size(IconSize))
}

private fun Modifier.iconDescription(text: String) = semantics { contentDescription = text }

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp).clip(KidaGoShapes.Pill)
            .background(KidaGoColors.Accent.copy(alpha = 0.25f)).testTag("strip-progress"),
    ) {
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(KidaGoColors.Accent))
    }
}

@Composable
private fun ErrorStrip(state: SyncState.Error) {
    var expanded by remember { mutableStateOf(false) } // leaving Error leaves the composition, so it collapses again
    Column(Modifier.testTag("strip-error")) {
        StripRow(StripIcon.Error, ERROR_TEXT, "strip-error-row", onClick = { expanded = !expanded }, role = Role.Button)
        AnimatedVisibility(expanded) { ErrorDetail(state) } // grows in place and pushes the content below down
    }
}

@Composable
private fun ErrorDetail(state: SyncState.Error) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp)
            .background(KidaGoColors.Surface, KidaGoShapes.Card)
            .border(1.dp, KidaGoColors.Border, KidaGoShapes.Card)
            .padding(KidaGoSpacing.Gap)
            .testTag("strip-error-detail"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(ERROR_WARNING, style = MaterialTheme.typography.labelMedium, color = KidaGoColors.Text)
        state.diagnostics.forEach { Text(describe(it), style = MaterialTheme.typography.labelMedium, color = KidaGoColors.Text) }
        val hidden = state.failureCount - state.diagnostics.size
        if (hidden > 0) Text("y $hidden más", style = MaterialTheme.typography.labelMedium, color = KidaGoColors.Text)
    }
}

private fun describe(d: RecordDiagnostic) =
    if (d.lineNumber == 0) "${d.content}: ${d.reason}" else "Registro ${d.lineNumber}: «${d.content}» - ${d.reason}"
