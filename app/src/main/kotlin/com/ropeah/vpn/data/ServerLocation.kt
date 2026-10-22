package com.ropeah.vpn.data

import java.util.Locale

/** Country/flag metadata resolved for a server card. */
data class ServerLocation(
    val countryCode: String?,
    val countryName: String,
    val flag: String?,
)

fun ServerConfig.countryLocation(): ServerLocation? = ServerLocations.resolve(
    countryCode = countryCode,
    countryName = countryName,
    flag = flag,
    name = name,
)

object ServerLocations {

    private val isoCodes: Set<String> by lazy {
        Locale.getISOCountries().toSet()
    }

    /** Two-letter tokens that are far more likely to be protocol/telemetry text. */
    private val nonCountryTokens = setOf(
        "AS", "ID", "IP", "KB", "MB", "MS", "SS", "TB", "VM", "WS",
    )

    /** Country codes that are also common storage units; accepted unless they follow a number. */
    private val unitTokens = setOf("GB")

    private val extraCountryAliases = mapOf(
        "usa" to "US",
        "uk" to "GB",
        "uae" to "AE",
        "south korea" to "KR",
        "north korea" to "KP",
        "russia" to "RU",
        "vietnam" to "VN",
        "czech republic" to "CZ",
        "ivory coast" to "CI",
        "taiwan" to "TW",
        "hong kong" to "HK",
        "macau" to "MO",
        "laos" to "LA",
        "brunei" to "BN",
        "moldova" to "MD",
        "tanzania" to "TZ",
        "iran" to "IR",
        "syria" to "SY",
        "palestine" to "PS",
        "bolivia" to "BO",
        "venezuela" to "VE",
    )

    private val countryAliases: List<Pair<String, String>> by lazy {
        (isoCodes.mapNotNull { code ->
            val name = displayName(code)
            if (name.isBlank() || name == code) null else name.lowercase(Locale.ENGLISH) to code
        } + extraCountryAliases.entries.map { it.key to it.value })
            .sortedByDescending { it.first.length }
    }

    fun resolve(
        countryCode: String? = null,
        countryName: String? = null,
        flag: String? = null,
        name: String? = null,
    ): ServerLocation? {
        var code = countryCode?.trim()?.uppercase(Locale.US)
            ?.takeIf { it in isoCodes }
        var resolvedName = countryName?.trim()?.takeIf {
            it.isNotBlank() &&
                !it.equals("Unknown", ignoreCase = true) &&
                !it.equals("N/A", ignoreCase = true)
        }
        var resolvedFlag = flag?.trim()?.takeIf { it.isNotBlank() && it != "🏳️" }

        val fromExplicitFlag = resolvedFlag?.let(::findFlag)
        if (code == null) code = fromExplicitFlag?.second
        val fromName = name?.let(::findFlag)
        if (code == null) code = fromName?.second
        if (code == null) code = name?.let(::findCodeToken)
        if (code == null) code = (resolvedName ?: name)?.let(::findCountryCode)
        if (resolvedFlag == null) resolvedFlag = fromName?.first
        if (resolvedName == null && code != null) resolvedName = displayName(code)

        if (code == null && resolvedName == null && resolvedFlag == null) return null
        if (resolvedFlag == null && code != null) resolvedFlag = flagFor(code)
        return ServerLocation(
            countryCode = code,
            countryName = resolvedName ?: code ?: resolvedFlag.orEmpty(),
            flag = resolvedFlag,
        )
    }

    private fun findFlag(text: String): Pair<String, String>? {
        var i = 0
        while (i + 3 < text.length) {
            if (text[i] == '\uD83C' &&
                text[i + 1] in '\uDDE6'..'\uDDFF' &&
                text[i + 2] == '\uD83C' &&
                text[i + 3] in '\uDDE6'..'\uDDFF'
            ) {
                val flag = text.substring(i, i + 4)
                val first = 'A' + (text[i + 1].code - 0xDDE6)
                val second = 'A' + (text[i + 3].code - 0xDDE6)
                val code = "$first$second"
                if (code in isoCodes) return flag to code
            }
            i++
        }
        return null
    }

    private fun findCodeToken(text: String): String? {
        val pattern = Regex("(?<![A-Za-z])([A-Z]{2})(?![A-Za-z])")
        for (match in pattern.findAll(text)) {
            val code = match.groupValues[1]
            if (code !in isoCodes || code in nonCountryTokens) continue
            if (code in unitTokens && precededByNumber(text, match.range.first)) continue
            return code
        }
        return null
    }

    private fun precededByNumber(text: String, start: Int): Boolean {
        var index = start - 1
        while (index >= 0 && text[index].isWhitespace()) index--
        return index >= 0 && text[index].isDigit()
    }

    private fun findCountryCode(text: String): String? {
        val lower = text.lowercase(Locale.ENGLISH)
        return countryAliases.firstOrNull { (alias, _) -> containsWord(lower, alias) }?.second
    }

    private fun containsWord(text: String, word: String): Boolean {
        var index = text.indexOf(word)
        while (index >= 0) {
            val before = text.getOrNull(index - 1)
            val after = text.getOrNull(index + word.length)
            if ((before == null || !before.isLetter()) && (after == null || !after.isLetter())) {
                return true
            }
            index = text.indexOf(word, index + 1)
        }
        return false
    }

    private fun displayName(code: String): String =
        Locale.Builder().setRegion(code).build()
            .getDisplayCountry(Locale.ENGLISH)
            .takeIf { it.isNotBlank() }
            ?: code

    private fun flagFor(code: String): String = buildString {
        code.forEach { letter ->
            append(String(Character.toChars(0x1F1E6 + (letter - 'A'))))
        }
    }
}
