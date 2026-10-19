package com.azza.core.common

/**
 * A line-for-line port of apps/api's `store-inventory/product-code.util.ts`
 * (POS-01), so a code scanned here and the code the server stored are always
 * the same string. The unit test uses the server's own test vectors — change
 * both files together.
 *
 * - trimmed;
 * - a numeric GTIN is checked by its check digit (EAN-13, EAN-8, UPC-A,
 *   GTIN-14 with a leading 0) and stored as 13 digits where it fits: UPC-A
 *   (12) gains a leading 0, a GTIN-14 with a leading 0 loses it; EAN-8 stays 8;
 * - a URL is a QR code; anything else printable is Code 128.
 */
object ProductCodes {
    enum class Symbology { EAN13, UPC_A, EAN8, CODE128, QR }

    enum class Invalid { EMPTY, BAD_CHECK_DIGIT, TOO_LONG, UNPRINTABLE }

    sealed interface Normalized {
        data class Ok(val code: String, val symbology: Symbology) : Normalized
        data class Bad(val reason: Invalid) : Normalized
    }

    /** The longest code the server stores (VARCHAR(128)). */
    const val MAX_LENGTH = 128

    /** The GS1 check digit for [body]: from the right, weights 3,1,3,1,… */
    fun checkDigit(body: String): Int {
        var sum = 0
        for (i in body.indices) {
            val digit = body[body.length - 1 - i] - '0'
            sum += digit * if (i % 2 == 0) 3 else 1
        }
        return (10 - sum % 10) % 10
    }

    fun hasValidCheckDigit(digits: String): Boolean {
        if (digits.length < 2 || digits.any { it !in '0'..'9' }) return false
        return checkDigit(digits.dropLast(1)) == digits.last() - '0'
    }

    /** An internal code: EAN-13 in the GS1 in-store range (prefix 2), as the server generates. */
    fun isInternal(code: String): Boolean =
        code.length == 13 && code[0] == '2' && code.all { it in '0'..'9' } && hasValidCheckDigit(code)

    fun normalize(raw: String?, hint: Symbology? = null): Normalized {
        val code = raw.orEmpty().trim()
        if (code.isEmpty()) return Normalized.Bad(Invalid.EMPTY)
        if (code.length > MAX_LENGTH) return Normalized.Bad(Invalid.TOO_LONG)

        if (hint != Symbology.CODE128 && hint != Symbology.QR && code.all { it in '0'..'9' }) {
            return when (code.length) {
                8 -> if (hasValidCheckDigit(code)) Normalized.Ok(code, Symbology.EAN8) else bad()
                // UPC-A is EAN-13 with a leading 0: same number, one form stored.
                12 -> if (hasValidCheckDigit(code)) Normalized.Ok("0$code", Symbology.EAN13) else bad()
                13 -> if (hasValidCheckDigit(code)) Normalized.Ok(code, Symbology.EAN13) else bad()
                14 -> when {
                    !hasValidCheckDigit(code) -> bad()
                    // A GTIN-14 with a leading 0 is the same item as the EAN-13 inside it.
                    code.startsWith("0") -> Normalized.Ok(code.substring(1), Symbology.EAN13)
                    else -> Normalized.Ok(code, Symbology.CODE128)
                }
                // Digits of another length are not a GTIN: an internal shelf number.
                else -> Normalized.Ok(code, Symbology.CODE128)
            }
        }

        if (code.any { it.code in 0x00..0x1f || it.code == 0x7f }) return Normalized.Bad(Invalid.UNPRINTABLE)
        if (hint == Symbology.QR || code.startsWith("http://", ignoreCase = true) || code.startsWith("https://", ignoreCase = true)) {
            return Normalized.Ok(code, Symbology.QR)
        }
        if (code.any { it.code !in 0x20..0x7e }) return Normalized.Ok(code, Symbology.QR)
        return Normalized.Ok(code, Symbology.CODE128)
    }

    private fun bad() = Normalized.Bad(Invalid.BAD_CHECK_DIGIT)
}
