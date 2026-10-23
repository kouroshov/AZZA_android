package com.azza.core.data

import com.azza.core.common.Outcome
import com.azza.core.common.map
import com.azza.core.model.Comment
import com.azza.core.model.Discussion
import com.azza.core.model.DiscussionAuthor
import com.azza.core.model.Review
import com.azza.core.model.ReviewSummary
import com.azza.core.network.MediaUrlResolver
import com.azza.core.network.api.SocialApi
import com.azza.core.network.model.CreateCommentRequest
import com.azza.core.network.model.CreateReviewRequest
import com.azza.core.network.model.DiscussionAuthorDto
import com.azza.core.network.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

/**
 * ✅ (2026-10-23, the audit: "the app shows no comments or reviews"). A
 * listing's rating, reviews and comments (the web's ProductDiscussion).
 * A review comes only from a paid purchase: [reviewableOrderItem] says
 * whether the caller has one not yet reviewed.
 */
@Singleton
class DiscussionRepository @Inject constructor(
    private val api: SocialApi,
    private val media: MediaUrlResolver,
    private val json: Json,
) {
    suspend fun load(productId: String): Outcome<Discussion> = coroutineScope {
        val summary = async { safeApiCall(json) { api.reviewSummary(productId).data } }
        val reviews = async { safeApiCall(json) { api.reviews(productId).data } }
        val comments = async { safeApiCall(json) { api.comments(productId).data } }
        val s = summary.await()
        val r = reviews.await()
        val c = comments.await()
        when {
            s is Outcome.Failure -> s
            r is Outcome.Failure -> r
            c is Outcome.Failure -> c
            else -> Outcome.Success(
                Discussion(
                    summary = (s as Outcome.Success).value.let { dto ->
                        ReviewSummary(dto.count, dto.average, (1..5).associateWith { dto.byStar[it.toString()] ?: 0 })
                    },
                    reviews = (r as Outcome.Success).value.map {
                        Review(it.id, it.rating, it.title, it.body, it.createdAt, it.author.toModel(), it.verifiedPurchase)
                    },
                    comments = (c as Outcome.Success).value.map { Comment(it.id, it.parentId, it.body, it.createdAt, it.author.toModel()) },
                ),
            )
        }
    }

    suspend fun reviewableOrderItem(productId: String): Outcome<String?> =
        safeApiCall(json) { api.reviewEligibility(productId).data }.map { it.orderItemId }

    suspend fun addComment(productId: String, body: String, parentId: String?): Outcome<Unit> =
        safeApiCall(json) { api.addComment(productId, CreateCommentRequest(body.trim(), parentId)) }.map { }

    suspend fun deleteComment(commentId: String): Outcome<Unit> = safeApiCall(json) { api.deleteComment(commentId) }

    suspend fun addReview(productId: String, orderItemId: String, rating: Int, title: String?, body: String?): Outcome<Unit> =
        safeApiCall(json) {
            api.addReview(productId, CreateReviewRequest(orderItemId, rating, title?.trim()?.ifBlank { null }, body?.trim()?.ifBlank { null }))
        }.map { }

    private fun DiscussionAuthorDto.toModel() = DiscussionAuthor(id, username, name.ifBlank { username }, media.resolve(avatarKey))
}
