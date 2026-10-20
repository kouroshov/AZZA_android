package com.azza.feature.register

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.data.pos.CodeOwner
import com.azza.core.data.pos.InventoryRepository
import com.azza.core.data.pos.NewItem
import com.azza.core.data.pos.RegisterRepository
import com.azza.core.data.pos.SaleRecorder
import com.azza.core.data.pos.codeOwner
import com.azza.core.model.CodeLookup
import com.azza.core.model.DaySummary
import com.azza.core.model.PaymentMethod
import com.azza.core.model.PosItem
import com.azza.core.model.QueuedSale
import com.azza.core.model.SaleLine
import com.azza.core.model.SchemaRules
import com.azza.core.model.ServerSale
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// ✅ POS-05. The "Sales register" — never a certified cash register. Built
// for speed: the camera runs from the moment the screen opens, a known code
// lands in the cart, and Pay → Cash / Card records the sale on the phone
// (SaleRecorder), to be sent when there is a network. It works offline.

/** A typed amount in major units ("12,500", Persian digits allowed) → minor units; null if not one. */
internal fun parseAmountMinor(text: String): Long? {
    val clean = SchemaRules.toLatinDigits(text).replace(",", "").replace("٬", "").replace(" ", "").trim()
    if (!Regex("""^\d{1,13}(\.\d{1,2})?$""").matches(clean)) return null
    return BigDecimal(clean).movePointRight(2).longValueExact()
}

/** One line of the cart. Kept in the saved state, so it survives rotation and the app being killed. */
@Serializable
data class CartLine(
    val productId: String,
    val name: String,
    val unitPriceMinor: Long,
    val quantity: Int,
    val currency: String = "IRR",
) {
    val totalMinor: Long get() = unitPriceMinor * quantity
}

/** Adds one of [item] — a new line, or one more on the line already there. */
internal fun List<CartLine>.add(productId: String, name: String, unitPriceMinor: Long, currency: String): List<CartLine> {
    val at = indexOfFirst { it.productId == productId }
    if (at < 0) return this + CartLine(productId, name, unitPriceMinor, 1, currency)
    return toMutableList().also { it[at] = it[at].copy(quantity = it[at].quantity + 1) }
}

/** Where the register sells: one branch of one store. */
data class Place(val storeId: String, val storeName: String, val branchId: String, val branchName: String) {
    val label: String get() = if (branchName.isBlank() || branchName == storeName) storeName else "$storeName · $branchName"
}

/** An unknown code, online: a quick item made without leaving the register. */
data class UnknownSheet(
    val code: String,
    val symbology: String,
    val name: String = "",
    val price: String = "",
    val saving: Boolean = false,
    val invalid: Boolean = false,
    val conflict: CodeOwner? = null,
    val error: AppError? = null,
)

sealed interface RegisterNotice {
    data class Added(val name: String, val quantity: Int) : RegisterNotice
    data object PostLink : RegisterNotice
    data object InvalidCode : RegisterNotice
    data class UnknownOffline(val code: String) : RegisterNotice
    data class Sold(val totalMinor: Long, val currency: String) : RegisterNotice
}

data class RegisterState(
    val place: Place? = null,
    val places: List<Place> = emptyList(),
    /** Signed in without a store: the register has nothing to sell. */
    val noStore: Boolean = false,
    val cart: List<CartLine> = emptyList(),
    val unknown: UnknownSheet? = null,
    val notice: RegisterNotice? = null,
    val paying: Boolean = false,
    val online: Boolean = true,
    val pending: List<QueuedSale> = emptyList(),
    val rejected: List<QueuedSale> = emptyList(),
) {
    val totalMinor: Long get() = cart.sumOf { it.totalMinor }
    val currency: String get() = cart.firstOrNull()?.currency ?: "IRR"
    val itemCount: Int get() = cart.sumOf { it.quantity }

    /** The scanner reports only while nothing else is open. */
    val scanning: Boolean get() = place != null && unknown == null && !paying
}

private val cartJson = Json { ignoreUnknownKeys = true }
private val cartSerializer = ListSerializer(CartLine.serializer())

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val inventory: InventoryRepository,
    private val register: RegisterRepository,
    private val sales: SaleRecorder,
) : ViewModel() {
    private val local = MutableStateFlow(RegisterState())

    private val cart = savedState.getStateFlow(CART, "[]").map { raw ->
        runCatching { cartJson.decodeFromString(cartSerializer, raw) }.getOrDefault(emptyList())
    }

    val state: StateFlow<RegisterState> = combine(local, cart, inventory.online, sales.pending, sales.rejected) { s, lines, online, pending, rejected ->
        s.copy(cart = lines, online = online, pending = pending, rejected = rejected)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RegisterState())

    init {
        viewModelScope.launch { loadPlaces() }
    }

    private fun currentCart(): List<CartLine> =
        runCatching { cartJson.decodeFromString(cartSerializer, savedState.get<String>(CART) ?: "[]") }.getOrDefault(emptyList())

    private fun setCart(lines: List<CartLine>) {
        savedState[CART] = cartJson.encodeToString(cartSerializer, lines)
    }

    /** Every branch of every store the person runs; the register opens where it was last used. */
    private suspend fun loadPlaces() {
        val stores = register.stores.first()
        if (stores.isEmpty()) return local.update { it.copy(noStore = true) }
        // First the main branches (known offline), so the register opens at once.
        val quick = stores.map { Place(it.id, it.name, it.mainBranchId, "") }
        val last = register.lastPlace.firstOrNull()
        select(quick.firstOrNull { it.storeId == last?.first && it.branchId == last.second } ?: quick.firstOrNull { it.storeId == last?.first } ?: quick.first(), remember = false)
        local.update { it.copy(places = quick) }
        val all = stores.flatMap { st ->
            inventory.branches(st.id).map { b -> Place(st.id, st.name, b.id, b.name) }.ifEmpty { listOf(Place(st.id, st.name, st.mainBranchId, "")) }
        }
        local.update { s ->
            val current = s.place
            val withName = current?.let { c -> all.firstOrNull { it.storeId == c.storeId && it.branchId == c.branchId } } ?: current
            s.copy(places = all, place = withName)
        }
        if (last != null && last != local.value.place?.let { it.storeId to it.branchId }) {
            all.firstOrNull { it.storeId == last.first && it.branchId == last.second }?.let { select(it, remember = false) }
        }
    }

    fun selectPlace(place: Place) = select(place, remember = true)

    private fun select(place: Place, remember: Boolean) {
        val previous = local.value.place
        // The cart's items belong to a store: another store starts an empty cart.
        if (previous != null && previous.storeId != place.storeId) setCart(emptyList())
        local.update { it.copy(place = place, unknown = null, notice = null) }
        viewModelScope.launch {
            if (remember) register.rememberPlace(place.storeId, place.branchId)
            // Brings the local catalog up to date; offline the copy on the phone serves.
            inventory.open(place.storeId, place.branchId)
        }
    }

    // ---------------------------------------------------------------- scanning

    fun onScanned(raw: String, symbology: String?) {
        val place = local.value.place ?: return
        if (!state.value.scanning) return
        viewModelScope.launch {
            when (val found = inventory.lookup(place.storeId, raw, place.branchId)) {
                is CodeLookup.Product -> addItem(found.item)
                is CodeLookup.UnknownCode ->
                    if (state.value.online) {
                        local.update { it.copy(unknown = UnknownSheet(found.code, found.symbology), notice = null) }
                    } else {
                        // The rest of the sale goes on; this item is added when back online.
                        local.update { it.copy(notice = RegisterNotice.UnknownOffline(found.code)) }
                    }
                is CodeLookup.PostLink -> local.update { it.copy(notice = RegisterNotice.PostLink) }
                is CodeLookup.Invalid -> local.update { it.copy(notice = RegisterNotice.InvalidCode) }
            }
        }
    }

    private fun addItem(item: PosItem) {
        val next = currentCart().add(item.id, item.name, item.salePriceMinor, item.currency)
        setCart(next)
        local.update { it.copy(notice = RegisterNotice.Added(item.name, next.first { l -> l.productId == item.id }.quantity)) }
    }

    fun editUnknown(change: UnknownSheet.() -> UnknownSheet) =
        local.update { s -> s.copy(unknown = s.unknown?.change()?.copy(invalid = false, error = null, conflict = null)) }

    fun dismissUnknown() = local.update { it.copy(unknown = null) }

    /** Makes a PRIVATE item with the scanned code and puts it in the cart, without leaving the register. */
    fun saveUnknown() {
        val sheet = local.value.unknown ?: return
        val place = local.value.place ?: return
        if (sheet.saving) return
        val price = parseAmountMinor(sheet.price)
        if (sheet.name.isBlank() || price == null) return local.update { it.copy(unknown = sheet.copy(invalid = true)) }
        if (!state.value.online) return local.update { it.copy(unknown = sheet.copy(error = AppError.Network)) }
        local.update { it.copy(unknown = sheet.copy(saving = true)) }
        viewModelScope.launch {
            when (val r = inventory.create(place.storeId, place.branchId, NewItem(name = sheet.name, priceMinor = price, code = sheet.code))) {
                is Outcome.Success -> {
                    // The catalog was refreshed by the create; fall back to what was typed.
                    val item = inventory.item(r.value, place.branchId).firstOrNull()
                    val next = currentCart().add(r.value, item?.name ?: sheet.name.trim(), item?.salePriceMinor ?: price, item?.currency ?: "IRR")
                    setCart(next)
                    local.update { it.copy(unknown = null, notice = RegisterNotice.Added(sheet.name.trim(), 1)) }
                }
                is Outcome.Failure -> local.update {
                    it.copy(unknown = sheet.copy(saving = false, conflict = r.error.codeOwner(), error = r.error.takeIf { e -> e.codeOwner() == null }))
                }
            }
        }
    }

    // ---------------------------------------------------------------- cart

    fun increase(productId: String) = setCart(currentCart().map { if (it.productId == productId) it.copy(quantity = it.quantity + 1) else it })

    fun decrease(productId: String) =
        setCart(currentCart().mapNotNull { if (it.productId != productId) it else it.copy(quantity = it.quantity - 1).takeIf { l -> l.quantity > 0 } })

    fun remove(productId: String) = setCart(currentCart().filterNot { it.productId == productId })

    fun clearCart() = setCart(emptyList())

    fun dismissNotice() = local.update { it.copy(notice = null) }

    // ---------------------------------------------------------------- paying

    fun startPay() {
        if (currentCart().isNotEmpty()) local.update { it.copy(paying = true) }
    }

    fun cancelPay() = local.update { it.copy(paying = false) }

    /** One tap: the sale is recorded on the phone and the register is ready for the next customer. */
    fun pay(method: PaymentMethod) {
        val place = local.value.place ?: return
        val lines = currentCart().takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            sales.recordSale(place.storeId, place.branchId, method, lines.map { SaleLine(it.productId, it.name, it.quantity, it.unitPriceMinor) })
            setCart(emptyList())
            local.update { it.copy(paying = false, notice = RegisterNotice.Sold(lines.sumOf { l -> l.totalMinor }, lines.first().currency)) }
        }
    }

    fun dismissRejected(id: String) = viewModelScope.launch { sales.dismissRejected(id) }

    private companion object {
        const val CART = "register_cart"
    }
}

// ---------------------------------------------------------------- today

data class TodayState(
    val loading: Boolean = true,
    val online: Boolean = true,
    /** The server's day; null offline or before it arrives. */
    val summary: DaySummary? = null,
    val sales: List<ServerSale> = emptyList(),
    val error: AppError? = null,
    /** This phone's sales from today still waiting to be sent. */
    val waiting: List<QueuedSale> = emptyList(),
    val rejected: List<QueuedSale> = emptyList(),
    val confirmVoid: ServerSale? = null,
    val voiding: Boolean = false,
)

@HiltViewModel
class TodayViewModel internal constructor(
    val storeId: String,
    val branchId: String,
    private val register: RegisterRepository,
    sales: SaleRecorder,
    online: StateFlow<Boolean>,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    @Inject constructor(savedState: SavedStateHandle, register: RegisterRepository, sales: SaleRecorder, inventory: InventoryRepository) :
        this(savedState.toRoute<RegisterTodayRoute>(), register, sales, inventory.online)

    private constructor(route: RegisterTodayRoute, register: RegisterRepository, sales: SaleRecorder, online: StateFlow<Boolean>) :
        this(route.storeId, route.branchId, register, sales, online)

    private val local = MutableStateFlow(TodayState())

    val state: StateFlow<TodayState> = combine(local, online, sales.pending, sales.rejected) { s, isOnline, pending, rejected ->
        val start = startOfToday()
        s.copy(
            online = isOnline,
            waiting = pending.filter { it.branchId == branchId && it.soldAtMillis >= start },
            rejected = rejected.filter { it.branchId == branchId },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TodayState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            local.update { it.copy(loading = true, error = null) }
            val summary = register.daySummary(storeId, branchId)
            val list = register.todaysSales(storeId, branchId)
            local.update {
                it.copy(
                    loading = false,
                    summary = (summary as? Outcome.Success)?.value,
                    sales = (list as? Outcome.Success)?.value.orEmpty(),
                    error = (summary as? Outcome.Failure)?.error ?: (list as? Outcome.Failure)?.error,
                )
            }
        }
    }

    fun askVoid(sale: ServerSale?) = local.update { it.copy(confirmVoid = sale) }

    fun void() {
        val sale = local.value.confirmVoid ?: return
        local.update { it.copy(voiding = true) }
        viewModelScope.launch {
            val r = register.voidSale(storeId, sale.id)
            local.update { it.copy(voiding = false, confirmVoid = null, error = (r as? Outcome.Failure)?.error) }
            if (r is Outcome.Success) refresh()
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran")).apply {
        timeInMillis = now()
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
