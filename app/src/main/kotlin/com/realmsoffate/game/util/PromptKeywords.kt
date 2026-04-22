package com.realmsoffate.game.util

/**
 * Extract keyword tokens from free-form text for entity retrieval.
 *
 * Lowercased, non-letter-split, deduped, stopword-filtered, min length 3.
 */
object PromptKeywords {
    private val STOPWORDS = setOf(
        "the","and","but","for","are","was","were","you","your","yours",
        "him","her","his","she","they","their","them","our","ours",
        "this","that","these","those","with","from","into","onto","over","under",
        "than","then","when","where","what","which","who","whom","why","how",
        "will","would","could","should","have","has","had","not","can","cant",
        "now","all","any","some","one","two","too","very","just","still",
        "about","there","here","also","been","being","more","most","much","such",
        "only","even","ever","never","back","down","away","each","every"
    )

    fun extract(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^a-z]+"))
            .asSequence()
            .filter { it.length >= 3 && it !in STOPWORDS }
            .distinct()
            .toList()
}
