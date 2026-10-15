package com.azza.feature.notifications

import android.content.res.Resources
import com.azza.core.model.AppNotification

// Plain functions, not composables: the list and the background poll's
// system notifications say the same thing in the same words.

fun notificationTitle(res: Resources, n: AppNotification): String = res.getString(
    when (n.type) {
        "USER_FOLLOWED" -> R.string.n_type_user_followed
        "STORE_FOLLOWED" -> R.string.n_type_store_followed
        "ORDER_RECEIVED" -> R.string.n_type_order_received
        "ORDER_PAID" -> R.string.n_type_order_paid
        "ORDER_SHIPPED" -> R.string.n_type_order_shipped
        "PRODUCT_REVIEWED" -> R.string.n_type_product_reviewed
        "APPOINTMENT_BOOKED" -> R.string.n_type_appointment_booked
        "APPOINTMENT_CANCELLED" -> R.string.n_type_appointment_cancelled
        "REPORT_REVIEWED" -> R.string.n_type_report_reviewed
        "LISTING_TAKEN_DOWN" -> R.string.n_type_listing_taken_down
        "LISTING_RESTORED" -> R.string.n_type_listing_restored
        "STORE_SUSPENDED" -> R.string.n_type_store_suspended
        "STORE_RESTORED" -> R.string.n_type_store_restored
        "CONTENT_HIDDEN" -> R.string.n_type_content_hidden
        "APPEAL_DECIDED" -> R.string.n_type_appeal_decided
        "TICKET_REPLIED" -> R.string.n_type_ticket_replied
        "ACCOUNT_SECURITY_NOTICE" -> R.string.n_type_account_security
        "ORDER_CANCELLED" -> R.string.n_type_order_cancelled
        "ORDER_REFUNDED" -> R.string.n_type_order_refunded
        "PAYOUT_SENT" -> R.string.n_type_payout_sent
        else -> R.string.n_type_generic
    },
)

/** The web's sentences (the web's `notifications.text…` messages); `reference` is the subject title for order events. */
fun notificationBody(res: Resources, n: AppNotification): String {
    val actor = n.actorName ?: res.getString(R.string.n_someone)
    val subject = n.subjectTitle.orEmpty()
    return when (n.type) {
        "USER_FOLLOWED" -> res.getString(R.string.n_text_user_followed, actor)
        "STORE_FOLLOWED" -> res.getString(R.string.n_text_store_followed, actor, subject)
        "ORDER_RECEIVED" -> res.getString(R.string.n_text_order_received, actor, subject)
        "ORDER_PAID" -> res.getString(R.string.n_text_order_paid, subject)
        "ORDER_SHIPPED" -> res.getString(R.string.n_text_order_shipped, subject)
        "PRODUCT_REVIEWED" -> res.getString(R.string.n_text_product_reviewed, actor, subject)
        "APPOINTMENT_BOOKED" -> res.getString(R.string.n_text_appointment_booked, actor, subject)
        "APPOINTMENT_CANCELLED" -> res.getString(R.string.n_text_appointment_cancelled, actor, subject)
        "REPORT_REVIEWED" ->
            if (subject.isNotEmpty()) res.getString(R.string.n_text_report_reviewed_subject, subject) else res.getString(R.string.n_text_report_reviewed)
        "LISTING_TAKEN_DOWN" -> res.getString(R.string.n_text_listing_taken_down, subject)
        "LISTING_RESTORED" -> res.getString(R.string.n_text_listing_restored, subject)
        "STORE_SUSPENDED" -> res.getString(R.string.n_text_store_suspended, subject)
        "STORE_RESTORED" -> res.getString(R.string.n_text_store_restored, subject)
        "CONTENT_HIDDEN" -> res.getString(R.string.n_text_content_hidden, subject)
        "APPEAL_DECIDED" -> res.getString(R.string.n_text_appeal_decided, subject)
        "TICKET_REPLIED" -> res.getString(R.string.n_text_ticket_replied, subject)
        "ACCOUNT_SECURITY_NOTICE" -> res.getString(
            when (subject) {
                // subjectTitle is a CODE here, not text (CLAUDE.md, phase 3 of the admin plan).
                "SIGNED_OUT" -> R.string.n_text_security_signed_out
                "PASSWORD_RESET" -> R.string.n_text_security_password_reset
                "EMAIL_VERIFIED" -> R.string.n_text_security_email_verified
                else -> R.string.n_text_security_generic
            },
        )
        "ORDER_CANCELLED" ->
            res.getString(if (n.sellerOrderId != null) R.string.n_text_order_cancelled_seller else R.string.n_text_order_cancelled, subject)
        "ORDER_REFUNDED" ->
            res.getString(if (n.sellerOrderId != null) R.string.n_text_order_refunded_seller else R.string.n_text_order_refunded, subject)
        "PAYOUT_SENT" -> res.getString(R.string.n_text_payout_sent)
        else -> subject
    }
}
