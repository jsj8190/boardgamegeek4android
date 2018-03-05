package com.boardgamegeek

import android.text.Html
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.widget.TextView

fun TextView.setTextMaybeHtml(text: String?) {
    when {
        text == null -> this.text = ""
        text.isBlank() -> this.text = ""
        text.contains("<") && text.contains(">") || text.contains("&") && text.contains(";") -> {
            var html = text
            // Fix up problematic HTML
            // replace DIVs with BR
            html = html.replace("[<]div[^>]*[>]".toRegex(), "")
            html = html.replace("[<]/div[>]".toRegex(), "<br/>")
            // remove all P tags
            html = html.replace("[<](/)?p[>]".toRegex(), "")
            // remove trailing BRs
            html = html.replace("(<br\\s?/>)+$".toRegex(), "")
            // replace 3+ BRs with a double
            html = html.replace("(<br\\s?/>){3,}".toRegex(), "<br/><br/>")
            // use BRs instead of new line character
            html = html.replace("\n".toRegex(), "<br/>")
            html = fixInternalLinks(html)

            val spanned = Html.fromHtml(html)
            this.text = spanned
            this.movementMethod = LinkMovementMethod.getInstance()
        }
        else -> this.text = text
    }
}

private fun fixInternalLinks(html: String): String {
    // ensure internal, path-only links are complete with the hostname
    if (TextUtils.isEmpty(html)) return ""
    var fixedText = html.replace("<a\\s+href=\"/".toRegex(), "<a href=\"https://www.boardgamegeek.com/")
    fixedText = fixedText.replace("<img\\s+src=\"//".toRegex(), "<img src=\"https://")
    return fixedText
}