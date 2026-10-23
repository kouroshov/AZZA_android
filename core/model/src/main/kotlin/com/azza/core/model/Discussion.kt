package com.azza.core.model

/** ✅ (2026-10-23) Who wrote a comment or a review: public profile fields only. */
data class DiscussionAuthor(val id: String, val username: String, val name: String, val avatarUrl: String?)

data class Comment(val id: String, val parentId: String?, val body: String, val createdAt: String, val author: DiscussionAuthor)

data class Review(
    val id: String,
    val rating: Int,
    val title: String?,
    val body: String?,
    val createdAt: String,
    val author: DiscussionAuthor,
    val verifiedPurchase: Boolean,
)

/** Digikala's rating box: how many, the average, and how many gave each star (1..5). */
data class ReviewSummary(val count: Int, val average: Double?, val byStar: Map<Int, Int>)

/** Everything under a listing: its rating, its reviews and its comments. */
data class Discussion(val summary: ReviewSummary, val reviews: List<Review>, val comments: List<Comment>)
