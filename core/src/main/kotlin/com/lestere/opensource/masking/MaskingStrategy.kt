package com.lestere.opensource.masking

/**
 * Strategy for masking sensitive data in log messages.
 *
 * @author LesterE
 * @since 1.0.0
 */
interface MaskingStrategy {
    /**
     * Mask a sensitive value.
     *
     * @param key The key/name of the field being masked
     * @param value The original value
     * @return The masked value
     */
    fun mask(key: String, value: String): String

    /**
     * Check if this strategy applies to the given key.
     *
     * @param key The key to check
     * @return true if this strategy should be applied
     */
    fun appliesTo(key: String): Boolean
}

/**
 * Composite masking strategy that applies multiple strategies.
 */
class CompositeMaskingStrategy(
    private val strategies: List<MaskingStrategy>
) : MaskingStrategy {

    override fun mask(key: String, value: String): String {
        val strategy = strategies.find { it.appliesTo(key) }
        return strategy?.mask(key, value) ?: value
    }

    override fun appliesTo(key: String): Boolean {
        return strategies.any { it.appliesTo(key) }
    }

    /**
     * Mask all values in a map using applicable strategies.
     */
    fun maskMap(map: Map<String, String>): Map<String, String> {
        return map.mapValues { (k, v) ->
            if (appliesTo(k)) mask(k, v) else v
        }
    }

    /**
     * Mask sensitive values in a message string.
     * Looks for patterns like key=value or "key": "value"
     */
    fun maskMessage(message: String): String {
        var result = message
        strategies.forEach { strategy ->
            // Simple pattern matching for key=value or "key":"value"
            val keyPattern = Regex("([\"']?)(\\w+)([\"']?)\\s*[=:]\\s*([\"']?)([^\"'\\s,}]+)([\"']?)")
            result = keyPattern.replace(result) { match ->
                val key = match.groupValues[2]
                if (strategy.appliesTo(key)) {
                    val prefix = match.groupValues[1] + key + match.groupValues[3] + match.groupValues[4]
                    val suffix = match.groupValues[6]
                    prefix + strategy.mask(key, match.groupValues[5]) + suffix
                } else {
                    match.value
                }
            }
        }
        return result
    }
}

/**
 * Registry of default masking strategies.
 */
object MaskingStrategies {

    /**
     * Credit card masking: shows first 4 and last 4 digits.
     * Example: 4532123456789012 -> 4532****9012
     */
    val creditCard = object : MaskingStrategy {
        private val keys = setOf(
            "creditCard", "credit_card", "cardNumber", "card_number",
            "ccNumber", "cc_number", "cardNo", "card_no"
        )
        private val cardPattern = Regex("^\\d{13,19}$")

        override fun appliesTo(key: String): Boolean {
            return keys.any { key.lowercase().contains(it.lowercase()) }
        }

        override fun mask(key: String, value: String): String {
            if (!cardPattern.matches(value)) return value
            if (value.length < 8) return "*".repeat(value.length)
            return value.take(4) + "*".repeat(value.length - 8) + value.takeLast(4)
        }
    }

    /**
     * Email masking: shows first character and domain.
     * Example: user@example.com -> u***@example.com
     */
    val email = object : MaskingStrategy {
        private val keys = setOf("email", "emailAddress", "email_address", "mail")
        private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", RegexOption.IGNORE_CASE)

        override fun appliesTo(key: String): Boolean {
            return keys.any { key.lowercase().contains(it.lowercase()) }
        }

        override fun mask(key: String, value: String): String {
            if (!emailPattern.matches(value)) return value
            val parts = value.split("@")
            if (parts.size != 2) return value
            val local = parts[0]
            val maskedLocal = if (local.length > 1) {
                local.first() + "*".repeat(local.length - 1)
            } else {
                "*"
            }
            return "$maskedLocal@${parts[1]}"
        }
    }

    /**
     * Phone number masking: shows first 3 and last 2 digits.
     * Example: 13800138000 -> 138****8000
     */
    val phone = object : MaskingStrategy {
        private val keys = setOf("phone", "phoneNumber", "phone_number", "mobile", "tel")
        private val phonePattern = Regex("^\\+?[\\d\\s-]{10,20}$")

        override fun appliesTo(key: String): Boolean {
            return keys.any { key.lowercase().contains(it.lowercase()) }
        }

        override fun mask(key: String, value: String): String {
            val digits = value.replace(Regex("[^\\d]"), "")
            if (digits.length < 7) return "*".repeat(value.length)
            return digits.take(3) + "*".repeat(digits.length - 5) + digits.takeLast(2)
        }
    }

    /**
     * Password masking: completely hidden.
     * Example: mySecret123 -> ***********
     */
    val password = object : MaskingStrategy {
        private val keys = setOf(
            "password", "passwd", "pwd", "secret", "token",
            "apiKey", "api_key", "apikey", "accessToken", "access_token",
            "privateKey", "private_key", "secretKey", "secret_key"
        )

        override fun appliesTo(key: String): Boolean {
            return keys.any { key.lowercase().contains(it.lowercase()) }
        }

        override fun mask(key: String, value: String): String {
            return "*".repeat(minOf(value.length, 8))
        }
    }

    /**
     * SSN/ID masking: shows last 4 digits.
     * Example: 123-45-6789 -> ***-**-6789
     */
    val ssn = object : MaskingStrategy {
        private val keys = setOf("ssn", "socialSecurity", "social_security", "idNumber", "id_number", "idNo")
        private val ssnPattern = Regex("^\\d{3}-?\\d{2}-?\\d{4}$")

        override fun appliesTo(key: String): Boolean {
            return keys.any { key.lowercase().contains(it.lowercase()) }
        }

        override fun mask(key: String, value: String): String {
            if (!ssnPattern.matches(value)) return value
            val digits = value.replace("-", "")
            if (digits.length != 9) return value
            return "***-**-${digits.takeLast(4)}"
        }
    }

    /**
     * Get default composite strategy with all built-in strategies.
     */
    fun default(): CompositeMaskingStrategy {
        return CompositeMaskingStrategy(
            listOf(creditCard, email, phone, password, ssn)
        )
    }

    /**
     * Get strategy with only safe defaults (no password/token masking to avoid over-masking).
     */
    fun safe(): CompositeMaskingStrategy {
        return CompositeMaskingStrategy(listOf(creditCard, email, phone, ssn))
    }
}
