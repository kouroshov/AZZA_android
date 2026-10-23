package com.azza.feature.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.azza.core.designsystem.theme.AzzaTheme
import com.azza.core.model.Comment
import com.azza.core.model.Discussion
import com.azza.core.ui.Avatar
import com.azza.core.ui.formatInstantDate
import com.azza.core.ui.formatPlainNumber

/** What the post page knows about the viewer, for the comment and review forms. */
data class DiscussionUi(
    val discussion: Discussion? = null,
    /** A paid, not-yet-reviewed order line of this listing, when the viewer has one. */
    val reviewableOrderItem: String? = null,
    val sending: Boolean = false,
    val reviewSent: Boolean = false,
)

/**
 * ✅ (2026-10-23, the audit: "the app shows no comments or reviews"). The
 * web's ProductDiscussion: the rating box (Digikala), the reviews — each
 * from a paid purchase — and comments, where a visitor asks and the seller
 * answers. The review form shows only when apps/api says the viewer may.
 */
@Composable
internal fun DiscussionSection(
    ui: DiscussionUi,
    myId: String?,
    sellerId: String,
    /** Reviews come from purchases: a listing not sold through the basket shows none (unless it has some). */
    reviewable: Boolean,
    onComment: (body: String, parentId: String?) -> Unit,
    onDelete: (String) -> Unit,
    onReview: (rating: Int, title: String, body: String) -> Unit,
    onRequireSignIn: () -> Unit,
) {
    val d = ui.discussion ?: return
    val colors = AzzaTheme.colors
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.discussion_title), style = MaterialTheme.typography.titleMedium)

        // Rating box
        if (reviewable || d.summary.count > 0) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceRaised).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.summary.average?.let { formatPlainNumber(it) } ?: "—", style = MaterialTheme.typography.headlineMedium)
                Stars(d.summary.average ?: 0.0)
                Text(
                    if (d.summary.count == 0) stringResource(R.string.discussion_no_reviews)
                    else pluralStringResource(R.plurals.discussion_review_count, d.summary.count, d.summary.count),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            if (d.summary.count > 0) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    (5 downTo 1).forEach { star ->
                        val n = d.summary.byStar[star] ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(formatPlainNumber(star.toDouble()), style = MaterialTheme.typography.labelSmall)
                            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.border)) {
                                Box(Modifier.fillMaxWidth(n.toFloat() / d.summary.count).height(6.dp).background(colors.warning))
                            }
                            Text(formatPlainNumber(n.toDouble()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(24.dp))
                        }
                    }
                }
            }
        }

        if (ui.reviewableOrderItem != null && !ui.reviewSent) ReviewForm(ui.sending, onReview)
        if (ui.reviewSent) Text(stringResource(R.string.discussion_review_thanks), color = colors.success, style = MaterialTheme.typography.bodyMedium)

        d.reviews.forEach { r ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stars(r.rating.toDouble())
                    if (r.verifiedPurchase) {
                        Icon(Icons.Outlined.Verified, null, tint = colors.success, modifier = Modifier.size(14.dp))
                        Text(stringResource(R.string.discussion_bought), style = MaterialTheme.typography.labelSmall, color = colors.success)
                    }
                }
                r.title?.let { Text(it, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content)) }
                r.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)) }
                AuthorRow(r.author.avatarUrl, r.author.name, r.createdAt)
            }
        }

        // Comments
        Text(
            if (d.comments.isEmpty()) stringResource(R.string.discussion_comments)
            else pluralStringResource(R.plurals.discussion_comment_count, d.comments.size, d.comments.size),
            style = MaterialTheme.typography.titleSmall,
        )
        if (d.comments.isEmpty()) Text(stringResource(R.string.discussion_no_comments), color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
        var replyTo by rememberSaveable { mutableStateOf<String?>(null) }
        d.comments.filter { it.parentId == null }.forEach { c ->
            CommentRow(c, sellerId, myId, nested = false, onReply = { replyTo = c.id }.takeIf { myId != null }, onDelete = onDelete)
            d.comments.filter { it.parentId == c.id }.forEach { reply ->
                CommentRow(reply, sellerId, myId, nested = true, onReply = null, onDelete = onDelete)
            }
        }
        var text by rememberSaveable { mutableStateOf("") }
        replyTo?.let { id ->
            val name = d.comments.firstOrNull { it.id == id }?.author?.name.orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.discussion_replying_to, name), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                TextButton(onClick = { replyTo = null }) { Text(stringResource(R.string.discussion_cancel)) }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { if (myId == null) onRequireSignIn() else text = it.take(2000) },
            placeholder = { Text(stringResource(if (myId == null) R.string.discussion_sign_in else R.string.discussion_comment_placeholder)) },
            minLines = 2,
            textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                if (myId == null) return@OutlinedButton onRequireSignIn()
                onComment(text, replyTo)
                text = ""
                replyTo = null
            },
            enabled = text.isNotBlank() && !ui.sending,
        ) { Text(stringResource(R.string.discussion_send)) }
    }
}

@Composable
private fun Stars(value: Double) {
    Row {
        (1..5).forEach { n ->
            Icon(
                if (n <= Math.round(value)) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (n <= Math.round(value)) AzzaTheme.colors.warning else AzzaTheme.colors.textMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AuthorRow(avatarUrl: String?, name: String, at: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Avatar(url = avatarUrl, isStore = false, size = 24)
        Text(name, style = MaterialTheme.typography.labelLarge)
        Text(formatInstantDate(at), style = MaterialTheme.typography.labelSmall, color = AzzaTheme.colors.textMuted)
    }
}

@Composable
private fun CommentRow(c: Comment, sellerId: String, myId: String?, nested: Boolean, onReply: (() -> Unit)?, onDelete: (String) -> Unit) {
    Column(Modifier.padding(start = if (nested) 24.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { AuthorRow(c.author.avatarUrl, c.author.name, c.createdAt) }
            if (c.author.id == sellerId) {
                Text(
                    stringResource(R.string.discussion_seller),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(AzzaTheme.colors.selectedContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (myId != null && c.author.id == myId) {
                IconButton(onClick = { onDelete(c.id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.discussion_delete)) }
            }
        }
        Text(c.body, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
        onReply?.let { TextButton(onClick = it) { Text(stringResource(R.string.discussion_reply)) } }
    }
}

@Composable
private fun ReviewForm(sending: Boolean, onReview: (Int, String, String) -> Unit) {
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AzzaTheme.colors.selectedContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.discussion_review_prompt), style = MaterialTheme.typography.titleSmall)
        Row {
            (1..5).forEach { n ->
                IconButton(onClick = { rating = n }) {
                    Icon(
                        if (n <= rating) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = pluralStringResource(R.plurals.discussion_stars, n, n),
                        tint = if (n <= rating) AzzaTheme.colors.warning else AzzaTheme.colors.textMuted,
                    )
                }
            }
        }
        OutlinedTextField(title, { title = it.take(200) }, placeholder = { Text(stringResource(R.string.discussion_review_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it.take(4000) }, placeholder = { Text(stringResource(R.string.discussion_review_body)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onReview(rating, title, body) }, enabled = rating > 0 && !sending) { Text(stringResource(R.string.discussion_send_review)) }
    }
}
