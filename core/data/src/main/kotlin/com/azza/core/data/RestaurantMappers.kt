package com.azza.core.data

import com.azza.core.model.Money
import com.azza.core.model.RestaurantInfo
import com.azza.core.network.model.RestaurantTermsDto

/** ✅ (2026-10-23, restaurants) apps/api's delivery terms as the app shows them. */
internal fun RestaurantTermsDto.toModel() = RestaurantInfo(
    deliveryFee = Money(deliveryFeeAmountMinor, "IRR"),
    minOrder = Money(minOrderAmountMinor, "IRR"),
    deliveryMinutes = deliveryMinutes?.let { it.min..it.max },
    paused = ordersPaused,
    ordering = ordering,
)
