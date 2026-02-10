package de.haberland.meicaller.util

/**
 * Simple DE-friendly normalization for comparing keys:
 * - keep digits (+ only at start)
 * - 00xx -> +xx
 * - 0xxxx -> +49xxxx (default)
 * - 49xxxx -> +49xxxx
 *
 * This is not a full E.164 implementation, but solves +49 / 0 / 00 issues well.
 */
fun normalizeForCompare(
    raw: String,
    defaultCountryCode: String = "49",
): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""

    val hasPlus = t.startsWith("+")
    val digits = t.filter { it.isDigit() }

    var out =
        when {
            hasPlus -> "+$digits"
            digits.startsWith("00") -> "+" + digits.drop(2)
            else -> digits
        }

    if (!out.startsWith("+")) {
        out =
            when {
                out.startsWith(defaultCountryCode) -> "+$out"
                out.startsWith("0") -> "+$defaultCountryCode" + out.drop(1)
                else -> out
            }
    }

    return out
}
