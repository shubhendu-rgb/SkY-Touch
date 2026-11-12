package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Helpers for the "open a web link" gesture action. */
object WebLinkHelper {

    private val schemeRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://")

    /**
     * Cleans up what the user typed. "example.com" becomes "https://example.com".
     * Returns null when it is empty or is not an http / https link.
     */
    fun normalize(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val withScheme = if (schemeRegex.containsMatchIn(text)) text else "https://$text"
        val uri = try {
            Uri.parse(withScheme)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return withScheme
    }

    /** Short text for lists, e.g. "example.com/page". */
    fun displayName(url: String?): String {
        val clean = normalize(url) ?: return url.orEmpty()
        return clean.removePrefix("https://").removePrefix("http://").removeSuffix("/")
    }

    /**
     * Opens the link in the phone's default browser app (not in a verified-link app such as the
     * YouTube app). Falls back to a normal VIEW intent if no default browser can be resolved.
     * Returns false when the link is invalid or nothing can open it.
     */
    fun open(context: Context, rawUrl: String?): Boolean {
        val url = normalize(rawUrl) ?: return false
        val uri = Uri.parse(url)
        try {
            val browserIntent = Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN,
                Intent.CATEGORY_APP_BROWSER
            ).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            return true
        } catch (_: Exception) {
            // fall through to the normal VIEW intent
        }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
