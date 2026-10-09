package com.emh01.transcribe.speech

import java.text.Normalizer
import kotlin.math.max

/**
 * Conservative post-processing for user-provided names and difficult terms.
 *
 * Whisper remains responsible for the transcription. This corrector only
 * replaces a word or same-length phrase when it is very close to an entry in
 * the local glossary, so the glossary can restore spellings such as
 * "Este María" -> "Esther María" without acting as a general autocorrect.
 */
object LocalVocabularyCorrector {
    private val wordRegex = Regex("""\p{L}+(?:['’\-]\p{L}+)*""")

    fun correct(text: String, glossary: String): String {
        if (text.isBlank()) return text

        var corrected = applyKnownDictationCorrections(text)
        if (glossary.isBlank()) return corrected

        val entries = glossary
            .split(',', ';', '\n')
            .map(String::trim)
            .filter { it.length >= 3 }
            .distinctBy(::normalize)
            .sortedByDescending { wordRegex.findAll(it).count() * 1000 + it.length }

        for (entry in entries) {
            corrected = applyEntry(corrected, entry)
        }
        return corrected
    }

    private fun applyEntry(text: String, entry: String): String {
        val entryWords = wordRegex.findAll(entry).map { it.value }.toList()
        if (entryWords.isEmpty()) return text

        val matches = wordRegex.findAll(text).toList()
        if (matches.size < entryWords.size) return text

        val canonicalNorm = normalize(entryWords.joinToString(" "))
        if (canonicalNorm.isBlank()) return text

        val replacements = mutableListOf<IntRange>()
        for (start in 0..matches.size - entryWords.size) {
            val end = start + entryWords.size - 1
            val first = matches[start]
            val last = matches[end]
            val raw = text.substring(first.range.first, last.range.last + 1)
            val candidateNorm = normalize(
                matches.subList(start, end + 1).joinToString(" ") { it.value },
            )

            // For a fuzzy single-word correction, require Whisper to have
            // treated the candidate like a proper noun (capitalized). Exact
            // normalized matches may still restore accents/canonical casing.
            if (
                entryWords.size == 1 &&
                candidateNorm != canonicalNorm &&
                raw.firstOrNull()?.isLowerCase() == true
            ) continue

            if (!isCloseEnough(candidateNorm, canonicalNorm, entryWords.size)) continue
            if (raw == entry) continue
            replacements += first.range.first..last.range.last
        }

        if (replacements.isEmpty()) return text

        val result = StringBuilder(text)
        for (range in replacements.asReversed()) {
            result.replace(range.first, range.last + 1, entry)
        }
        return result.toString()
    }

    private fun isCloseEnough(candidate: String, canonical: String, wordCount: Int): Boolean {
        if (candidate == canonical) return true
        if (candidate.isBlank() || canonical.isBlank()) return false
        if (candidate.first() != canonical.first()) return false

        val distance = levenshtein(candidate, canonical)
        val longest = max(candidate.length, canonical.length)
        val similarity = 1f - distance.toFloat() / longest

        return if (wordCount > 1) {
            val candidateWords = candidate.split(' ')
            val canonicalWords = canonical.split(' ')
            val trailingWordsMatch =
                candidateWords.size == canonicalWords.size &&
                    candidateWords.size > 1 &&
                    candidateWords.drop(1) == canonicalWords.drop(1)
            val leadingPrefixMatch =
                candidateWords.firstOrNull()?.take(3) ==
                    canonicalWords.firstOrNull()?.take(3)

            distance <= 3 && (
                similarity >= 0.76f ||
                    (trailingWordsMatch && leadingPrefixMatch && similarity >= 0.74f)
                )
        } else {
            canonical.length >= 5 && distance <= 2 && similarity >= 0.80f
        }
    }

    private fun applyKnownDictationCorrections(text: String): String {
        var corrected = text
        for ((variant, canonical) in KNOWN_DICTATION_CORRECTIONS) {
            val pattern = Regex(
                """(?iu)(?<!\p{L})${Regex.escape(variant)}(?!\p{L})""",
            )
            corrected = pattern.replace(corrected) { match ->
                if (match.value.firstOrNull()?.isUpperCase() == true) {
                    canonical.replaceFirstChar { it.uppercase() }
                } else {
                    canonical
                }
            }
        }
        return corrected
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^\p{L}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private val KNOWN_DICTATION_CORRECTIONS = mapOf(
        "ditar" to "dictar",
    )

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val substitution = previous[j] + if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    substitution,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
