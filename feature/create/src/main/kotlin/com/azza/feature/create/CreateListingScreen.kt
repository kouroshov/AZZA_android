package com.azza.feature.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import coil.compose.AsyncImage
import com.azza.core.common.AppError
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.AttributeDataType
import com.azza.core.model.FieldErrorCode
import com.azza.core.model.ItemCondition
import com.azza.core.model.LocalizedText
import com.azza.core.model.OfferingKind
import com.azza.core.model.SchemaField
import com.azza.core.model.SchemaRules
import com.azza.core.ui.errorMessage
import java.util.Locale
import kotlinx.serialization.Serializable

/** The web's Dashboard ("+"): the one place a post is created. */
@Serializable data object CreateRoute

/** The same form, opened from a store's page: "Post as" starts on that store. */
@Serializable data class CreateForStoreRoute(val storeId: String)

/** The same form, for a listing you own (the "⋯ › Edit" of the web's cards). */
@Serializable data class EditListingRoute(val productId: String)

fun NavGraphBuilder.createScreen(onPosted: (String) -> Unit, onSaved: () -> Unit, onDeleted: () -> Unit) {
    composable<CreateRoute> { CreateListingScreen(onPosted = onPosted, onDeleted = onDeleted) }
    composable<CreateForStoreRoute> { CreateListingScreen(onPosted = onPosted, onDeleted = onDeleted) }
    composable<EditListingRoute> { CreateListingScreen(onPosted = { onSaved() }, onDeleted = onDeleted) }
}

private fun LocalizedText.local(): String = forLanguage(Locale.getDefault().language)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateListingScreen(onPosted: (String) -> Unit, onDeleted: () -> Unit, viewModel: CreateListingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val colors = AzzaTheme.colors

    LaunchedEffect(state.postedId) { state.postedId?.let(onPosted) }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.edit_delete_title)) },
            text = { Text(stringResource(R.string.edit_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.edit_delete), color = colors.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.create_cancel)) } },
        )
    }
    if (state.loadingListing) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val editing = state.editingId != null

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { uris ->
        viewModel.addPhotos(uris.map { it.toString() })
    }

    if (picker.open) CategoryPickerDialog(picker, viewModel)

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(if (editing) R.string.edit_heading else R.string.create_heading), style = MaterialTheme.typography.headlineSmall)

        // 1. Post as — your personal page, or one of your stores.
        if (!editing && state.stores.isNotEmpty()) {
            Section(stringResource(R.string.create_post_as)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.isPersonal, { viewModel.postAs(null) }, { Text(stringResource(R.string.create_personal_page)) })
                    state.stores.forEach { store ->
                        FilterChip(state.storeId == store.id, { viewModel.postAs(store.id) }, { Text(store.name) })
                    }
                }
            }
        }

        // 2. Category, then what it is (a store may offer more than goods).
        Section(stringResource(R.string.create_category)) {
            val category = state.category
            OutlinedButton(onClick = viewModel::openPicker, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (category == null) stringResource(R.string.create_choose_category)
                    else (category.path + category.name).joinToString(" › ") { it.local() },
                )
            }
            if (FormError.CATEGORY in state.formErrors) ErrorText(stringResource(R.string.create_error_category))
            if (state.allowedKinds.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.allowedKinds.forEach { kind ->
                        FilterChip(state.offeringKind == kind, { viewModel.chooseKind(kind) }, { Text(stringResource(kind.label())) })
                    }
                }
            }
            if (FormError.GOODS_ONLY in state.formErrors) ErrorText(stringResource(R.string.create_error_goods_only))
        }

        // 3. The basics.
        Field(state.title, { v -> viewModel.edit { copy(title = v, formErrors = formErrors - FormError.TITLE) } }, R.string.create_title,
            error = (FormError.TITLE in state.formErrors).then(R.string.create_error_title))
        Field(state.description, { v -> viewModel.edit { copy(description = v, formErrors = formErrors - FormError.DESCRIPTION) } }, R.string.create_description,
            singleLine = false, error = (FormError.DESCRIPTION in state.formErrors).then(R.string.create_error_description))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(state.price, { v -> viewModel.edit { copy(price = v, formErrors = formErrors - FormError.PRICE) } }, R.string.create_price,
                type = KeyboardType.Decimal, modifier = Modifier.weight(1f), error = (FormError.PRICE in state.formErrors).then(R.string.create_error_price))
            Field(state.discount, { v -> viewModel.edit { copy(discount = v, formErrors = formErrors - FormError.DISCOUNT) } }, R.string.create_discount,
                type = KeyboardType.Decimal, modifier = Modifier.weight(1f), error = (FormError.DISCOUNT in state.formErrors).then(R.string.create_error_discount))
        }
        if (state.offeringKind == OfferingKind.GOOD) {
            Section(stringResource(R.string.create_condition)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ItemCondition.entries.forEach { c ->
                        FilterChip(state.condition == c, { viewModel.edit { copy(condition = c) } }, { Text(stringResource(c.label())) })
                    }
                }
            }
        }
        // A personal listing is one item, set by the server: there is no field for it (CLAUDE.md §4).
        if (!editing && !state.isPersonal) {
            Field(state.quantity, { v -> viewModel.edit { copy(quantity = v, formErrors = formErrors - FormError.QUANTITY) } },
                R.string.create_quantity_main_branch, type = KeyboardType.Number,
                error = (FormError.QUANTITY in state.formErrors).then(R.string.create_error_quantity))
        }

        // 4. The category's own fields, shown and required by its rules.
        if (state.schemaLoading) CircularProgressIndicator()
        state.schema?.let { schema ->
            val accepted = SchemaRules.validate(schema.fields, state.attributes, state.ruleContext).values
            schema.fields.filter { SchemaRules.isVisible(it, accepted, state.ruleContext) }.forEach { field ->
                AttributeInput(
                    field = field,
                    required = SchemaRules.isRequired(field, accepted, state.ruleContext),
                    value = state.attributes[field.slug],
                    error = state.fieldErrors[field.slug]?.code,
                    onChange = { viewModel.setAttribute(field.slug, it) },
                )
            }
        }

        // 5. Photos, before posting — the first is the cover.
        Section(stringResource(R.string.create_photos, state.existingPhotos.size + state.photos.size, MAX_PHOTOS)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.existingPhotos.forEachIndexed { index, photo ->
                    Box(Modifier.size(92.dp).clip(RoundedCornerShape(10.dp))) {
                        AsyncImage(photo.url, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        if (index == 0) {
                            Text(
                                stringResource(R.string.create_cover), style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.BottomStart).background(colors.bg.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        IconButton(onClick = { viewModel.removeExistingPhoto(photo) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.create_remove_photo), tint = colors.textPrimary,
                                modifier = Modifier.background(colors.bg.copy(alpha = 0.6f), RoundedCornerShape(50)))
                        }
                    }
                }
                state.photos.forEachIndexed { newIndex, uri ->
                    val index = newIndex + state.existingPhotos.size
                    val failed = uri in state.failedPhotos
                    Box(
                        Modifier.size(92.dp).clip(RoundedCornerShape(10.dp))
                            .then(if (failed) Modifier.border(2.dp, colors.error, RoundedCornerShape(10.dp)) else Modifier),
                    ) {
                        AsyncImage(uri, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                        if (index == 0) {
                            Text(
                                stringResource(R.string.create_cover), style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.align(Alignment.BottomStart).background(colors.bg.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (uri !in state.uploaded) {
                            IconButton(onClick = { viewModel.removePhoto(uri) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.create_remove_photo), tint = colors.textPrimary,
                                    modifier = Modifier.background(colors.bg.copy(alpha = 0.6f), RoundedCornerShape(50)))
                            }
                        }
                    }
                }
                if (state.existingPhotos.size + state.photos.size < MAX_PHOTOS) {
                    Box(
                        Modifier.size(92.dp).clip(RoundedCornerShape(10.dp)).background(colors.surfaceRaised)
                            .clickable { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.create_add_photos), tint = colors.textSecondary) }
                }
            }
            if (state.failedPhotos.isNotEmpty()) ErrorText(stringResource(R.string.create_photos_failed, state.failedPhotos.size))
        }

        if (state.stockNotPlaced) ErrorText(stringResource(R.string.create_stock_not_placed))
        state.error?.let { ErrorText(postErrorText(it)) }

        Button(onClick = viewModel::post, enabled = !state.posting, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            if (state.posting) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            else Text(
                stringResource(
                    when {
                        editing -> R.string.edit_save
                        state.pendingId != null -> R.string.create_post_again
                        else -> R.string.create_post
                    },
                ),
            )
        }
        if (editing) {
            TextButton(onClick = { confirmDelete = true }, enabled = !state.posting, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.edit_delete), color = colors.error)
            }
        }
    }
}

private fun Boolean.then(res: Int): Int? = if (this) res else null

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = AzzaTheme.colors.textSecondary)
        content()
    }
}

@Composable
private fun ErrorText(text: String) = Text(text, color = AzzaTheme.colors.error, style = MaterialTheme.typography.bodySmall)

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    type: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    error: Int? = null,
) = OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(stringResource(label)) },
    singleLine = singleLine,
    minLines = if (singleLine) 1 else 3,
    isError = error != null,
    supportingText = error?.let { { Text(stringResource(it)) } },
    keyboardOptions = KeyboardOptions(keyboardType = type),
    // What a seller writes is laid out by its own direction, whatever the UI's.
    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
    modifier = modifier,
)

/** One schema field, by its type; the rules (SchemaRules) decide visibility, required-ness and errors. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttributeInput(field: SchemaField, required: Boolean, value: Any?, error: FieldErrorCode?, onChange: (Any?) -> Unit) {
    val label = field.label.local() + (field.unit?.let { " ($it)" } ?: "") + if (required) " *" else ""
    val errorText = error?.let { stringResource(it.message()) }
    when (field.dataType) {
        AttributeDataType.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Switch(checked = value == true, onCheckedChange = onChange)
        }
        AttributeDataType.ENUM -> {
            var expanded by remember { mutableStateOf(false) }
            val options = field.options.orEmpty()
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = options.firstOrNull { it.value == value }?.label?.local().orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(label) },
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    if (!required) DropdownMenuItem(text = { Text("—") }, onClick = { onChange(null); expanded = false })
                    options.forEach { option ->
                        DropdownMenuItem(text = { Text(option.label.local()) }, onClick = { onChange(option.value); expanded = false })
                    }
                }
            }
        }
        AttributeDataType.MULTI_ENUM -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            val chosen = (value as? List<*>)?.filterIsInstance<String>().orEmpty()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                field.options.orEmpty().forEach { option ->
                    val on = option.value in chosen
                    FilterChip(on, { onChange(if (on) chosen - option.value else chosen + option.value) }, { Text(option.label.local()) })
                }
            }
            errorText?.let { ErrorText(it) }
        }
        else -> OutlinedTextField(
            value = (value as? String).orEmpty(),
            onValueChange = { onChange(it) },
            label = { Text(label) },
            placeholder = if (field.dataType == AttributeDataType.DATE) ({ Text("YYYY-MM-DD") }) else null,
            singleLine = field.dataType != AttributeDataType.LONG_TEXT,
            minLines = if (field.dataType == AttributeDataType.LONG_TEXT) 3 else 1,
            isError = errorText != null,
            supportingText = (errorText ?: field.help?.local())?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                keyboardType = when (field.dataType) {
                    AttributeDataType.NUMBER -> KeyboardType.Decimal
                    AttributeDataType.DATE -> KeyboardType.Number
                    else -> KeyboardType.Text
                },
            ),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CategoryPickerDialog(state: CategoryPickerState, viewModel: CreateListingViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closePicker,
        title = { Text(stringResource(R.string.create_choose_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::searchCategories,
                    placeholder = { Text(stringResource(R.string.create_search_categories)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.query.trim().length >= 2 -> LazyColumn(Modifier.heightIn(max = 400.dp)) {
                        items(state.hits, key = { it.id }) { hit ->
                            Column(Modifier.fillMaxWidth().clickable { viewModel.chooseHit(hit) }.padding(vertical = 10.dp)) {
                                Text(hit.name.local())
                                if (hit.path.isNotEmpty()) {
                                    Text(hit.path.joinToString(" › ") { it.local() }, style = MaterialTheme.typography.bodySmall, color = AzzaTheme.colors.textMuted)
                                }
                            }
                        }
                    }
                    else -> {
                        val nodes = state.trail.lastOrNull()?.children ?: state.roots
                        if (state.trail.isNotEmpty()) {
                            TextButton(onClick = viewModel::up) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                Text(state.trail.joinToString(" › ") { it.name.local() })
                            }
                        }
                        LazyColumn(Modifier.heightIn(max = 400.dp)) {
                            items(nodes, key = { it.id }) { node ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { viewModel.openNode(node) }.padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(node.name.local(), modifier = Modifier.weight(1f))
                                    if (node.children.isNotEmpty()) Icon(Icons.Outlined.ChevronRight, null, tint = AzzaTheme.colors.textMuted)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = viewModel::closePicker) { Text(stringResource(R.string.create_cancel)) } },
    )
}

private fun OfferingKind.label(): Int = when (this) {
    OfferingKind.GOOD -> R.string.kind_good
    OfferingKind.SERVICE -> R.string.kind_service
    OfferingKind.BOOKABLE -> R.string.kind_bookable
    OfferingKind.RENTAL -> R.string.kind_rental
    OfferingKind.DIGITAL -> R.string.kind_digital
    OfferingKind.ACCESS -> R.string.kind_access
    OfferingKind.UNKNOWN -> R.string.kind_good
}

private fun ItemCondition.label(): Int = when (this) {
    ItemCondition.NEW -> R.string.condition_new
    ItemCondition.LIKE_NEW -> R.string.condition_like_new
    ItemCondition.USED -> R.string.condition_used
    ItemCondition.FOR_PARTS -> R.string.condition_for_parts
}

private fun FieldErrorCode.message(): Int = when (this) {
    FieldErrorCode.REQUIRED -> R.string.field_error_required
    FieldErrorCode.TOO_SMALL -> R.string.field_error_too_small
    FieldErrorCode.TOO_LARGE -> R.string.field_error_too_large
    FieldErrorCode.TOO_LONG -> R.string.field_error_too_long
    FieldErrorCode.PATTERN_MISMATCH -> R.string.field_error_pattern
    else -> R.string.field_error_invalid
}

@Composable
private fun postErrorText(error: AppError): String = when ((error as? AppError.Api)?.code) {
    "PERSONAL_LISTING_GOODS_ONLY" -> stringResource(R.string.create_error_goods_only)
    "ACCOUNT_NOT_ACTIVE" -> stringResource(R.string.create_error_not_active)
    "MEDIA_LIMIT_REACHED" -> stringResource(R.string.create_error_media_limit)
    "STORE_MODERATED" -> stringResource(R.string.create_error_store_moderated)
    else -> errorMessage(error)
}
