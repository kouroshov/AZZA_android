package com.azza.feature.create

import com.azza.core.model.CategorySuggestion
import com.azza.core.model.OccupationRef
import com.azza.core.model.OptionDraft
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.ListingRepository
import com.azza.core.model.CategoryHit
import com.azza.core.model.CategoryNode
import com.azza.core.model.CategorySchema
import com.azza.core.model.ExistingPhoto
import com.azza.core.model.FieldError
import com.azza.core.model.ItemCondition
import com.azza.core.model.ListingDraft
import com.azza.core.model.LocalizedText
import com.azza.core.model.OfferingKind
import com.azza.core.model.OwnStore
import com.azza.core.model.RuleContext
import com.azza.core.model.SchemaRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The category the listing is filed under, with how it reads ("Home › Furniture › Wardrobe"). */
data class ChosenCategory(val id: String, val name: LocalizedText, val path: List<LocalizedText>)

data class CategoryPickerState(
    val open: Boolean = false,
    val query: String = "",
    val hits: List<CategoryHit> = emptyList(),
    /** Browsing the tree: the nodes from the root to the one being looked into. */
    val trail: List<CategoryNode> = emptyList(),
    val roots: List<CategoryNode> = emptyList(),
    val loading: Boolean = false,
)

data class CreateUiState(
    val stores: List<OwnStore> = emptyList(),
    /** Null = the personal page. */
    val storeId: String? = null,
    val category: ChosenCategory? = null,
    val schema: CategorySchema? = null,
    val schemaLoading: Boolean = false,
    val offeringKind: OfferingKind = OfferingKind.GOOD,
    val title: String = "",
    val description: String = "",
    val price: String = "",
    val discount: String = "",
    /** The seller's options ("extra sauce"): up to 20 (apps/api's MAX_PRODUCT_OPTIONS). */
    val options: List<OptionDraft> = emptyList(),
    val condition: ItemCondition = ItemCondition.USED,
    val quantity: String = "",
    /** Raw form values per attribute slug: String, Boolean or List<String>. */
    val attributes: Map<String, Any> = emptyMap(),
    val photos: List<String> = emptyList(),
    val fieldErrors: Map<String, FieldError> = emptyMap(),
    val formErrors: Set<FormError> = emptySet(),
    val posting: Boolean = false,
    val error: AppError? = null,
    /** A hidden listing an earlier attempt created; posting again continues it. */
    val pendingId: String? = null,
    val uploaded: Set<String> = emptySet(),
    val failedPhotos: List<String> = emptyList(),
    /** Editing a saved listing: its id, and the photos it already has. */
    val editingId: String? = null,
    val existingPhotos: List<ExistingPhoto> = emptyList(),
    val loadingListing: Boolean = false,
    val deleted: Boolean = false,
    val stockNotPlaced: Boolean = false,
    val postedId: String? = null,
    /** Free-form "name: value" features. */
    val features: List<Pair<String, String>> = emptyList(),
    /** The occupation step (create only): chosen, or skipped (done with none). */
    val occupation: OccupationRef? = null,
    val occupationDone: Boolean = false,
    val occupationQuery: String = "",
    val occupationHits: List<OccupationRef> = emptyList(),
    val myOccupations: List<OccupationRef> = emptyList(),
    val suggestions: List<CategorySuggestion> = emptyList(),
) {
    val isPersonal: Boolean get() = storeId == null

    /** A personal page sells physical goods only (CLAUDE.md §4); a store may offer what the category allows. */
    val allowedKinds: List<OfferingKind>
        get() {
            val kinds = schema?.allowedKinds.orEmpty().ifEmpty { listOf(OfferingKind.GOOD) }
            return if (isPersonal) kinds.filter { it == OfferingKind.GOOD } else kinds
        }

    val ruleContext: RuleContext get() = RuleContext(condition = condition.name, offeringKind = offeringKind.name)
}

enum class FormError { CATEGORY, TITLE, DESCRIPTION, PRICE, DISCOUNT, QUANTITY, GOODS_ONLY }

const val MAX_PHOTOS = 10
const val MAX_STORE_QUANTITY = 1_000_000

@OptIn(FlowPreview::class)
@HiltViewModel
class CreateListingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listings: ListingRepository,
) : ViewModel() {

    /** Set when the screen was opened as EditListingRoute. */
    private val editId: String? = savedStateHandle.get<String>("productId")

    /** Set when the screen was opened as CreateForStoreRoute. */
    private val startStoreId: String? = savedStateHandle.get<String>("storeId")

    private val _state = MutableStateFlow(CreateUiState())
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    private var occupationJob: Job? = null

    /** The occupations this page already has; the main one is preselected, as on the web. */
    private fun loadMyOccupations() = viewModelScope.launch {
        val mine = (listings.myOccupations(_state.value.storeId) as? Outcome.Success)?.value.orEmpty()
        _state.update { it.copy(myOccupations = mine) }
        if (_state.value.occupation == null && !_state.value.occupationDone) mine.firstOrNull()?.let(::chooseOccupation)
    }

    fun searchOccupations(q: String) {
        _state.update { it.copy(occupationQuery = q) }
        occupationJob?.cancel()
        if (q.trim().length < 2) return _state.update { it.copy(occupationHits = emptyList()) }
        occupationJob = viewModelScope.launch {
            delay(300)
            val hits = (listings.searchOccupations(q) as? Outcome.Success)?.value.orEmpty()
            _state.update { it.copy(occupationHits = hits) }
        }
    }

    fun chooseOccupation(o: OccupationRef) {
        _state.update { it.copy(occupation = o, occupationDone = true, occupationQuery = "", occupationHits = emptyList(), suggestions = emptyList()) }
        viewModelScope.launch {
            val found = (listings.occupationSuggestions(o.id) as? Outcome.Success)?.value.orEmpty()
            _state.update { if (it.occupation?.id == o.id) it.copy(suggestions = found) else it }
        }
    }

    /** "Skip": no occupation is a real answer (one used phone). */
    fun skipOccupation() = _state.update { it.copy(occupation = null, occupationDone = true, suggestions = emptyList()) }

    fun changeOccupation() = _state.update { it.copy(occupationDone = false) }

    /** A suggestion picks the category and the kind in one tap. */
    fun chooseSuggestion(s: CategorySuggestion) {
        if (!_state.value.isPersonal || s.kind == OfferingKind.GOOD) _state.update { it.copy(offeringKind = s.kind) }
        choose(ChosenCategory(s.categoryId, s.name, s.path))
    }

    // ---- free-form features
    fun addFeature(name: String, value: String) {
        if (name.isBlank() || value.isBlank()) return
        _state.update { s -> s.copy(features = s.features.filterNot { it.first == name.trim() } + (name.trim() to value.trim())) }
    }

    fun removeFeature(name: String) = _state.update { s -> s.copy(features = s.features.filterNot { it.first == name }) }

    // ---- photo order: earlier/later and "make cover", for saved and new photos alike
    fun movePhotoLater(index: Int) {
        val n = _state.value.existingPhotos.size
        if (index < n - 1) movePhotoEarlier(index + 1)
    }

    fun makeCover(index: Int) {
        val before = _state.value.existingPhotos
        if (index <= 0 || index >= before.size) return
        val id = _state.value.editingId ?: return
        val after = listOf(before[index]) + before.filterIndexed { i, _ -> i != index }
        _state.update { it.copy(existingPhotos = after) }
        viewModelScope.launch {
            val r = listings.reorderPhotos(id, after.map { it.mediaId })
            if (r is Outcome.Failure) _state.update { it.copy(existingPhotos = before, error = r.error) }
        }
    }

    fun moveNewPhoto(index: Int, by: Int) = _state.update { s ->
        val to = index + by
        if (index !in s.photos.indices || to !in s.photos.indices) s
        else s.copy(photos = s.photos.toMutableList().apply { add(to, removeAt(index)) })
    }

    fun makeNewCover(index: Int) = _state.update { s ->
        if (index !in s.photos.indices) s else s.copy(photos = listOf(s.photos[index]) + s.photos.filterIndexed { i, _ -> i != index })
    }

    private val _picker = MutableStateFlow(CategoryPickerState())
    val picker: StateFlow<CategoryPickerState> = _picker.asStateFlow()

    private var searchJob: Job? = null

    init {
        if (editId != null) loadForEdit(editId) else viewModelScope.launch {
            (listings.myStores() as? Outcome.Success)?.let { r -> _state.update { it.copy(stores = r.value) } }
            startStoreId?.takeIf { id -> _state.value.stores.any { it.id == id } }?.let(::postAs)
            loadMyOccupations()
        }
    }

    private fun loadForEdit(id: String) {
        _state.update { it.copy(editingId = id, loadingListing = true) }
        viewModelScope.launch {
            when (val r = listings.loadForEdit(id)) {
                is Outcome.Failure -> _state.update { it.copy(loadingListing = false, error = r.error) }
                is Outcome.Success -> {
                    val l = r.value
                    _state.update {
                        it.copy(
                            loadingListing = false,
                            storeId = l.storeId,
                            offeringKind = l.offeringKind,
                            category = ChosenCategory(l.categoryId, l.categoryName, emptyList()),
                            title = l.title,
                            description = l.description,
                            price = l.price,
                            discount = l.discountPercentage,
                            condition = l.condition,
                            attributes = l.attributes,
                            features = l.features,
                            existingPhotos = l.photos,
                            options = l.options,
                        )
                    }
                    loadSchema(l.categoryId)
                }
            }
        }
    }

    /** Moves a saved photo one place earlier, at once; put back if apps/api refuses. */
    fun movePhotoEarlier(index: Int) {
        val id = editId ?: return
        val before = _state.value.existingPhotos
        if (index !in 1 until before.size) return
        val after = before.toMutableList().apply { add(index - 1, removeAt(index)) }
        _state.update { it.copy(existingPhotos = after) }
        viewModelScope.launch {
            val r = listings.reorderPhotos(id, after.map { it.mediaId })
            if (r is Outcome.Failure) _state.update { it.copy(existingPhotos = before, error = r.error) }
        }
    }

    /** Removes a saved photo at once, like the web's media manager. */
    fun removeExistingPhoto(photo: ExistingPhoto) {
        val id = editId ?: return
        _state.update { it.copy(existingPhotos = it.existingPhotos - photo) }
        viewModelScope.launch {
            val r = listings.deletePhoto(id, photo.mediaId)
            if (r is Outcome.Failure) _state.update { it.copy(existingPhotos = it.existingPhotos + photo, error = r.error) }
        }
    }

    fun delete() {
        val id = editId ?: return
        _state.update { it.copy(posting = true, error = null) }
        viewModelScope.launch {
            when (val r = listings.delete(id)) {
                is Outcome.Success -> _state.update { it.copy(posting = false, deleted = true) }
                is Outcome.Failure -> _state.update { it.copy(posting = false, error = r.error) }
            }
        }
    }

    fun edit(transform: CreateUiState.() -> CreateUiState) = _state.update { it.transform().copy(error = null) }

    fun addOption() = edit { if (options.size < MAX_OPTIONS) copy(options = options + OptionDraft()) else this }

    fun setOption(index: Int, option: OptionDraft) = edit { copy(options = options.mapIndexed { i, o -> if (i == index) option else o }) }

    fun removeOption(index: Int) = edit { copy(options = options.filterIndexed { i, _ -> i != index }) }

    fun setAttribute(slug: String, value: Any?) = _state.update { s ->
        val next = if (value == null || SchemaRules.isEmptyValue(value)) s.attributes - slug else s.attributes + (slug to value)
        s.copy(attributes = next, fieldErrors = s.fieldErrors - slug)
    }

    fun postAs(storeId: String?) {
        _state.update { s ->
            val next = s.copy(storeId = storeId)
            // Switching to the personal page drops a service choice it cannot make (the web does the same).
            if (storeId == null && s.offeringKind != OfferingKind.GOOD) next.copy(offeringKind = OfferingKind.GOOD) else next
        }
        _state.value.category?.let { loadSchema(it.id) }
        // Each page has its own occupations.
        if (_state.value.editingId == null) loadMyOccupations()
    }

    fun chooseKind(kind: OfferingKind) {
        _state.update { it.copy(offeringKind = kind) }
        _state.value.category?.let { loadSchema(it.id) }
    }

    fun addPhotos(uris: List<String>) = _state.update { s ->
        s.copy(photos = (s.photos + uris.filterNot { it in s.photos }).take(MAX_PHOTOS - s.existingPhotos.size))
    }

    fun removePhoto(uri: String) = _state.update { it.copy(photos = it.photos - uri, failedPhotos = it.failedPhotos - uri) }

    // ---- category picker ----

    fun openPicker() {
        _picker.value = CategoryPickerState(open = true, loading = true)
        viewModelScope.launch {
            val roots = (listings.categoryTree() as? Outcome.Success)?.value.orEmpty()
            _picker.update { it.copy(roots = roots, loading = false) }
        }
    }

    fun closePicker() {
        _picker.value = CategoryPickerState()
    }

    fun searchCategories(query: String) {
        _picker.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) return _picker.update { it.copy(hits = emptyList()) }
        searchJob = viewModelScope.launch {
            delay(250)
            val hits = (listings.searchCategories(query) as? Outcome.Success)?.value.orEmpty()
            _picker.update { it.copy(hits = hits) }
        }
    }

    fun openNode(node: CategoryNode) {
        if (node.children.isEmpty()) {
            choose(ChosenCategory(node.id, node.name, _picker.value.trail.map { it.name }))
        } else {
            _picker.update { it.copy(trail = it.trail + node) }
        }
    }

    fun up() = _picker.update { it.copy(trail = it.trail.dropLast(1)) }

    fun chooseHit(hit: CategoryHit) = choose(ChosenCategory(hit.id, hit.name, hit.path))

    private fun choose(category: ChosenCategory) {
        _picker.value = CategoryPickerState()
        _state.update { it.copy(category = category, attributes = emptyMap(), fieldErrors = emptyMap(), formErrors = it.formErrors - FormError.CATEGORY) }
        loadSchema(category.id)
    }

    private fun loadSchema(categoryId: String) {
        _state.update { it.copy(schemaLoading = true) }
        viewModelScope.launch {
            val s = _state.value
            when (val r = listings.schema(categoryId, s.offeringKind)) {
                is Outcome.Success -> _state.update { cur ->
                    val schema = r.value
                    // The category decides which kinds exist; keep the choice if it still fits.
                    val kind = when {
                        cur.offeringKind in schema.allowedKinds -> cur.offeringKind
                        cur.isPersonal -> OfferingKind.GOOD
                        else -> schema.offeringKind
                    }
                    cur.copy(schema = schema, schemaLoading = false, offeringKind = kind)
                }
                is Outcome.Failure -> _state.update { it.copy(schemaLoading = false, error = r.error) }
            }
        }
    }

    // ---- posting ----

    fun post() {
        val s = _state.value
        if (s.posting) return
        val formErrors = validateForm(s)
        val schemaResult = s.schema?.let { SchemaRules.validate(it.fields, s.attributes, s.ruleContext) }
        val fieldErrors = schemaResult?.errors.orEmpty().associateBy { it.field }
        if (formErrors.isNotEmpty() || fieldErrors.isNotEmpty()) {
            _state.update { it.copy(formErrors = formErrors, fieldErrors = fieldErrors) }
            return
        }
        val draft = ListingDraft(
            storeId = s.storeId,
            offeringKind = s.offeringKind,
            categoryId = s.category!!.id,
            title = s.title,
            description = s.description,
            price = s.price,
            discountPercentage = s.discount,
            condition = s.condition,
            quantity = s.quantity,
            // Only what the rules accept: hidden fields and empty inputs are not sent.
            attributeValues = schemaResult?.values.orEmpty(),
            photos = s.photos,
            options = s.options,
            features = s.features,
            occupationId = s.occupation?.id,
        )
        _state.update { it.copy(posting = true, error = null, formErrors = emptySet(), fieldErrors = emptyMap()) }
        if (editId != null) {
            viewModelScope.launch {
                when (val r = listings.saveEdit(editId, draft.copy(photos = s.photos.filterNot { it in s.uploaded }))) {
                    is Outcome.Failure -> _state.update { it.copy(posting = false, error = r.error) }
                    is Outcome.Success -> _state.update {
                        if (r.value.isEmpty()) it.copy(posting = false, postedId = editId)
                        else it.copy(posting = false, failedPhotos = r.value, uploaded = it.uploaded + (s.photos - r.value.toSet()))
                    }
                }
            }
            return
        }
        viewModelScope.launch {
            when (val r = listings.submit(draft, s.pendingId, s.uploaded)) {
                is Outcome.Failure -> _state.update { it.copy(posting = false, error = r.error) }
                is Outcome.Success -> _state.update {
                    val result = r.value
                    if (result.published) {
                        it.copy(posting = false, postedId = result.productId, stockNotPlaced = result.stockNotPlaced)
                    } else {
                        it.copy(
                            posting = false,
                            pendingId = result.productId,
                            failedPhotos = result.failedPhotos,
                            uploaded = it.uploaded + (draft.photos - result.failedPhotos.toSet()),
                            stockNotPlaced = result.stockNotPlaced,
                        )
                    }
                }
            }
        }
    }

    private fun validateForm(s: CreateUiState): Set<FormError> = buildSet {
        if (s.category == null) add(FormError.CATEGORY)
        if (s.title.isBlank() || s.title.length > 200) add(FormError.TITLE)
        if (s.description.isBlank()) add(FormError.DESCRIPTION)
        val price = number(s.price)
        if (price == null || price <= 0) add(FormError.PRICE)
        if (s.discount.isNotBlank()) {
            val d = number(s.discount)
            if (d == null || d < 0 || d > 100) add(FormError.DISCOUNT)
        }
        if (!s.isPersonal && s.quantity.isNotBlank()) {
            val q = number(s.quantity)
            if (q == null || q < 0 || q > MAX_STORE_QUANTITY || q != Math.floor(q)) add(FormError.QUANTITY)
        }
        // apps/api's PERSONAL_LISTING_GOODS_ONLY, said before the request: a personal page sells a
        // physical item, so a category that only takes services is posted from a store.
        val goodsAllowed = s.schema?.allowedKinds?.let { it.isEmpty() || OfferingKind.GOOD in it } ?: true
        if (s.isPersonal && (s.offeringKind != OfferingKind.GOOD || !goodsAllowed)) add(FormError.GOODS_ONLY)
    }

    private fun number(text: String): Double? =
        SchemaRules.toLatinDigits(text.trim()).replace(Regex("[,٬]"), "").replace('٫', '.').toDoubleOrNull()
}

/** apps/api's MAX_PRODUCT_OPTIONS. */
const val MAX_OPTIONS = 20
