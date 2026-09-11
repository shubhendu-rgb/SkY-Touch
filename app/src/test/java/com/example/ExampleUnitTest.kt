package com.example

import com.example.data.CodeDetectionConfigEntity
import com.example.util.CodeDetector
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testNumericOtpDetection() {
        val config = CodeDetectionConfigEntity(minDigits = 4, maxDigits = 8)
        val message = "Your one-time password for transaction of $142.50 at Amazon is 839201. Valid for 10 minutes."
        val result = CodeDetector.detectCode(message, config)

        assertNotNull(result)
        assertEquals("839201", result?.code)
        assertEquals("NUMERIC_OTP", result?.type)
    }

    @Test
    fun testGoogleServiceCodeDetection() {
        val config = CodeDetectionConfigEntity()
        val message = "G-749201 is your Google verification code."
        val result = CodeDetector.detectCode(message, config)

        assertNotNull(result)
        assertEquals("G-749201", result?.code)
        assertEquals("SERVICE_PREFIX", result?.type)
    }

    @Test
    fun testHyphenatedTwoFactorAuthDetection() {
        val config = CodeDetectionConfigEntity(allowAlphanumeric = true)
        val message = "Your Discord security login code is W8KZ-9P2A. Do not share."
        val result = CodeDetector.detectCode(message, config)

        assertNotNull(result)
        assertEquals("W8KZ-9P2A", result?.code)
        assertEquals("HYPHENATED_2FA", result?.type)
    }

    @Test
    fun testAlphanumericOtpDetection() {
        val config = CodeDetectionConfigEntity(allowAlphanumeric = true)
        val message = "Your verification code is 9X8Y7Z. Enter this to confirm."
        val result = CodeDetector.detectCode(message, config)

        assertNotNull(result)
        assertEquals("9X8Y7Z", result?.code)
        assertEquals("ALPHANUMERIC_2FA", result?.type)
    }

    @Test
    fun testCustomRegexDetection() {
        val config = CodeDetectionConfigEntity(customRegex = """PIN:\s*([0-9]{4})""")
        val message = "Welcome! Your activation PIN: 9021. Enjoy the service."
        val result = CodeDetector.detectCode(message, config)

        assertNotNull(result)
        assertEquals("9021", result?.code)
        assertEquals("CUSTOM_REGEX", result?.type)
    }
}
