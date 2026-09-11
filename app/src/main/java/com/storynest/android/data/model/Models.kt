package com.storynest.android.data.model

import kotlinx.serialization.Serializable

enum class AgeBand(val label: String, val promptHint: String) {
    AGES_3_5("3–5", "ages 3 to 5: very simple words, short sentences, concrete images"),
    AGES_6_8("6–8", "ages 6 to 8: slightly richer vocabulary, gentle plot, clear feelings")
}

enum class StoryLength(val pages: Int, val label: String) {
    SHORT(5, "5 pages"),
    MEDIUM(8, "8 pages"),
    LONG(12, "12 pages")
}

enum class StoryMood(val label: String, val promptHint: String) {
    COZY("Cozy", "cozy, warm, snuggly bedtime tone"),
    FUNNY("Funny", "gentle humor, silly moments, light giggles — never mean"),
    SOFT_ADVENTURE("Soft adventure", "soft adventure with mild curiosity and safe exploration")
}

data class CreateBookRequest(
    val idea: String,
    val ageBand: AgeBand,
    val length: StoryLength,
    val mood: StoryMood
)

@Serializable
data class GeneratedStory(
    val title: String,
    val characterCard: String,
    val pages: List<GeneratedPage>
)

@Serializable
data class GeneratedPage(
    val pageNumber: Int,
    val text: String,
    val imagePrompt: String = ""
)

data class BookSummary(
    val id: String,
    val title: String,
    val createdAt: Long,
    val pageCount: Int,
    val coverPath: String?,
    val ageBand: String,
    val mood: String
)

data class BookDetail(
    val id: String,
    val title: String,
    val createdAt: Long,
    val ageBand: String,
    val mood: String,
    val characterCard: String,
    val styleLock: String,
    val pages: List<PageDetail>
)

data class PageDetail(
    val pageNumber: Int,
    val text: String,
    val imagePath: String?,
    val imagePrompt: String,
    val isPlaceholder: Boolean
)
