package com.azza.core.common

/**
 * EAN-13 symbols — a line-for-line port of the website's `src/lib/ean13.ts`,
 * so a label printed from the app scans exactly like one printed from the
 * web. Every listing's barcode is made by the database (GS1 prefix 2).
 */
object Ean13Symbol {
    private val L = listOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
    private val G = listOf("0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111")
    private val R = listOf("1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100")
    private val PARITY = listOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")

    /** Modules (dark = true) of the 95-module symbol; null when [code] is not a valid EAN-13. */
    fun modules(code: String): List<Boolean>? {
        if (!isValid(code)) return null
        val parity = PARITY[code[0] - '0']
        val bits = StringBuilder("101")
        for (i in 1..6) bits.append(if (parity[i - 1] == 'L') L[code[i] - '0'] else G[code[i] - '0'])
        bits.append("01010")
        for (i in 7..12) bits.append(R[code[i] - '0'])
        bits.append("101")
        return bits.map { it == '1' }
    }

    /** The start, centre and end guard bars, drawn taller. */
    fun isGuard(index: Int): Boolean = index in 0 until 3 || index in 45 until 50 || index in 92 until 95

    fun isValid(code: String): Boolean {
        if (code.length != 13 || code.any { it !in '0'..'9' }) return false
        val total = (0 until 12).sumOf { i -> (code[i] - '0') * if (i % 2 == 0) 1 else 3 }
        return (10 - total % 10) % 10 == code[12] - '0'
    }
}
