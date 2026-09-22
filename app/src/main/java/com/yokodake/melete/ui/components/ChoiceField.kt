package com.yokodake.melete.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One of a handful of choices, as a single line.
 *
 * A row of chips states every option at once, which is the right trade when there are two or three
 * and you are choosing between them constantly. For a setting you pick once when you create an
 * exercise and then leave alone, it is the wrong one: four chips wrap onto two lines and take a
 * whole band of the screen to say something that is usually already right. A closed dropdown says
 * the answer in one line and costs one tap to change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceField(
    value: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    supportingText: String? = null,
    minHeight: Int = 44,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        CompactTextField(
            value = optionLabel(value),
            onValueChange = {},
            readOnly = true,
            label = label,
            supportingText = supportingText,
            minHeight = minHeight,
            trailingIcon = { DropdownChevron(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * The arrow that says a field opens.
 *
 * A filled triangle in a box of its own width, rather than Material's `TrailingIcon`: that one
 * carries a 48dp icon slot and was setting the height of every dropdown in the app. Hand-rolling
 * it went too far the other way at first — a hairline chevron crammed against the border — so the
 * box is what gives the arrow room without giving the field height.
 */
@Composable
private fun DropdownChevron(expanded: Boolean) {
    Box(
        modifier = Modifier.width(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (expanded) "▴" else "▾",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
