package com.example.mydialer

import java.util.Locale

/**
 * Pure dialer helpers shared by UI and tests. Keep this file free of Android APIs
 * so the core formatting/validation rules can be tested on the JVM in CI.
 */
fun validateDialNumber(rawNumber: String): String? {
    val number = rawNumber.trim()
    if (number.isEmpty()) return "전화번호를 입력해 주세요."

    val allowed = "+*#()-. "
    if (number.any { !it.isDigit() && it !in allowed }) {
        return "전화번호에 사용할 수 없는 문자가 있습니다."
    }
    if (number.none { it.isDigit() || it == '*' || it == '#' }) {
        return "전화번호에 숫자 또는 서비스 코드를 입력해 주세요."
    }
    if (number.count { it == '+' } > 1 || ('+' in number && number.indexOf('+') != number.indexOfFirst { !it.isWhitespace() })) {
        return "국가 번호 기호(+)는 맨 앞에 한 번만 사용할 수 있습니다."
    }
    if (number.count { it == '(' } != number.count { it == ')' }) {
        return "전화번호 괄호의 짝이 맞지 않습니다."
    }
    if (number.indexOf(')') >= 0 && number.indexOf('(') > number.indexOf(')')) {
        return "전화번호 괄호의 위치를 확인해 주세요."
    }
    return null
}

/** Returns only numeric characters, useful for matching differently formatted phone numbers. */
fun normalizePhoneDigits(value: String): String = value.filter(Char::isDigit)

/** Name/number search that tolerates spaces, hyphens and country-code punctuation. */
fun matchesPhoneQuery(name: String, number: String, query: String, extraText: String? = null): Boolean {
    val q = query.trim().lowercase(Locale.KOREA)
    if (q.isEmpty()) return true
    if (name.lowercase(Locale.KOREA).contains(q) ||
        number.lowercase(Locale.KOREA).contains(q) ||
        extraText?.lowercase(Locale.KOREA)?.contains(q) == true
    ) return true

    val digits = normalizePhoneDigits(q)
    return digits.isNotEmpty() && normalizePhoneDigits(number).contains(digits)
}

/** Initial group label for Korean Hangul names and Latin names. */
fun groupKey(name: String): String {
    val c = name.trim().firstOrNull() ?: return "#"
    if (c in '\uAC00'..'\uD7A3') {
        val cho = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"[(c - '\uAC00') / 588]
        return when (cho) {
            'ㄲ' -> "ㄱ"
            'ㄸ' -> "ㄷ"
            'ㅃ' -> "ㅂ"
            'ㅆ' -> "ㅅ"
            'ㅉ' -> "ㅈ"
            else -> cho.toString()
        }
    }
    return if (c.isLetter()) c.uppercaseChar().toString() else "#"
}

/** Duration used by the connected-call status. Negative clock deltas are clamped to zero. */
fun fmtDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    return if (safe >= 3600L) {
        String.format(Locale.KOREA, "%d:%02d:%02d", safe / 3600L, safe % 3600L / 60L, safe % 60L)
    } else {
        String.format(Locale.KOREA, "%02d:%02d", safe / 60L, safe % 60L)
    }
}

/** Localized duration shown in call-history details. */
fun formatCallDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    return if (safe >= 3600L) {
        String.format(Locale.KOREA, "%d시간 %02d분 %02d초", safe / 3600L, safe % 3600L / 60L, safe % 60L)
    } else {
        String.format(Locale.KOREA, "%d분 %02d초", safe / 60L, safe % 60L)
    }
}
