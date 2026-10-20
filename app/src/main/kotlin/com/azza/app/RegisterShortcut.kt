package com.azza.app

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.azza.app.widget.EXTRA_OPEN_SCREEN
import com.azza.app.widget.WidgetTarget
import com.azza.core.common.ApplicationScope
import com.azza.core.data.pos.RegisterRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The "Sales register" app shortcut (long press on the AZZA icon, POS-05):
 * published while the signed-in person runs a store, withdrawn otherwise.
 * A dynamic shortcut rather than a static one, so it is not offered to
 * someone with nothing to sell.
 */
@Singleton
class RegisterShortcut @Inject constructor(
    @ApplicationContext private val context: Context,
    private val register: RegisterRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            register.stores.map { it.isNotEmpty() }.distinctUntilChanged().collect { owner ->
                runCatching { if (owner) publish() else ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(ID)) }
            }
        }
    }

    private fun publish() {
        val open = Intent(context, MainActivity::class.java)
            .setAction("com.azza.action.OPEN_REGISTER")
            .putExtra(EXTRA_OPEN_SCREEN, WidgetTarget.REGISTER.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val shortcut = ShortcutInfoCompat.Builder(context, ID)
            .setShortLabel(context.getString(R.string.shortcut_register_short))
            .setLongLabel(context.getString(R.string.shortcut_register_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_register))
            .setIntent(open)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    private companion object {
        const val ID = "register"
    }
}
