package com.azza.feature.register

import com.azza.core.common.Outcome
import com.azza.core.data.pos.InventoryRepository
import com.azza.core.data.pos.NewItem
import com.azza.core.data.pos.PosBranch
import com.azza.core.model.CodeLookup
import com.azza.core.model.PosItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The inventory as the view models see it, in memory. */
class FakeInventoryRepository : InventoryRepository {
    override val online = MutableStateFlow(true)
    val all = MutableStateFlow<List<PosItem>>(emptyList())
    val created = mutableListOf<NewItem>()
    val calls = mutableListOf<String>()

    /** Codes this "store" already has: code → item. */
    val known = mutableMapOf<String, PosItem>()

    /** What the next change answers; success by default. */
    var nextResult: Outcome<Unit> = Outcome.Success(Unit)
    var createResult: Outcome<String>? = null
    var branchList = listOf(PosBranch("b1", "Main"), PosBranch("b2", "Bazaar"))
    var remembered: String? = null

    override fun items(storeId: String, branchId: String): Flow<List<PosItem>> = all
    override fun item(productId: String, branchId: String): Flow<PosItem?> = all.map { list -> list.firstOrNull { it.id == productId } }
    override suspend fun branches(storeId: String) = branchList
    override fun lastBranch(storeId: String): Flow<String?> = MutableStateFlow(remembered)
    override suspend fun rememberBranch(storeId: String, branchId: String) {
        remembered = branchId
    }

    override suspend fun open(storeId: String, branchId: String): Outcome<Unit> {
        calls += "open:$branchId"
        return Outcome.Success(Unit)
    }

    override suspend fun lookup(storeId: String, raw: String, branchId: String): CodeLookup {
        known[raw]?.let { return CodeLookup.Product(it) }
        return when {
            raw.startsWith("https://") -> CodeLookup.PostLink(raw, null)
            raw == "4006381333932" -> CodeLookup.Invalid("BAD_CHECK_DIGIT")
            raw == "036000291452" -> CodeLookup.UnknownCode("0036000291452", "EAN13")
            else -> CodeLookup.UnknownCode(raw, "CODE128")
        }
    }

    override suspend fun create(storeId: String, branchId: String, item: NewItem): Outcome<String> {
        created += item
        return createResult ?: Outcome.Success("new-${created.size}")
    }

    private fun change(name: String): Outcome<Unit> {
        calls += name
        return nextResult
    }

    override suspend fun rename(storeId: String, branchId: String, productId: String, name: String, priceMinor: Long) = change("rename:$name:$priceMinor")
    override suspend fun setPhoto(storeId: String, branchId: String, productId: String, uri: String) = change("photo")
    override suspend fun attachCode(storeId: String, branchId: String, productId: String, code: String, symbology: String?) = change("attach:$code")
    override suspend fun generateCode(storeId: String, branchId: String, productId: String) = change("generate")
    override suspend fun removeCode(storeId: String, branchId: String, productId: String, codeId: String) = change("remove:$codeId")
    override suspend fun transfer(storeId: String, branchId: String, productId: String, fromStore: Boolean, qty: Int) = change("transfer:$fromStore:$qty")
    override suspend fun setPublic(storeId: String, branchId: String, productId: String, public: Boolean) = change("public:$public")
}
