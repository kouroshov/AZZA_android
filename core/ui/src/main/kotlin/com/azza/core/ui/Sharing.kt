package com.azza.core.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.staticCompositionLocalOf
import com.azza.core.common.WebLinks

/** The website's addresses, provided once by the app (it knows the origin from its build). */
val LocalWebLinks = staticCompositionLocalOf<WebLinks> { error("LocalWebLinks is not provided") }

/** The phone's share sheet with a page's title and its website address. */
fun shareLink(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, if (title.isBlank()) url else "$title\n$url")
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
