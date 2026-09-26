package subscriptiontracker.mobile.data

/**
 * Turns file bytes into text the way people actually save files: UTF-8,
 * or, if that isn't valid, the Windows character set that Excel's
 * "CSV (Comma delimited)" and Notepad's "ANSI" use. A byte order mark at the
 * start is removed.
 */
object TextDecoding {

    /** Windows-1252 characters for bytes 0x80 to 0x9F; the rest match Latin-1. */
    private const val WINDOWS_80_9F =
        "€\u0081‚ƒ„…†‡ˆ‰Š‹Œ\u008DŽ\u008F" +
            "\u0090‘’“”•–—˜™š›œ\u009DžŸ"

    fun decode(bytes: ByteArray): String {
        val text = try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (e: CharacterCodingException) {
            windows1252(bytes)
        }
        return text.removePrefix("﻿")
    }

    private fun windows1252(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size)
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            out.append(if (c in 0x80..0x9F) WINDOWS_80_9F[c - 0x80] else c.toChar())
        }
        return out.toString()
    }
}
