package com.example.util

import com.example.data.CodeDetectionConfigEntity
import java.util.regex.Pattern

data class DetectedCodeResult(
    val code: String,
    val type: String,
    val contextSnippet: String
)

object CodeDetector {

    private val recentlyDetectedCache = mutableMapOf<String, Long>()

    fun isDuplicate(code: String, pkg: String): Boolean {
        val now = System.currentTimeMillis()
        val cacheKey = "$pkg:$code"
        val lastDetected = recentlyDetectedCache[cacheKey]
        if (lastDetected != null && (now - lastDetected) < 180_000) {
            return true
        }
        recentlyDetectedCache[cacheKey] = now
        recentlyDetectedCache.entries.removeIf { (now - it.value) > 180_000 }
        return false
    }

    /**
     * Attempts to extract a verification code from a text body based on the provided configuration.
     */
    fun detectCode(text: String, config: CodeDetectionConfigEntity): DetectedCodeResult? {
        if (text.isBlank()) return null

        // 0. Prototype Message matching
        if (config.prototypeMessage.isNotBlank()) {
            if (config.prototypeMessage.contains("[CODE]")) {
                try {
                    val parts = config.prototypeMessage.split("[CODE]")
                    val regexStr = parts.joinToString("([a-zA-Z0-9]+)") { Pattern.quote(it) }
                    val pattern = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE)
                    val matcher = pattern.matcher(text)
                    if (matcher.find() && matcher.groupCount() >= 1) {
                        val match = matcher.group(1)
                        if (!match.isNullOrBlank()) {
                            return DetectedCodeResult(
                                code = match.trim(),
                                type = "PROTOTYPE_MATCH",
                                contextSnippet = extractSnippet(text, matcher.start(), matcher.end())
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
            // If prototype message is set, strict matching is enforced. Reject if it doesn't match.
            return null
        }

        // 1. If custom regex is provided and valid, try it first
        if (config.customRegex.isNotBlank()) {
            try {
                val pattern = Pattern.compile(config.customRegex, Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(text)
                if (matcher.find()) {
                    val match = if (matcher.groupCount() >= 1 && matcher.group(1) != null) {
                        matcher.group(1)!!
                    } else {
                        matcher.group()
                    }
                    if (match.isNotBlank()) {
                        val snippet = extractSnippet(text, matcher.start(), matcher.end())
                        return DetectedCodeResult(
                            code = match.trim(),
                            type = "CUSTOM_REGEX",
                            contextSnippet = snippet
                        )
                    }
                }
            } catch (_: Exception) {
                // Ignore invalid regex syntax and fall back to standard detectors
            }
        }

        val keywords = config.keywordFilter
            .split(",", ";", "\n")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        val hasKeyword = if (keywords.isEmpty()) {
            true
        } else {
            val lower = text.lowercase()
            keywords.any { lower.contains(it) }
        }

        // 2. Google / Standard Service formatted codes (e.g., G-123456 or G - 123456)
        val servicePrefixPattern = Pattern.compile("""\b([A-Z])-?(\d{4,8})\b""", Pattern.CASE_INSENSITIVE)
        val serviceMatcher = servicePrefixPattern.matcher(text)
        if (serviceMatcher.find()) {
            val fullCode = serviceMatcher.group()
            val snippet = extractSnippet(text, serviceMatcher.start(), serviceMatcher.end())
            return DetectedCodeResult(
                code = fullCode.trim(),
                type = "SERVICE_PREFIX",
                contextSnippet = snippet
            )
        }

        // 3. Hyphenated 2FA codes (e.g., 123-456, W8KZ-9P2A, 1234-5678)
        if (config.allowAlphanumeric || hasKeyword) {
            val hyphenPattern = Pattern.compile("""\b([A-Z0-9]{3,5})-([A-Z0-9]{3,5})\b""", Pattern.CASE_INSENSITIVE)
            val hyphenMatcher = hyphenPattern.matcher(text)
            if (hyphenMatcher.find()) {
                val matched = hyphenMatcher.group()
                // Avoid matching common hyphenated words
                if (matched.any { it.isDigit() }) {
                    val snippet = extractSnippet(text, hyphenMatcher.start(), hyphenMatcher.end())
                    return DetectedCodeResult(
                        code = matched.trim(),
                        type = "HYPHENATED_2FA",
                        contextSnippet = snippet
                    )
                }
            }
        }

        // 4. Numeric OTP extraction with contextual awareness
        val minD = config.minDigits.coerceIn(3, 10)
        val maxD = config.maxDigits.coerceIn(minD, 12)

        // Find potential numeric codes
        val numericPattern = Pattern.compile("""\b(\d{$minD,$maxD})\b""")
        val numMatcher = numericPattern.matcher(text)
        val candidates = mutableListOf<Pair<String, IntRange>>()

        while (numMatcher.find()) {
            val candidate = numMatcher.group()
            val range = numMatcher.start()..numMatcher.end()
            candidates.add(candidate to range)
        }

        if (candidates.isNotEmpty()) {
            // If text contains keywords, prioritize candidate closest to a keyword
            if (hasKeyword && keywords.isNotEmpty()) {
                val lower = text.lowercase()
                var bestCandidate: Pair<String, IntRange>? = null
                var minDistance = Int.MAX_VALUE

                for (candidate in candidates) {
                    val candMid = (candidate.second.first + candidate.second.last) / 2
                    for (kw in keywords) {
                        var kwIdx = lower.indexOf(kw)
                        while (kwIdx != -1) {
                            val kwMid = kwIdx + kw.length / 2
                            val dist = Math.abs(candMid - kwMid)
                            if (dist < minDistance) {
                                minDistance = dist
                                bestCandidate = candidate
                            }
                            kwIdx = lower.indexOf(kw, kwIdx + 1)
                        }
                    }
                }

                if (bestCandidate != null) {
                    val snippet = extractSnippet(text, bestCandidate.second.first, bestCandidate.second.last)
                    return DetectedCodeResult(
                        code = bestCandidate.first,
                        type = "NUMERIC_OTP",
                        contextSnippet = snippet
                    )
                }
            } else if (hasKeyword || !config.requireKeywordForNumeric) {
                // Take first candidate if no keyword distance calculation needed
                val first = candidates.first()
                val snippet = extractSnippet(text, first.second.first, first.second.last)
                return DetectedCodeResult(
                    code = first.first,
                    type = "NUMERIC_OTP",
                    contextSnippet = snippet
                )
            }
        }

        // 5. Alphanumeric OTP (e.g., 4 to 8 characters mixed letters/digits like 9X8Y7Z or ABC123)
        if (config.allowAlphanumeric && hasKeyword) {
            val alphaNumPattern = Pattern.compile("""\b([A-Z0-9]{$minD,$maxD})\b""", Pattern.CASE_INSENSITIVE)
            val alphaMatcher = alphaNumPattern.matcher(text)
            while (alphaMatcher.find()) {
                val cand = alphaMatcher.group()
                // Must contain at least one digit and at least one letter, and not look like regular words
                val hasDigit = cand.any { it.isDigit() }
                val hasLetter = cand.any { it.isLetter() }
                if (hasDigit && hasLetter) {
                    val snippet = extractSnippet(text, alphaMatcher.start(), alphaMatcher.end())
                    return DetectedCodeResult(
                        code = cand.trim(),
                        type = "ALPHANUMERIC_2FA",
                        contextSnippet = snippet
                    )
                }
            }
        }

        return null
    }

    private fun extractSnippet(text: String, start: Int, end: Int, contextPadding: Int = 30): String {
        val snippetStart = (start - contextPadding).coerceAtLeast(0)
        val snippetEnd = (end + contextPadding).coerceAtMost(text.length)
        val prefix = if (snippetStart > 0) "…" else ""
        val suffix = if (snippetEnd < text.length) "…" else ""
        return prefix + text.substring(snippetStart, snippetEnd).trim() + suffix
    }
}
