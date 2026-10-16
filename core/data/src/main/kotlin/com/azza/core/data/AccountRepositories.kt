package com.azza.core.data

import com.azza.core.network.api.TokenRequest
import com.azza.core.network.api.ResetPasswordRequest
import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.datastore.AppPreferences
import com.azza.core.model.Page
import com.azza.core.model.User
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.api.AccountApi
import com.azza.core.network.api.CreateReportRequest
import com.azza.core.network.api.CreateTicketRequest
import com.azza.core.network.api.ForgotPasswordRequest
import com.azza.core.network.api.ReportsApi
import com.azza.core.network.api.SupportApi
import com.azza.core.network.api.TicketDetailDto
import com.azza.core.network.api.TicketMessageRequest
import com.azza.core.network.api.UpdateProfileRequest
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/** Dark (the web's default), light, or whatever the phone uses. */
enum class ThemeChoice { DARK, LIGHT, SYSTEM }

@Singleton
class AccountRepository @Inject constructor(
    private val api: AccountApi,
    private val users: UserRepository,
    private val media: MediaUrlResolver,
    private val preferences: AppPreferences,
    private val json: Json,
) {
    val theme: Flow<String?> = preferences.theme

    suspend fun setTheme(choice: ThemeChoice) = preferences.setTheme(choice.name)

    val phoneNotifications: Flow<Boolean> = preferences.phoneNotifications

    suspend fun setPhoneNotifications(on: Boolean) = preferences.setPhoneNotifications(on)

    val appLock: Flow<Boolean> = preferences.appLock

    suspend fun setAppLock(on: Boolean) = preferences.setAppLock(on)

    val notificationPermissionAsked: Flow<Boolean> = preferences.notificationPermissionAsked

    suspend fun setNotificationPermissionAsked() = preferences.setNotificationPermissionAsked()

    /** Saves name and bio; the cached current user is refreshed so every screen shows the new one. */
    suspend fun resetPassword(token: String, newPassword: String): Outcome<Unit> =
        safeApiCall(json) { api.resetPassword(ResetPasswordRequest(token, newPassword)) }.map { }

    suspend fun confirmEmailChange(token: String): Outcome<Unit> =
        safeApiCall(json) { api.confirmEmailChange(TokenRequest(token)) }.map { }

    suspend fun updateProfile(
        firstName: String,
        lastName: String,
        bio: String,
        phoneNumber: String? = null,
        province: String? = null,
        city: String? = null,
    ): Outcome<User> =
        safeApiCall(json) {
            api.updateProfile(
                UpdateProfileRequest(firstName.trim(), lastName.trim(), bio.trim(), phoneNumber?.trim(), province, city),
            ).data
        }
            .map { it.toModel(media) }
            .also { if (it is Outcome.Success) users.me() }

    suspend fun forgotPassword(email: String): Outcome<Unit> =
        safeApiCall(json) { api.forgotPassword(ForgotPasswordRequest(email.trim())) }.map { }
}

data class TicketSummary(
    val id: String,
    val number: Long,
    val subject: String,
    val category: String,
    val status: String,
    val lastMessageAt: String,
    val awaitingYou: Boolean,
)

data class TicketMessage(val id: String, val fromSupport: Boolean, val body: String, val createdAt: String)

data class TicketDetail(
    val id: String,
    val number: Long,
    val subject: String,
    val category: String,
    val status: String,
    val messages: List<TicketMessage>,
) {
    val closed: Boolean get() = status == "CLOSED"
}

/** Support tickets, the member's side (`/tickets`): every read is scoped to the caller by apps/api. */
@Singleton
class SupportRepository @Inject constructor(private val api: SupportApi, private val json: Json) {
    suspend fun page(cursor: String?): Outcome<Page<TicketSummary>> = safeApiCall(json) { api.mine(cursor).data }.map { p ->
        Page(p.items.map { TicketSummary(it.id, it.number, it.subject, it.category, it.status, it.lastMessageAt, it.awaitingYou) }, p.nextCursor, p.hasMore)
    }

    suspend fun detail(id: String): Outcome<TicketDetail> = safeApiCall(json) { api.detail(id).data }.map { it.toModel() }

    suspend fun create(subject: String, category: String, body: String): Outcome<TicketDetail> =
        safeApiCall(json) { api.create(CreateTicketRequest(subject.trim(), category, body.trim())).data }.map { it.toModel() }

    suspend fun reply(id: String, body: String): Outcome<TicketDetail> =
        safeApiCall(json) { api.reply(id, TicketMessageRequest(body.trim())).data }.map { it.toModel() }

    suspend fun close(id: String): Outcome<TicketDetail> = safeApiCall(json) { api.close(id).data }.map { it.toModel() }

    private fun TicketDetailDto.toModel() = TicketDetail(
        id = id,
        number = number,
        subject = subject,
        category = category,
        status = status,
        messages = messages.map { TicketMessage(it.id, it.from == "SUPPORT", it.body, it.createdAt) },
    )
}

/** What can be reported, and why — apps/api's REPORT_TARGETS and REPORT_REASONS. */
enum class ReportTarget { PRODUCT, USER, STORE }

enum class ReportReason { SPAM, FRAUD, COUNTERFEIT, PROHIBITED, INAPPROPRIATE, HARASSMENT, OTHER }

@Singleton
class ReportsRepository @Inject constructor(private val api: ReportsApi, private val json: Json) {
    suspend fun report(target: ReportTarget, targetId: String, reason: ReportReason, details: String): Outcome<Unit> =
        safeApiCall(json) {
            api.create(CreateReportRequest(target.name, targetId, reason.name, details.trim().ifEmpty { null }))
        }.map { }
}
