# StoryNest

AI cartoon picture books for children’s bedtime. Parents give **one** high-level idea; StoryNest writes every page, generates soft Western kids’-book illustrations with Gemini, and **saves text + images on-device** so covers don’t vanish later.

Package: `com.storynest.android` · Min SDK 26 · Kotlin · Jetpack Compose · Material 3

## Features (v1)

- **Library** — saved books with cover thumb, title, date; empty state explains create
- **Create** — single form: idea, age band (3–5 / 6–8), length (5 / 8 / 12), mood (cozy / funny / soft adventure) → one **Create** button
- **Generation pipeline** — Gemini writes full JSON story + character card; locked cartoon style; per-page images; Room index + files under app internal storage
- **Reader** — swipe pages, large text, night mode
- **Regenerate this page** — redo image (and optionally text) without remaking the book
- **Settings** — paste Gemini API key (EncryptedSharedPreferences); link to AI Studio; free-tier notes
- **Offline library** — reading works offline; create/regenerate need network

## Open in Android Studio

1. **File → Open** → select this folder (`storynest-android`)
2. Let Gradle sync (JDK 17+)
3. Run on a device/emulator, or build a debug APK (below)

## Build debug APK

```bash
./gradlew assembleDebug
```

APK path:

```
app/build/outputs/apk/debug/app-debug.apk
```

Sideload with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

(`local.properties` with `sdk.dir=…` is gitignored; Android Studio creates it automatically.)

## Gemini API key

1. Open [Google AI Studio → API keys](https://aistudio.google.com/apikey)
2. Create a key
3. In the app: **Settings** → paste key → **Save**

The key is stored only on-device (encrypted prefs). It is **not** bundled in the APK or committed to git.

**Free-tier limits:** text is usually fine; image generation can hit quotas quickly. If images fail, StoryNest still ships a readable book with cozy gradient placeholders and shows a clear message in Settings / generation progress.

### Models used

- Text: `gemini-3.5-flash` (fallback: `gemini-3.6-flash`, `gemini-flash-latest`, `gemini-3.1-flash-lite`)
- Images (tried in order): `gemini-3.1-flash-lite-image`, `gemini-3.1-flash-image`, `gemini-2.5-flash-image`, `gemini-3-pro-image`

## Kid safety defaults

System prompts keep stories age-appropriate, gentle endings, no gore/horror/romance; image prompts force cartoon-only soft picture-book style (not photoreal / not anime-dot-eye).

## Out of scope (v1)

TTS, PDF export, multi-child profiles, subscription, Play flavors, parent PIN.
