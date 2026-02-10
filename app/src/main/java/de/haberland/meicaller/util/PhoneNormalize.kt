package de.haberland.meicaller.util

/**
 * Provides simple phone number normalization, particularly optimized for German (DE) numbers.
 * This function helps in creating consistent keys for comparisons or database lookups.
 *
 * It handles:
 * - Preservation of leading '+' for international format.
 * - Conversion of '00' prefix to '+'.
 * - Prefixing local numbers (starting with '0') with the default country code (e.g., +49).
 * - Ensuring numbers starting with the country code are prefixed with '+'.
 *
 * @param raw The raw phone number string to normalize.
 * @param defaultCountryCode The country code to use for local numbers (default is "49" for Germany).
 * @return A normalized phone number string.
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
