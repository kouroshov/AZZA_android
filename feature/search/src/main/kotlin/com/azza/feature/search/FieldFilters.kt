package com.azza.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azza.core.model.AttrFilter
import com.azza.core.model.AttributeDataType
import com.azza.core.model.CategorySchema
import com.azza.core.model.Money
import com.azza.core.model.SchemaField
import com.azza.core.model.SchemaRules

/** Field types a filter makes sense for; free text is the search bar's job. */
private val FILTERABLE = setOf(AttributeDataType.ENUM, AttributeDataType.MULTI_ENUM, AttributeDataType.BOOLEAN, AttributeDataType.NUMBER, AttributeDataType.MONEY)
private const val MAX_FIELDS = 8

/**
 * ✅ (2026-10-23) The chosen category's kinds of ad and own fields, inside
 * the filter sheet (the web's ExploreFilterPanel): any of the ticked
 * options, a yes, or a from–to range. A MONEY range is typed in toman and
 * kept in rial, as apps/api compares it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FieldFilters(
    schema: CategorySchema,
    listingType: String?,
    onListingType: (String?) -> Unit,
    attrs: Map<String, AttrFilter>,
    onAttr: (String, AttrFilter?) -> Unit,
) {
    val language = LocalConfiguration.current.locales[0].language
    if (schema.allowedListingTypes.size > 1) {
        Text(stringResource(R.string.filters_listing_type), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = listingType == null, onClick = { onListingType(null) }, label = { Text(stringResource(R.string.filters_any)) })
            schema.allowedListingTypes.forEach { t ->
                FilterChip(selected = listingType == t.code, onClick = { onListingType(t.code) }, label = { Text(t.name.forLanguage(language)) })
            }
        }
    }
    schema.fields.filter { it.dataType in FILTERABLE }.take(MAX_FIELDS).forEach { field ->
        FieldFilter(field, language, attrs[field.slug]) { onAttr(field.slug, it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldFilter(field: SchemaField, language: String, value: AttrFilter?, onChange: (AttrFilter?) -> Unit) {
    val label = field.label.forLanguage(language) + (field.unit?.let { " (${com.azza.core.ui.localizeUnit(it)})" } ?: "")
    when (field.dataType) {
        AttributeDataType.BOOLEAN -> FilterChip(
            selected = value == AttrFilter.Yes,
            onClick = { onChange(if (value == AttrFilter.Yes) null else AttrFilter.Yes) },
            label = { Text(field.label.forLanguage(language)) },
        )
        AttributeDataType.NUMBER, AttributeDataType.MONEY -> {
            val money = field.dataType == AttributeDataType.MONEY
            val range = value as? AttrFilter.Range ?: AttrFilter.Range()
            fun shown(n: Double?) = n?.let { if (money) (it / Money.RIAL_PER_TOMAN).toLong().toString() else it.toLong().toString() }.orEmpty()
            fun kept(text: String) = SchemaRules.toLatinDigits(text).replace(Regex("[,٬\\s]"), "").toDoubleOrNull()
                ?.takeIf { it >= 0 }?.let { if (money) Money.tomanToRial(it) else it }
            fun set(next: AttrFilter.Range) = onChange(next.takeIf { it.min != null || it.max != null })
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label + if (money) " — " + stringResource(R.string.filters_toman) else "", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = shown(range.min),
                        onValueChange = { set(range.copy(min = kept(it))) },
                        label = { Text(stringResource(R.string.filters_from)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = shown(range.max),
                        onValueChange = { set(range.copy(max = kept(it))) },
                        label = { Text(stringResource(R.string.filters_to)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        else -> {
            val options = field.options.orEmpty()
            if (options.isEmpty()) return
            val picked = (value as? AttrFilter.Options)?.values.orEmpty()
            Text(label, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { o ->
                    val on = o.value in picked
                    FilterChip(
                        selected = on,
                        onClick = {
                            val next = if (on) picked - o.value else picked + o.value
                            onChange(next.takeIf { it.isNotEmpty() }?.let { AttrFilter.Options(it) })
                        },
                        label = { Text(o.label.forLanguage(language)) },
                    )
                }
            }
        }
    }
}
