package com.azza.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azza.core.data.CartRepository
import com.azza.core.data.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The chrome's two badges: unread notifications on the bell, items in the basket on the bag. */
@HiltViewModel
class AppViewModel @Inject constructor(
    cart: CartRepository,
    private val notifications: NotificationsRepository,
) : ViewModel() {

    val unreadNotifications: StateFlow<Int> = notifications.unreadCount

    val basketCount: StateFlow<Int> = cart.cart
        .map { it?.itemCount ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Like the web's badge, refreshed on navigation and when the app comes back to the front. */
    fun refreshBadges() = viewModelScope.launch { notifications.refreshUnreadCount() }
}
