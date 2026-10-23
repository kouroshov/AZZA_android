package com.azza.core.network.model

import kotlinx.serialization.Serializable

// ✅ (2026-10-23) A listing's comments and reviews (apps/api's social module):
// each with its author's public profile fields.

@Serializable
data class DiscussionAuthorDto(val id: String, val username: String, val name: String = "", val avatarKey: String? = null)

@Serializable
data class CommentDto(
    val id: String,
    val parentId: String? = null,
    val body: String,
    val createdAt: String,
    val author: DiscussionAuthorDto,
)

@Serializable
data class ReviewDto(
    val id: String,
    val rating: Int,
    val title: String? = null,
    val body: String? = null,
    val createdAt: String,
    val author: DiscussionAuthorDto,
    val verifiedPurchase: Boolean = true,
)

@Serializable
data class ReviewSummaryDto(val count: Int = 0, val average: Double? = null, val byStar: Map<String, Int> = emptyMap())

@Serializable
data class ReviewEligibilityDto(val orderItemId: String? = null)

@Serializable
data class CreateCommentRequest(val body: String, val parentId: String? = null)

@Serializable
data class CreateReviewRequest(val orderItemId: String, val rating: Int, val title: String? = null, val body: String? = null)

/** Only the id of what was created is read back. */
@Serializable
data class CreatedIdDto(val id: String)
