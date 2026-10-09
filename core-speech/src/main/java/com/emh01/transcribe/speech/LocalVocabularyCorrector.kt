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
        if (text.isBlank() || glossary.isBlank()) return text

        val entries = glossary
            .split(',', ';', '\n')
            .map(String::trim)
            .filter { it.length >= 3 }
            .distinctBy(::normalize)
            .sortedByDescending { wordRegex.findAll(it).count() * 1000 + it.length }

        var corrected = text
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
            distance <= 3 && similarity >= 0.76f
        } else {
            canonical.length >= 5 && distance <= 2 && similarity >= 0.80f
        }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^\p{L}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

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
