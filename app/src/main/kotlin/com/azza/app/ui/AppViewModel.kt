package com.azza.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.azza.core.data.AccountRepository
import com.azza.core.data.AuthRepository
import com.azza.core.data.CartRepository
import com.azza.core.data.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The chrome's two badges: unread notifications on the bell, items in the basket on the bag. */
@HiltViewModel
class AppViewModel @Inject constructor(
    cart: CartRepository,
    private val notifications: NotificationsRepository,
    auth: AuthRepository,
    private val account: AccountRepository,
) : ViewModel() {

    /** "+" is for signed-in people only, as on the web. */
    val signedIn: StateFlow<Boolean> = auth.isSignedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val unreadNotifications: StateFlow<Int> = notifications.unreadCount

    val basketCount: StateFlow<Int> = cart.cart
        .map { it?.itemCount ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Android 13+ asks before an app may post notifications: once, after someone signs in. */
    val askNotificationPermission: StateFlow<Boolean> =
        combine(auth.isSignedIn, account.notificationPermissionAsked) { signedIn, asked -> signedIn && !asked }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun notificationPermissionAsked() = viewModelScope.launch { account.setNotificationPermissionAsked() }

    /** Like the web's badge, refreshed on navigation and when the app comes back to the front. */
    private var lastRefresh = 0L

    fun refreshBadges() {
        val now = android.os.SystemClock.elapsedRealtime()
        // Navigation, resume and sign-in arrive together at start-up: one request covers them.
        if (now - lastRefresh < 2_000) return
        lastRefresh = now
        viewModelScope.launch { notifications.refreshUnreadCount() }
    }
}
