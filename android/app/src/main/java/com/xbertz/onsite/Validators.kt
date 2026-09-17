package com.xbertz.onsite

import android.util.Patterns

/**
 * Field validation shared by the Profile and Companies forms. Every rule treats a
 * blank value as valid — the fields are optional; only non-empty input is checked.
 * Bank/ABN rules follow the Australian formats the app is built around.
 */
object Validators {

    private val ABN_WEIGHTS = intArrayOf(10, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19)

    /** 11 digits (spaces allowed) passing the ATO modulus-89 check. */
    fun isValidAbn(value: String): Boolean {
        val digits = value.filter { it.isDigit() }
        if (value.any { !it.isDigit() && !it.isWhitespace() }) return false
        if (digits.length != 11) return false
        val sum = digits.mapIndexed { index, c ->
            val digit = c.digitToInt() - if (index == 0) 1 else 0
            digit * ABN_WEIGHTS[index]
        }.sum()
        return sum % 89 == 0
    }

    /** 6 digits, optionally written as 123-456 or 123 456. */
    fun isValidBsb(value: String): Boolean {
        if (value.any { !it.isDigit() && it != '-' && !it.isWhitespace() }) return false
        return value.filter { it.isDigit() }.length == 6
    }

    /** Australian account numbers are 6 to 10 digits. */
    fun isValidAccountNumber(value: String): Boolean {
        if (value.any { !it.isDigit() && !it.isWhitespace() }) return false
        return value.filter { it.isDigit() }.length in 6..10
    }

    /** Optional leading "+", then 8-15 digits; spaces, dashes and parentheses are ignored. */
    fun isValidPhone(value: String): Boolean {
        val stripped = value.filter { !it.isWhitespace() && it != '-' && it != '(' && it != ')' }
        return Regex("^\\+?\\d{8,15}$").matches(stripped)
    }

    fun isValidEmail(value: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()

    /** Money typed by the user: digits with an optional "." or "," decimal part, never negative. */
    fun parseAmount(value: String): Double? {
        val normalised = value.trim().replace(',', '.')
        if (normalised.isBlank() || !Regex("^\\d+(\\.\\d{0,2})?$").matches(normalised)) return null
        return normalised.toDoubleOrNull()?.takeIf { it >= 0 }
    }

    fun isValidAmount(value: String): Boolean = parseAmount(value) != null

    // "Optional" variants: blank is fine, otherwise the rule applies.
    fun abnOk(value: String) = value.isBlank() || isValidAbn(value)
    fun bsbOk(value: String) = value.isBlank() || isValidBsb(value)
    fun accountNumberOk(value: String) = value.isBlank() || isValidAccountNumber(value)
    fun phoneOk(value: String) = value.isBlank() || isValidPhone(value)
    fun emailOk(value: String) = value.isBlank() || isValidEmail(value)
    fun amountOk(value: String) = value.isBlank() || isValidAmount(value)
}
