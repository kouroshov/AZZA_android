package com.azza.core.data

import android.net.Uri
import com.azza.core.common.AppError
import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.model.Page
import com.azza.core.network.api.AddressDto
import com.azza.core.network.api.AddressRequest
import com.azza.core.network.api.AppealDto
import com.azza.core.network.api.AppealsApi
import com.azza.core.network.api.CreateAppealRequest
import com.azza.core.network.api.EmailChangeRequest
import com.azza.core.network.api.FollowListsApi
import com.azza.core.network.api.ProfileApi
import com.azza.core.network.api.RenameUsernameRequest
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** A saved delivery address (apps/api's user_addresses). */
data class Address(
    val id: String,
    val label: String?,
    val recipientName: String,
    val phone: String?,
    val line1: String,
    val line2: String?,
    val city: String,
    val region: String?,
    val postalCode: String?,
    val countryCode: String,
    val isDefault: Boolean,
) {
    /** One line for a list or for checkout's address box. */
    val oneLine: String get() = listOfNotNull(line1, line2?.takeIf { it.isNotBlank() }, city, region?.takeIf { it.isNotBlank() }).joinToString(", ")
}

/** What the address form edits; [id] null = a new one. */
data class AddressDraft(
    val id: String? = null,
    val label: String = "",
    val recipientName: String = "",
    val phone: String = "",
    val line1: String = "",
    val line2: String = "",
    val city: String = "",
    val region: String = "",
    val postalCode: String = "",
    val isDefault: Boolean = false,
) {
    val complete: Boolean get() = recipientName.isNotBlank() && line1.isNotBlank() && city.isNotBlank()
}

/** apps/api's availability answer for a typed username. */
enum class UsernameCheck { AVAILABLE, TAKEN, INVALID, RESERVED, UNCHANGED, UNKNOWN }

data class PendingEmailChange(val newEmail: String, val expiresAt: String)

/**
 * The account fields the profile form does not cover (phase 5a): photo,
 * username, email address, delivery addresses. Every change that alters
 * what the app shows about the person re-reads `users/me`.
 */
@Singleton
class MyAccountRepository @Inject constructor(
    private val api: ProfileApi,
    private val users: UserRepository,
    private val photos: PhotoCompressor,
    private val json: Json,
) {
    /** The photo is shrunk like a listing photo (2048 px JPEG, EXIF-rotated) before upload. */
    suspend fun uploadAvatar(uri: String): Outcome<Unit> {
        val bytes = withContext(Dispatchers.IO) { photos.toJpeg(Uri.parse(uri)) }
            ?: return Outcome.Failure(AppError.Unexpected(IllegalArgumentException("unreadable photo")))
        val part = MultipartBody.Part.createFormData("file", "avatar.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        return safeApiCall(json) { api.uploadAvatar(part) }.map { }.also { if (it is Outcome.Success) users.me() }
    }

    suspend fun removeAvatar(): Outcome<Unit> =
        safeApiCall(json) { api.removeAvatar() }.map { }.also { if (it is Outcome.Success) users.me() }

    suspend fun checkUsername(name: String): Outcome<UsernameCheck> =
        safeApiCall(json) { api.usernameAvailability(name).data }.map { answer ->
            when (answer.reason) {
                "AVAILABLE" -> UsernameCheck.AVAILABLE
                "TAKEN" -> UsernameCheck.TAKEN
                "INVALID_FORMAT", "TOO_SHORT", "TOO_LONG" -> UsernameCheck.INVALID
                "RESERVED" -> UsernameCheck.RESERVED
                "UNCHANGED", "SAME" -> UsernameCheck.UNCHANGED
                else -> if (answer.available) UsernameCheck.AVAILABLE else UsernameCheck.UNKNOWN
            }
        }

    suspend fun renameUsername(name: String): Outcome<Unit> =
        safeApiCall(json) { api.renameUsername(RenameUsernameRequest(name.trim())).data }.map { }.also { if (it is Outcome.Success) users.me() }

    suspend fun pendingEmailChange(): Outcome<PendingEmailChange?> =
        safeApiCall(json) { api.pendingEmailChange().data }.map { it.pending?.let { p -> PendingEmailChange(p.newEmail, p.expiresAt) } }

    suspend fun requestEmailChange(newEmail: String): Outcome<Unit> =
        safeApiCall(json) { api.requestEmailChange(EmailChangeRequest(newEmail.trim())) }.map { }

    suspend fun cancelEmailChange(): Outcome<Unit> = safeApiCall(json) { api.cancelEmailChange() }.map { }

    suspend fun addresses(): Outcome<List<Address>> =
        safeApiCall(json) { api.addresses().data }.map { list -> list.map { it.toModel() }.sortedByDescending { it.isDefault } }

    suspend fun saveAddress(draft: AddressDraft): Outcome<Unit> {
        // An optional field left empty: on a new address it is simply not
        // sent; on an edit it is sent as "" — the app's Json drops nulls, so a
        // null could never clear what was there before.
        val editing = draft.id != null
        fun optional(value: String) = value.trim().ifEmpty { if (editing) "" else null }
        val body = AddressRequest(
            label = optional(draft.label),
            recipientName = draft.recipientName.trim(),
            phoneE164 = optional(draft.phone),
            line1 = draft.line1.trim(),
            line2 = optional(draft.line2),
            city = draft.city.trim(),
            region = optional(draft.region),
            postalCode = optional(draft.postalCode),
            // Iran only for now, like the web's address form.
            countryCode = "IR",
            isDefault = draft.isDefault,
        )
        return safeApiCall(json) { if (draft.id == null) api.addAddress(body) else api.updateAddress(draft.id, body) }.map { }
    }

    suspend fun deleteAddress(id: String): Outcome<Unit> = safeApiCall(json) { api.deleteAddress(id) }

    private fun AddressDto.toModel() = Address(id, label, recipientName, phoneE164, line1, line2, city, region, postalCode, countryCode, isDefault)
}

fun Address.toDraft() = AddressDraft(
    id = id,
    label = label.orEmpty(),
    recipientName = recipientName,
    phone = phone.orEmpty(),
    line1 = line1,
    line2 = line2.orEmpty(),
    city = city,
    region = region.orEmpty(),
    postalCode = postalCode.orEmpty(),
    isDefault = isDefault,
)

/** A row in "Followers" / "Following": a person (has [username]) or a store (has [slug]). */
data class FollowEntry(val id: String, val name: String, val username: String?, val slug: String?) {
    val isStore: Boolean get() = slug != null && username == null
}

/** The signed-in person's own lists; apps/api keeps everyone else's private. */
@Singleton
class FollowListsRepository @Inject constructor(private val api: FollowListsApi, private val json: Json) {
    suspend fun followers(cursor: String?): Outcome<Page<FollowEntry>> = safeApiCall(json) { api.followers(cursor).data }.map { p ->
        Page(p.items.map { FollowEntry(it.id, it.displayName.ifBlank { it.username }, it.username, null) }, p.nextCursor, p.hasMore)
    }

    suspend fun following(cursor: String?): Outcome<Page<FollowEntry>> = safeApiCall(json) { api.following(cursor).data }.map { p ->
        Page(p.items.map { FollowEntry(it.id, it.name, it.username.takeIf { _ -> it.kind == "user" }, it.slug.takeIf { _ -> it.kind == "store" }) }, p.nextCursor, p.hasMore)
    }
}

data class Appeal(val id: String, val holdAt: String, val message: String, val status: String, val createdAt: String, val decidedAt: String?, val decisionNote: String?)

/** One appeal per decision (apps/api's unique index); the decision note is written for the appellant. */
@Singleton
class AppealsRepository @Inject constructor(private val api: AppealsApi, private val json: Json) {
    suspend fun forProduct(productId: String): Outcome<List<Appeal>> =
        safeApiCall(json) { api.mine(productId = productId).data }.map { list -> list.map { it.toModel() } }

    suspend fun appealProduct(productId: String, message: String): Outcome<Appeal> =
        safeApiCall(json) { api.create(CreateAppealRequest("PRODUCT", productId, message.trim())).data }.map { it.toModel() }

    private fun AppealDto.toModel() = Appeal(id, holdAt, message, status, createdAt, decidedAt, decisionNote)
}
