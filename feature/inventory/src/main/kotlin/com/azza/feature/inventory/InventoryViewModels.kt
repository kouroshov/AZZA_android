package com.azza.feature.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.common.ProductCodes
import com.azza.core.data.pos.CodeOwner
import com.azza.core.data.pos.InventoryRepository
import com.azza.core.data.pos.NewItem
import com.azza.core.data.pos.PosBranch
import com.azza.core.data.pos.codeOwner
import com.azza.core.data.pos.missingForPublic
import com.azza.core.model.CodeLookup
import com.azza.core.model.PosItem
import com.azza.core.model.SchemaRules
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ✅ POS-04. The store's private inventory. Items are "items", never "posts".
// Reads are the register's local catalog (works offline); every change
// goes to the server through InventoryRepository and needs a network.

/** A typed price in major units ("12,500", Persian digits allowed) → minor units; null if not a price. */
internal fun parsePriceMinor(text: String): Long? {
    val clean = SchemaRules.toLatinDigits(text).replace(",", "").replace("٬", "").replace(" ", "").trim()
    if (!Regex("""^\d{1,13}(\.\d{1,2})?$""").matches(clean)) return null
    return BigDecimal(clean).movePointRight(2).longValueExact()
}

internal fun parseQty(text: String): Int? {
    val clean = SchemaRules.toLatinDigits(text).trim()
    if (clean.isEmpty()) return null
    return clean.toIntOrNull()?.takeIf { it in 0..1_000_000 }
}

enum class InventoryFilter { PRIVATE, PUBLIC, NEEDS_REVIEW, OUT_OF_STOCK }

data class InventoryListState(
    val branches: List<PosBranch> = emptyList(),
    val branch: PosBranch? = null,
    val query: String = "",
    val filter: InventoryFilter? = null,
    val items: List<PosItem> = emptyList(),
    val loading: Boolean = true,
    /** The last catalog sync failed for a reason other than being offline. */
    val syncError: AppError? = null,
    val online: Boolean = true,
)

internal fun List<PosItem>.matching(query: String, filter: InventoryFilter?): List<PosItem> {
    val q = query.trim()
    val code = (ProductCodes.normalize(q) as? ProductCodes.Normalized.Ok)?.code
    return filter { item ->
        val byFilter = when (filter) {
            null -> true
            InventoryFilter.PRIVATE -> item.visibility == "PRIVATE"
            InventoryFilter.PUBLIC -> item.visibility == "PUBLIC"
            InventoryFilter.NEEDS_REVIEW -> item.stock?.needsReview == true
            InventoryFilter.OUT_OF_STOCK -> (item.stock?.storeQty ?: 0) <= 0 && (item.stock?.onlineQty ?: 0) <= 0
        }
        val byQuery = q.isEmpty() ||
            item.name.contains(q, ignoreCase = true) ||
            item.codes.any { it.code == q || it.code == code }
        byFilter && byQuery
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventoryListViewModel internal constructor(
    val storeId: String,
    private val repo: InventoryRepository,
) : ViewModel() {
    @Inject constructor(savedState: SavedStateHandle, repo: InventoryRepository) : this(savedState.toRoute<InventoryRoute>().storeId, repo)

    private val branches = MutableStateFlow<List<PosBranch>>(emptyList())
    private val branch = MutableStateFlow<PosBranch?>(null)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<InventoryFilter?>(null)
    private val loading = MutableStateFlow(true)
    private val syncError = MutableStateFlow<AppError?>(null)

    private val items: Flow<List<PosItem>> = branch.flatMapLatest { b -> if (b == null) flowOf(emptyList()) else repo.items(storeId, b.id) }

    val state: StateFlow<InventoryListState> = combine(
        combine(branches, branch, query, filter) { bs, b, q, f -> InventoryListState(branches = bs, branch = b, query = q, filter = f) },
        items,
        loading,
        syncError,
        repo.online,
    ) { base, all, isLoading, error, online ->
        base.copy(items = all.matching(base.query, base.filter), loading = isLoading && all.isEmpty(), syncError = error, online = online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryListState())

    init {
        viewModelScope.launch {
            val list = repo.branches(storeId)
            branches.value = list
            val remembered = repo.lastBranch(storeId).first()
            select(list.firstOrNull { it.id == remembered } ?: list.firstOrNull())
        }
    }

    fun selectBranch(id: String) = select(branches.value.firstOrNull { it.id == id })

    private fun select(next: PosBranch?) {
        branch.value = next ?: run { loading.value = false; return }
        viewModelScope.launch {
            repo.rememberBranch(storeId, next.id)
            refresh()
        }
    }

    /** Brings this branch's catalog up to date; offline it just shows what the phone has. */
    fun refresh() {
        val b = branch.value ?: return
        viewModelScope.launch {
            loading.value = true
            val r = repo.open(storeId, b.id)
            syncError.value = (r as? Outcome.Failure)?.error?.takeUnless { it is AppError.Network }
            loading.value = false
        }
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun setFilter(next: InventoryFilter?) {
        filter.value = if (filter.value == next) null else next
    }
}

// ---------------------------------------------------------------- scan to create

/** The "new item" sheet. [code] null = "no barcode" (the server makes an internal one). */
data class CreateSheet(
    val code: String?,
    val symbology: String?,
    val name: String = "",
    val price: String = "",
    val storeQty: String = "",
    val onlineQty: String = "",
    val photoUri: String? = null,
    val saving: Boolean = false,
    val nameMissing: Boolean = false,
    val priceInvalid: Boolean = false,
    val qtyInvalid: Boolean = false,
    /** Another item of this store already has [code]. */
    val conflict: CodeOwner? = null,
    val error: AppError? = null,
)

sealed interface ScanNotice {
    data class Saved(val name: String) : ScanNotice
    data object PostLink : ScanNotice
    data class InvalidCode(val reason: String) : ScanNotice
}

data class ScanCreateState(
    val sheet: CreateSheet? = null,
    val notice: ScanNotice? = null,
    val online: Boolean = true,
)

sealed interface ScanEvent {
    data class OpenItem(val productId: String) : ScanEvent
}

/**
 * Scan on the inventory: a known code opens its item; an unknown one opens
 * the "new item" sheet with the code filled in; saving makes a PRIVATE item
 * and goes straight back to scanning, for the next product.
 */
@HiltViewModel
class ScanCreateViewModel internal constructor(
    val storeId: String,
    val branchId: String,
    private val repo: InventoryRepository,
) : ViewModel() {
    @Inject constructor(savedState: SavedStateHandle, repo: InventoryRepository) : this(savedState.toRoute<InventoryScanRoute>(), repo)

    private constructor(route: InventoryScanRoute, repo: InventoryRepository) : this(route.storeId, route.branchId, repo)

    private val _state = MutableStateFlow(ScanCreateState())
    val state: StateFlow<ScanCreateState> = combine(_state, repo.online) { s, online -> s.copy(online = online) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanCreateState())

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The scanner pauses while the sheet is open. */
    fun onScanned(raw: String, symbology: String?) {
        if (_state.value.sheet != null) return
        viewModelScope.launch {
            when (val found = repo.lookup(storeId, raw, branchId)) {
                is CodeLookup.Product -> _events.send(ScanEvent.OpenItem(found.item.id))
                is CodeLookup.UnknownCode -> _state.update { it.copy(sheet = CreateSheet(found.code, found.symbology), notice = null) }
                is CodeLookup.PostLink -> _state.update { it.copy(notice = ScanNotice.PostLink) }
                is CodeLookup.Invalid -> _state.update { it.copy(notice = ScanNotice.InvalidCode(found.reason)) }
            }
        }
    }

    /** "No barcode": a new item that gets an internal, printable code. */
    fun startWithoutCode() = _state.update { it.copy(sheet = CreateSheet(code = null, symbology = null), notice = null) }

    fun edit(change: CreateSheet.() -> CreateSheet) = _state.update { s ->
        s.copy(sheet = s.sheet?.change()?.copy(nameMissing = false, priceInvalid = false, qtyInvalid = false, error = null))
    }

    fun dismissSheet() = _state.update { it.copy(sheet = null) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun save() {
        val sheet = _state.value.sheet ?: return
        if (sheet.saving) return
        val price = parsePriceMinor(sheet.price)
        val storeQty = parseQty(sheet.storeQty)
        val onlineQty = parseQty(sheet.onlineQty)
        val qtyBad = (sheet.storeQty.isNotBlank() && storeQty == null) || (sheet.onlineQty.isNotBlank() && onlineQty == null)
        if (sheet.name.isBlank() || price == null || qtyBad) {
            return _state.update { it.copy(sheet = sheet.copy(nameMissing = sheet.name.isBlank(), priceInvalid = price == null, qtyInvalid = qtyBad)) }
        }
        if (!state.value.online) return _state.update { it.copy(sheet = sheet.copy(error = AppError.Network)) }
        _state.update { it.copy(sheet = sheet.copy(saving = true)) }
        viewModelScope.launch {
            val result = repo.create(
                storeId,
                branchId,
                NewItem(
                    name = sheet.name,
                    priceMinor = price,
                    storeQty = storeQty,
                    onlineQty = onlineQty,
                    code = sheet.code,
                    generateCode = sheet.code == null,
                    photoUri = sheet.photoUri,
                ),
            )
            when (result) {
                is Outcome.Success -> _state.update { it.copy(sheet = null, notice = ScanNotice.Saved(sheet.name.trim())) }
                is Outcome.Failure -> _state.update {
                    it.copy(sheet = sheet.copy(saving = false, conflict = result.error.codeOwner(), error = result.error.takeIf { e -> e.codeOwner() == null }))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- item detail

data class ItemEdit(val name: String, val price: String)

sealed interface ItemDialog {
    data object AddCode : ItemDialog
    data class Transfer(val fromStore: Boolean, val qty: String = "", val invalid: Boolean = false) : ItemDialog
    data class ShowCode(val codeId: String) : ItemDialog
    data class RemoveCode(val codeId: String) : ItemDialog
}

data class ItemDetailState(
    val item: PosItem? = null,
    val loaded: Boolean = false,
    val edit: ItemEdit? = null,
    val editInvalid: Boolean = false,
    val busy: Boolean = false,
    val dialog: ItemDialog? = null,
    val error: AppError? = null,
    val conflict: CodeOwner? = null,
    /** Making it public was refused: what it lacks (title, description, categoryId, price). */
    val missing: List<String>? = null,
    val online: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemDetailViewModel internal constructor(
    val storeId: String,
    val branchId: String,
    val productId: String,
    private val repo: InventoryRepository,
) : ViewModel() {
    @Inject constructor(savedState: SavedStateHandle, repo: InventoryRepository) : this(savedState.toRoute<InventoryItemRoute>(), repo)

    private constructor(route: InventoryItemRoute, repo: InventoryRepository) : this(route.storeId, route.branchId, route.productId, repo)

    private val local = MutableStateFlow(ItemDetailState())

    val state: StateFlow<ItemDetailState> = combine(local, repo.item(productId, branchId), repo.online) { s, item, online ->
        s.copy(item = item, loaded = true, online = online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemDetailState())

    fun startEdit() {
        val item = state.value.item ?: return
        local.update { it.copy(edit = ItemEdit(item.name, formatMajor(item.priceMinor)), editInvalid = false) }
    }

    fun editName(text: String) = local.update { it.copy(edit = it.edit?.copy(name = text), editInvalid = false) }
    fun editPrice(text: String) = local.update { it.copy(edit = it.edit?.copy(price = text), editInvalid = false) }
    fun cancelEdit() = local.update { it.copy(edit = null) }

    fun saveEdit() {
        val edit = local.value.edit ?: return
        val price = parsePriceMinor(edit.price)
        if (edit.name.isBlank() || price == null) return local.update { it.copy(editInvalid = true) }
        run({ repo.rename(storeId, branchId, productId, edit.name, price) }) { local.update { it.copy(edit = null) } }
    }

    fun setPhoto(uri: String) = run({ repo.setPhoto(storeId, branchId, productId, uri) })

    fun openDialog(dialog: ItemDialog?) = local.update { it.copy(dialog = dialog, conflict = null) }

    fun addCode(raw: String, symbology: String?) {
        val normalized = ProductCodes.normalize(raw, symbology?.let { runCatching { ProductCodes.Symbology.valueOf(it) }.getOrNull() })
        if (normalized is ProductCodes.Normalized.Bad) {
            return local.update { it.copy(error = AppError.Api(400, "CODE_INVALID", normalized.reason.name, null)) }
        }
        normalized as ProductCodes.Normalized.Ok
        run({ repo.attachCode(storeId, branchId, productId, normalized.code, normalized.symbology.name) }) {
            local.update { it.copy(dialog = null) }
        }
    }

    fun generateCode() = run({ repo.generateCode(storeId, branchId, productId) })

    fun removeCode(codeId: String) = run({ repo.removeCode(storeId, branchId, productId, codeId) }) { local.update { it.copy(dialog = null) } }

    fun editTransfer(qty: String) = local.update { s -> s.copy(dialog = (s.dialog as? ItemDialog.Transfer)?.copy(qty = qty, invalid = false) ?: s.dialog) }

    fun transfer() {
        val dialog = local.value.dialog as? ItemDialog.Transfer ?: return
        val qty = parseQty(dialog.qty)?.takeIf { it > 0 } ?: return local.update { it.copy(dialog = dialog.copy(invalid = true)) }
        run({ repo.transfer(storeId, branchId, productId, dialog.fromStore, qty) }) { local.update { it.copy(dialog = null) } }
    }

    fun setPublic(public: Boolean) {
        local.update { it.copy(missing = null) }
        run({ repo.setPublic(storeId, branchId, productId, public) })
    }

    fun dismissMessages() = local.update { it.copy(error = null, conflict = null, missing = null) }

    /** One change at a time; offline it is refused before it starts. */
    private fun run(call: suspend () -> Outcome<Unit>, onSuccess: () -> Unit = {}) {
        if (local.value.busy) return
        if (!state.value.online) return local.update { it.copy(error = AppError.Network) }
        local.update { it.copy(busy = true, error = null, conflict = null) }
        viewModelScope.launch {
            when (val r = call()) {
                is Outcome.Success -> {
                    local.update { it.copy(busy = false) }
                    onSuccess()
                }
                is Outcome.Failure -> {
                    val owner = r.error.codeOwner()
                    val missing = r.error.missingForPublic()
                    local.update {
                        it.copy(
                            busy = false,
                            conflict = owner,
                            missing = missing,
                            error = r.error.takeIf { owner == null && missing == null },
                        )
                    }
                }
            }
        }
    }
}

/** Minor units as the major-unit text the price field shows ("12500", "12.5"). */
internal fun formatMajor(minor: Long): String =
    BigDecimal(minor).movePointLeft(2).stripTrailingZeros().toPlainString()
