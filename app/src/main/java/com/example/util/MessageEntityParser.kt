package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

enum class MessageEntityType {
    URL,
    EMAIL,
    PHONE
}

data class DetectedMessageEntity(
    val type: MessageEntityType,
    val rawValue: String
)

object MessageEntityParser {

    const val TAG_URL = "ENTITY_URL"
    const val TAG_EMAIL = "ENTITY_EMAIL"
    const val TAG_PHONE = "ENTITY_PHONE"

    private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val URL_REGEX = Regex("""(?:https?://|www\.)[^\s<>()]+""", RegexOption.IGNORE_CASE)
    private val PHONE_REGEX = Regex("""(?<!\w)(?:\+?\d[\d\s\-().]{6,}\d|01\d{7})(?!\w)""")

    private data class SpanMatch(
        val start: Int,
        val endExclusive: Int,
        val type: MessageEntityType,
        val value: String
    )

    fun buildHighlightedText(
        text: String,
        linkColor: Color
    ): AnnotatedString {
        if (text.isBlank()) return AnnotatedString(text)

        val matches = mutableListOf<SpanMatch>()

        EMAIL_REGEX.findAll(text).forEach { m ->
            matches += SpanMatch(m.range.first, m.range.last + 1, MessageEntityType.EMAIL, m.value)
        }
        URL_REGEX.findAll(text).forEach { m ->
            val overlap = matches.any { !(m.range.last < it.start || m.range.first >= it.endExclusive) }
            if (!overlap) {
                matches += SpanMatch(m.range.first, m.range.last + 1, MessageEntityType.URL, m.value)
            }
        }
        PHONE_REGEX.findAll(text).forEach { m ->
            val digitsOnly = m.value.filter { it.isDigit() }
            if (digitsOnly.length in 7..15) {
                val overlap = matches.any { !(m.range.last < it.start || m.range.first >= it.endExclusive) }
                if (!overlap) {
                    matches += SpanMatch(m.range.first, m.range.last + 1, MessageEntityType.PHONE, m.value.trim())
                }
            }
        }

        if (matches.isEmpty()) return AnnotatedString(text)
        matches.sortBy { it.start }

        return buildAnnotatedString {
            append(text)
            matches.forEach { span ->
                val tag = when (span.type) {
                    MessageEntityType.URL -> TAG_URL
                    MessageEntityType.EMAIL -> TAG_EMAIL
                    MessageEntityType.PHONE -> TAG_PHONE
                }
                addStyle(
                    style = SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline
                    ),
                    start = span.start,
                    end = span.endExclusive
                )
                addStringAnnotation(
                    tag = tag,
                    annotation = span.value,
                    start = span.start,
                    end = span.endExclusive
                )
            }
        }
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}
