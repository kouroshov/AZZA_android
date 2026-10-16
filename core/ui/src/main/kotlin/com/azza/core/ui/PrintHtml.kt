package com.azza.core.ui

import android.annotation.SuppressLint
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Prints a small HTML page through the phone's printing service (a
 * printer, or "Save as PDF"). HTML because a label mixes Persian and Latin
 * text, which the browser engine lays out correctly in both directions.
 */
@SuppressLint("SetJavaScriptEnabled")
fun printHtml(context: Context, jobName: String, html: String) {
    val webView = WebView(context)
    // Held until the page has loaded and the job is handed over; a WebView collected earlier prints nothing.
    pending += webView
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            manager.print(jobName, view.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A6).build())
            pending -= view
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

private val pending = mutableSetOf<WebView>()

/** Text placed in HTML, never read as markup (a name or address is something someone typed). */
fun htmlEscape(text: String): String = buildString(text.length) {
    for (c in text) when (c) {
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        '&' -> append("&amp;")
        '"' -> append("&quot;")
        '\'' -> append("&#39;")
        else -> append(c)
    }
}
