package com.azza.feature.basket

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/** [tab]: a [BasketTab] ordinal — the web's `/Basket?view=` / `/orders` opening on a tab. */
@Serializable data class BasketRoute(val tab: Int = 0)

@Serializable data object CheckoutRoute

@Serializable data class PlacedOrderRoute(val orderId: String)

@Serializable data class ReceivedOrderRoute(val sellerOrderId: String)

fun NavGraphBuilder.basketScreens(
    onSignIn: () -> Unit,
    onBrowse: () -> Unit,
    onOpenProduct: (String) -> Unit,
    onCheckout: () -> Unit,
    onOpenPlaced: (String) -> Unit,
    onOpenReceived: (String) -> Unit,
    onBack: () -> Unit,
) {
    composable<BasketRoute> { entry ->
        val tab = BasketTab.entries.getOrElse(entry.toRoute<BasketRoute>().tab) { BasketTab.BASKET }
        BasketScreen(
            initialTab = tab,
            onSignIn = onSignIn,
            onBrowse = onBrowse,
            onOpenProduct = onOpenProduct,
            onCheckout = onCheckout,
            onOpenPlaced = onOpenPlaced,
            onOpenReceived = onOpenReceived,
        )
    }
    composable<CheckoutRoute> { CheckoutScreen(onBackToCart = onBack, onViewOrder = onOpenPlaced) }
    composable<PlacedOrderRoute> { PlacedOrderScreen(onBack = onBack) }
    composable<ReceivedOrderRoute> { ReceivedOrderScreen(onBack = onBack) }
}
