package com.afede.kidago.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val ButtonPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)

/** Primary action: brand-red pill. Pass [destructive] = false for the neutral variant. */
@Composable
fun KidaGoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = KidaGoShapes.Pill,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) KidaGoColors.Accent else KidaGoColors.Text,
            contentColor = KidaGoColors.Surface,
            disabledContainerColor = KidaGoColors.Border,
            disabledContentColor = KidaGoColors.Text,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Secondary/neutral action (e.g. declining a confirmation): outlined pill. */
@Composable
fun KidaGoNeutralButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = KidaGoShapes.Pill,
        contentPadding = ButtonPadding,
        border = BorderStroke(1.dp, KidaGoColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = KidaGoColors.Surface,
            contentColor = KidaGoColors.Text,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Toggle: red when on, neutral when off. */
@Composable
fun KidaGoSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = KidaGoColors.Surface,
            checkedTrackColor = KidaGoColors.Accent,
            checkedBorderColor = KidaGoColors.Accent,
            uncheckedThumbColor = KidaGoColors.Surface,
            uncheckedTrackColor = KidaGoColors.Border,
            uncheckedBorderColor = KidaGoColors.Border,
        ),
    )
}

/** Input: white pill with a light border; accent border when focused. */
@Composable
fun KidaGoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = KidaGoColors.Border) } },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        shape = KidaGoShapes.Pill,
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = KidaGoColors.Surface,
            unfocusedContainerColor = KidaGoColors.Surface,
            focusedBorderColor = KidaGoColors.Accent,
            unfocusedBorderColor = KidaGoColors.Border,
            focusedLabelColor = KidaGoColors.Accent,
            unfocusedLabelColor = KidaGoColors.Text,
            cursorColor = KidaGoColors.Accent,
        ),
    )
}
