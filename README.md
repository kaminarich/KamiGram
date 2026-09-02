## KamiGram

KamiGram (`com.kaminari.gram`) is a Telegram client for Android with a skeuomorphic,
light-pastel interface. It is a fork of the official [Telegram for Android](https://github.com/DrKLO/Telegram)
client and remains licensed under GPL v2 or later — see [LICENSE](LICENSE).

The name is short for *kaminari* (lightning), which is also the app icon: a rounded
pastel bolt on a soft sky-blue plate.

### Design

* Warm paper surfaces (`#FAF7F2`) instead of flat white, with a cream/lilac chat wallpaper.
* Pastel accent (`#8EC9E8`), mint outgoing bubbles (`#E3F2DF`), cream incoming bubbles (`#FDFAF4`).
* Message bubbles carry a baked satin bevel and a warm drop shadow, so they read as
  physical cards; pills and buttons use a light-to-shade gradient fill.
* Softened ink (`#4A4A55`) rather than pure black.

The default *Classic* theme asset (`bluebubbles.attheme`) is repainted to this palette,
and the built-in defaults in `ThemeColors.java` match it, so a fresh install looks
correct before any theme is downloaded.

### Building

There is nothing to build locally. Every APK is produced by GitHub Actions
(`.github/workflows/android.yml`) on push to `master` and on `v*` tags:

* push to `master` → universal release APK as a build artifact
* push a `v*` tag → the same APK attached to a GitHub Release

No credentials are committed to this repository. The workflow reads them from
repository secrets (Settings → Secrets and variables → Actions):

| Secret | Purpose |
| --- | --- |
| `TELEGRAM_APP_ID` | `api_id` from [my.telegram.org](https://my.telegram.org) |
| `TELEGRAM_APP_HASH` | `api_hash` from my.telegram.org |
| `KEYSTORE_BASE64` | release keystore, `base64 -w0 your.jks` |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key password |
| `GOOGLE_SERVICES_JSON` | optional; `google-services.json` containing a client for `com.kaminari.gram`, enables FCM push |

Without `GOOGLE_SERVICES_JSON` the build still succeeds: the Firebase Gradle plugin is
skipped and push notifications fall back to the app's own MTProto connection, which
costs battery and latency but works.

To build on a workstation anyway, put the same keys in `local.properties`
(`TELEGRAM_APP_ID`, `TELEGRAM_APP_HASH`, `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`), drop `google-services.json` into
`TMessagesProj_App/`, then run `./gradlew :TMessagesProj_App:assembleAfatRelease`.
Requires JDK 17, NDK 21.4.7075529 and CMake 3.10.2.

### API and protocol documentation

Telegram API: https://core.telegram.org/api — MTProto: https://core.telegram.org/mtproto

Per Telegram's terms for third-party clients, KamiGram uses its own api_id, its own
name, and its own logo, and publishes its source as required by the licence.
