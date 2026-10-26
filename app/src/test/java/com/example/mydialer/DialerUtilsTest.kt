package com.example.mydialer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialerUtilsTest {
    @Test
    fun validatesNormalAndInternationalNumbers() {
        assertNull(validateDialNumber("010-1234-5678"))
        assertNull(validateDialNumber(" +82 (10) 1234-5678 "))
        assertNull(validateDialNumber("*#06#"))
    }

    @Test
    fun rejectsEmptyUnsafeAndMalformedNumbers() {
        assertEquals("전화번호를 입력해 주세요.", validateDialNumber("   "))
        assertEquals("전화번호에 사용할 수 없는 문자가 있습니다.", validateDialNumber("010-12ABC"))
        assertEquals("국가 번호 기호(+)는 맨 앞에 한 번만 사용할 수 있습니다.", validateDialNumber("010+123+"))
        assertEquals("전화번호 괄호의 짝이 맞지 않습니다.", validateDialNumber("(010-1234-5678"))
        assertEquals("전화번호에 숫자 또는 서비스 코드를 입력해 주세요.", validateDialNumber("+++"))
    }

    @Test
    fun normalizesPhoneNumbersAndSearchesFormattedNumbers() {
        assertEquals("821012345678", normalizePhoneDigits("+82 (10) 1234-5678"))
        assertTrue(matchesPhoneQuery("홍길동", "010-1234-5678", "123456"))
        assertTrue(matchesPhoneQuery("Alice", "+82 10 5555 7777", "alice"))
        assertTrue(matchesPhoneQuery("홍길동", "010-1234-5678", "수신", "수신"))
        assertFalse(matchesPhoneQuery("홍길동", "010-1234-5678", "김철수"))
        assertTrue(matchesPhoneQuery("홍길동", "010-1234-5678", "  "))
    }

    @Test
    fun groupsKoreanAndLatinNames() {
        assertEquals("ㄱ", groupKey("가나다"))
        assertEquals("ㄱ", groupKey("까치"))
        assertEquals("ㄴ", groupKey("나무"))
        assertEquals("A", groupKey("Alice"))
        assertEquals("#", groupKey("123"))
        assertEquals("#", groupKey("   "))
    }

    @Test
    fun formatsDurationsAndClampsNegativeValues() {
        assertEquals("00:00", fmtDuration(0))
        assertEquals("01:05", fmtDuration(65))
        assertEquals("1:01:01", fmtDuration(3661))
        assertEquals("00:00", fmtDuration(-10))
        assertEquals("0분 00초", formatCallDuration(0))
        assertEquals("1분 05초", formatCallDuration(65))
        assertEquals("1시간 01분 01초", formatCallDuration(3661))
        assertEquals("0분 00초", formatCallDuration(-10))
    }
}
