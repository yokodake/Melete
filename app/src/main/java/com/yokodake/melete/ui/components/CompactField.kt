package com.yokodake.melete.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * A text field sized for a table rather than a form.
 *
 * Material's `OutlinedTextField` enforces a 56dp minimum height and 16dp of padding on every side,
 * which is right for a login screen and absurd for a grid of two-digit numbers: the box ends up
 * several times the size of what it holds, and a set of four rows fills the screen.
 *
 * None of that is reachable through parameters, so the field is assembled from `BasicTextField`
 * and Material's own decoration box — the same container, border and colours, with the padding
 * and the height set to what the content actually needs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supportingText: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    minHeight: Int = 40,
    textStyle: TextStyle = LocalTextStyle.current,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = minHeight.dp),
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = textStyle.merge(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                colors = colors,
                label = label?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } },
                placeholder = placeholder?.let {
                    { Text(it, style = MaterialTheme.typography.bodyMedium) }
                },
                trailingIcon = trailingIcon,
                supportingText = supportingText?.let {
                    { Text(it, style = MaterialTheme.typography.bodySmall) }
                },
                // The whole point: Material's default is 16dp on every side.
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = colors,
                    )
                },
            )
        },
    )
}
