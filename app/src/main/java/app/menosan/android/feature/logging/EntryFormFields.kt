package app.menosan.android.feature.logging

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Recycling
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.menosan.android.R
import app.menosan.android.core.model.EntryRules
import app.menosan.android.core.model.Taxonomy
import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.ui.components.Pill
import app.menosan.android.core.ui.theme.MenosanTheme

/**
 * The shared entry form fields (mockup `Manual Waste Entry`): category tiles, subcategory, item name, quantity.
 * Stateless: the caller owns [state]. Used by manual logging and editing (AN-1) and photo review (AN-2).
 *
 * Fields in [aiSuggested] are tinted and show an "AI suggestion" chip until the user edits them or taps the chip
 * to confirm ([onConfirmField]) (NFR10, SFR9.2). Errors show only when [showErrors] is true (after a save attempt).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryFormFields(
    state: EntryFormState,
    taxonomy: Taxonomy,
    onChange: (EntryFormState) -> Unit,
    modifier: Modifier = Modifier,
    showErrors: Boolean = false,
    aiSuggested: Set<EntryField> = emptySet(),
    onConfirmField: (EntryField) -> Unit = {},
    enabled: Boolean = true,
) {
    val errors = if (showErrors) state.errors(taxonomy) else emptyMap()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Category
        Section(R.string.entry_category_title, R.string.entry_category_hint, EntryField.CATEGORY, aiSuggested, onConfirmField) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WasteCategory.entries.forEach { category ->
                    CategoryTile(
                        category = category,
                        selected = state.category == category,
                        enabled = enabled,
                        onClick = { onChange(state.withCategory(category)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.category == WasteCategory.SPECIAL) {
                Text(
                    stringResource(R.string.entry_special_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FieldError(errors[EntryField.CATEGORY])
        }

        // Subcategory
        Section(R.string.entry_subcategory_title, R.string.entry_subcategory_hint, EntryField.SUBCATEGORY, aiSuggested, onConfirmField) {
            var expanded by remember { mutableStateOf(false) }
            val options = state.category?.let(taxonomy::subcategoriesOf) ?: taxonomy.subcategories.sortedBy { it.category.ordinal * 100 + it.sortOrder }
            val selected = state.subcategory?.let(taxonomy::subcategory)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
                OutlinedTextField(
                    value = selected?.label.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    placeholder = { Text(stringResource(R.string.entry_subcategory_placeholder)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    isError = errors.containsKey(EntryField.SUBCATEGORY),
                    colors = fieldColors(EntryField.SUBCATEGORY in aiSuggested),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        option.examples.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                onChange(state.withSubcategory(option.code, taxonomy))
                                expanded = false
                            },
                        )
                    }
                }
            }
            FieldError(errors[EntryField.SUBCATEGORY])
        }

        // Item name
        Section(R.string.entry_name_title, null, EntryField.NAME, aiSuggested, onConfirmField) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { onChange(state.withName(it)) },
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.entry_name_placeholder)) },
                supportingText = {
                    Text(
                        "${state.name.length} / ${EntryRules.NAME_MAX}",
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                isError = errors.containsKey(EntryField.NAME),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = fieldColors(EntryField.NAME in aiSuggested),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            FieldError(errors[EntryField.NAME])
        }

        // Quantity
        Section(R.string.entry_quantity_title, R.string.entry_quantity_hint, EntryField.QUANTITY, aiSuggested, onConfirmField) {
            OutlinedTextField(
                value = state.quantity,
                onValueChange = { onChange(state.withQuantity(it)) },
                enabled = enabled,
                placeholder = { Text(stringResource(R.string.entry_quantity_placeholder)) },
                suffix = { Text(stringResource(R.string.entry_quantity_unit)) },
                isError = errors.containsKey(EntryField.QUANTITY),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors(EntryField.QUANTITY in aiSuggested),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            FieldError(errors[EntryField.QUANTITY])
        }
    }
}

@Composable
private fun Section(
    title: Int,
    hint: Int?,
    field: EntryField,
    aiSuggested: Set<EntryField>,
    onConfirmField: (EntryField) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                if (hint != null) {
                    Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (field in aiSuggested) {
                Pill(
                    text = stringResource(R.string.entry_ai_suggestion),
                    background = MenosanTheme.colors.pending,
                    content = MenosanTheme.colors.onPending,
                    icon = Icons.Outlined.AutoAwesome,
                    modifier = Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.entry_ai_confirm),
                    ) { onConfirmField(field) },
                )
            }
        }
        content()
    }
}

@Composable
private fun fieldColors(aiSuggested: Boolean) = if (aiSuggested) {
    val tint = MenosanTheme.colors.pending.copy(alpha = 0.35f)
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = tint,
        unfocusedContainerColor = tint,
        disabledContainerColor = tint,
        unfocusedBorderColor = MenosanTheme.colors.highlight,
    )
} else {
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MenosanTheme.colors.card,
        unfocusedContainerColor = MenosanTheme.colors.card,
        disabledContainerColor = MenosanTheme.colors.card,
    )
}

@Composable
private fun FieldError(message: Int?) {
    if (message != null) {
        Text(stringResource(message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CategoryTile(
    category: WasteCategory,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MenosanTheme.colors.calm
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MenosanTheme.colors.onCalm
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .aspectRatio(0.9f)
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(category.icon(), contentDescription = null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(category.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/** The line icon of a main category (entry form tiles, entry rows, details). */
fun WasteCategory.icon(): ImageVector = when (this) {
    WasteCategory.BIODEGRADABLE -> Icons.Outlined.Eco
    WasteCategory.RECYCLABLE -> Icons.Outlined.Recycling
    WasteCategory.RESIDUAL -> Icons.Outlined.DeleteOutline
    WasteCategory.SPECIAL -> Icons.Outlined.BatteryAlert
}
