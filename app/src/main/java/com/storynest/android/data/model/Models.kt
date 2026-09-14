package com.storynest.android.data.model

import kotlinx.serialization.Serializable

enum class AgeBand(val label: String, val promptHint: String) {
    AGES_3_5("3–5", "ages 3 to 5: simple warm words, 2–4 short sentences per page, concrete actions and settings a young child can picture"),
    AGES_6_8("6–8", "ages 6 to 8: slightly richer vocabulary, 3–5 short sentences per page, gentle plot, clear feelings, concrete scenes"),
    AGES_9_10("9–10", "ages 9 to 10: engaging chapter-like picture-book pages, 4–7 sentences per page, richer vocabulary, real curiosity and mild mystery still safe for bedtime, characters with thoughts and choices"),
    AGES_11_12("11–12", "ages 11 to 12: more sophisticated bedtime stories, 5–8 sentences per page, stronger plot and character voice, gentle suspense okay if resolved warmly, never graphic or romantic"),
    TEENS_13_17("13–17", "teens 13 to 17: richer prose, 6–10 sentences per page, real emotions and coming-of-age curiosity, gentle stakes, still wholesome and bedtime-safe — no graphic violence, explicit romance, or horror"),
    ADULTS_18("Grown-ups", "adults 18+: polished illustrated short-story pages, 7–12 sentences per page, thoughtful voice and sensory detail parents/partners enjoy reading together — warm, literary-lite, never graphic, erotic, or nightmare fuel")
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

/**
 * Story body language. imagePrompt + characterCard stay English for image models.
 */
enum class StoryLanguage(
    val label: String,
    val nativeName: String,
    val promptName: String,
    val scriptHint: String
) {
    ENGLISH("English", "English", "English", "Latin script"),
    MARATHI("Marathi", "मराठी", "Marathi (मराठी)", "Devanagari script — write ALL story text in Marathi Devanagari, not transliteration"),
    HINDI("Hindi", "हिन्दी", "Hindi (हिन्दी)", "Devanagari script — write ALL story text in Hindi Devanagari, not transliteration"),
    TAMIL("Tamil", "தமிழ்", "Tamil (தமிழ்)", "Tamil script — write ALL story text in Tamil, not transliteration"),
    TELUGU("Telugu", "తెలుగు", "Telugu (తెలుగు)", "Telugu script — write ALL story text in Telugu, not transliteration"),
    KANNADA("Kannada", "ಕನ್ನಡ", "Kannada (ಕನ್ನಡ)", "Kannada script — write ALL story text in Kannada, not transliteration"),
    GUJARATI("Gujarati", "ગુજરાતી", "Gujarati (ગુજરાતી)", "Gujarati script — write ALL story text in Gujarati, not transliteration")
}

data class CreateBookRequest(
    val idea: String,
    val ageBand: AgeBand,
    val length: StoryLength,
    val mood: StoryMood,
    val language: StoryLanguage = StoryLanguage.ENGLISH
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
