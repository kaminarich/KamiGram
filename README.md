# ⚡ KamiGram

KamiGram is a third-party Telegram client with a skeuomorphic pastel design and the Extraordikami feature set — deleted messages stay visible, profile IDs are always shown, and every crash lands in `kamigram.log`.

- Repository: https://github.com/kaminarich/KamiGram
- Downloads: https://github.com/kaminarich/KamiGram/releases
- Feedback: https://github.com/kaminarich/KamiGram/issues

Every build is produced by GitHub Actions; no local toolchain is required. Pushing to `master` publishes an APK artifact, pushing a `v*` tag publishes a Release.

## Extraordikami

KamiGram's modifications, toggleable from the chat menu → Extraordikami:

- **Show Deleted Messages** — messages deleted for everyone stay in the chat with a `DELETED` label instead of disappearing. Kept messages survive restarts. Your own *delete for me* still deletes normally.
- **Show User ID in Profile** — the profile header shows `@username` and the numeric ID immediately on open.

Always on, not toggleable:

- **kamigram.log** — every crash and error is recorded to `files/kamigram.log` on all build types (upstream only logs on debug builds).
- **Visible text selection** — selection highlights and quote markers use a high-contrast pastel green in both light and dark themes.
- **Skeuomorphic design** — one light model across the whole UI: embossed message bubbles, carved switch tracks with raised knobs, raised unread badges, and coin-framed avatars. Light and dark themes are authored as a pair; no color pair drops below WCAG large-text contrast.

## API, Protocol documentation

Telegram API manuals: https://core.telegram.org/api

MTProto protocol manuals: https://core.telegram.org/mtproto

## Compilation Guide

The CI workflow (`.github/workflows/android.yml`) is the canonical build. To reproduce it locally:

1. Clone the source (`git clone https://github.com/kaminarich/KamiGram.git`)
1. Create your own `api_id` / `api_hash` at https://my.telegram.org (per Telegram's terms, forks must not ship the author's credentials — the build fails fast without them)
1. Fill out `TELEGRAM_APP_ID`, `TELEGRAM_APP_HASH`, `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` in `local.properties` (git-ignored) to point at your own keystore
1. Optional, for FCM push: create a Firebase project with an Android app `com.kaminari.gram`, enable Cloud Messaging, and copy `google-services.json` into `TMessagesProj_App/`. Without it the build succeeds and push falls back to the app's own connection.
1. Open the project in Android Studio (opened, NOT imported), JDK 17, NDK 21.4.7075529, CMake 3.10.2
1. You are ready to compile KamiGram: `./gradlew :TMessagesProj_App:assembleAfatRelease`

Output: `TMessagesProj_App/build/outputs/apk/afat/release/app.apk` — universal APK, `armeabi-v7a` / `arm64-v8a` / `x86` / `x86_64`, minSdk 21, targetSdk 35.

## Repository secrets (for the CI build)

Set under Settings → Secrets and variables → Actions:

| Secret | Purpose |
| --- | --- |
| `TELEGRAM_APP_ID` / `TELEGRAM_APP_HASH` | your api credentials from my.telegram.org |
| `KEYSTORE_BASE64` | `base64 -w0 your.jks` |
| `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD` | keystore access |
| `GOOGLE_SERVICES_JSON` | optional; FCM push, needs a `com.kaminari.gram` client |

No credentials are committed. `google-services.json`, keystores and `local.properties` are git-ignored; `APP_ID`/`APP_HASH` arrive via Gradle properties at build time.

## Localization

KamiGram is forked from Telegram, so most locales follow the translations of Telegram for Android: https://translations.telegram.org/en/android/. Fork-specific strings (branding, Extraordikami) are currently English-only; the bundled locale files carry the KamiGram renaming, and cloud language packs are rewritten at load time so server translations cannot restore the Telegram branding.

## License

KamiGram is forked from [Telegram for Android](https://github.com/DrKLO/Telegram) and licensed under GPL v2 or later — see [LICENSE](https://github.com/kaminarich/KamiGram/blob/master/LICENSE). Per Telegram's terms for third-party clients, KamiGram ships its own `api_id`, name and logo, and publishes its source.
