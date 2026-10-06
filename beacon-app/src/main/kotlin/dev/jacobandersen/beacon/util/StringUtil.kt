package dev.jacobandersen.beacon.util

object StringUtil {
    /** Strips a surrounding pair of double quotes, when present. */
    internal fun String.unquote(): String =
        this.trim().let {
            if (it.startsWith("\"") && it.endsWith("\"") && it.length >= 2) {
                it.substring(1, it.length - 1)
            } else {
                it
            }
        }

    /** An excerpt of at most [maxLen] characters, optionally with an ellipsis. */
    fun String.excerpt(maxLen: Int): String {
        val len = maxLen.coerceIn(0, this.length)
        return this.substring(0, len)
    }
}
