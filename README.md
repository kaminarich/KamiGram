# KamiGram

A Telegram client for Android with a skeuomorphic, light-pastel interface. Fork of
[Telegram for Android](https://github.com/DrKLO/Telegram), licensed under GPL-2.0-or-later
(see [LICENSE](LICENSE)).

- Application ID: `com.kaminari.gram`
- Name: short for *kaminari* (lightning); the launcher icon is a rounded bolt on a pastel plate
- Builds: produced exclusively by GitHub Actions; no local toolchain required

## Interface

The theme layer replaces upstream's flat white/blue palette with warm paper tones and
adds depth cues to bubbles and controls.

| Surface | Colour |
| --- | --- |
| Window, action bar, panels | `#FAF7F2` |
| Accent | `#8EC9E8` |
| Incoming bubble | `#FDFAF4` |
| Outgoing bubble | `#E3F2DF` |
| Chat wallpaper | `#F3EAD9` → `#E9DCE8` |
| Primary text | `#4A4A55` |

Depth is implemented in two places rather than as static assets:

- `Theme.MessageDrawable#getBackgroundDrawable` bakes a vertical satin bevel into the
  bubble nine-patch. Only the centre row stretches, so the top highlight and bottom lip
  hold at any bubble height. The shadow gradient is warmed and deepened.
- `Theme.SkeuomorphicShapeDrawable` backs `createRoundRectDrawable` and the round-rect
  selectors, giving pills and buttons a light-to-shade fill. Colours at or below alpha 200
  are passed through unshaded so ripples and masks are unaffected.

`assets/bluebubbles.attheme` (the default *Classic* theme) and the compiled defaults in
`ThemeColors.java` carry the same palette, so a first launch renders correctly before any
theme is fetched from the network.

Relevant paths:

```
TMessagesProj/src/main/assets/bluebubbles.attheme          default theme asset
TMessagesProj/src/main/java/org/telegram/ui/ActionBar/
    ThemeColors.java                                       compiled colour defaults
    Theme.java                                             bubble bevel, control fills
TMessagesProj/src/main/res/drawable/kami_icon_*.xml        adaptive icon layers
TMessagesProj/src/main/res/mipmap-*/ic_launcher*.png       legacy icons
```

## Repository layout

```
TMessagesProj/              library module: all sources, resources, JNI
TMessagesProj_App/          application module (Play-style build)
TMessagesProj_AppStandalone/application module (self-distributed build, .web suffix)
buildSrc/                   Gradle build logic
Tools/                      upstream helper scripts
```

Upstream's Huawei, HockeyApp and instrumentation modules are removed: they depend on
credentials this project does not distribute.

## Build

CI is the only supported build path. `.github/workflows/android.yml` runs on push to
`master` and on `v*` tags:

| Trigger | Result |
| --- | --- |
| push to `master` | universal release APK uploaded as artifact `KamiGram-APK` (30-day retention) |
| push tag `v*` | same APK attached to a GitHub Release |
| `workflow_dispatch` | manual run on any ref |

The workflow installs JDK 17, NDK 21.4.7075529 and CMake 3.10.2, runs
`:TMessagesProj_App:assembleAfatRelease`, then verifies the output with `aapt2 dump badging`
and `apksigner verify --print-certs` before uploading. Secrets are written to disk only for
the duration of the build and removed in an `always()` step. Expect roughly 50 minutes,
dominated by the native build.

### Required secrets

Set these under Settings → Secrets and variables → Actions. The build fails immediately if
credentials are missing, rather than producing an unusable APK.

| Secret | Value |
| --- | --- |
| `TELEGRAM_APP_ID` | `api_id` from [my.telegram.org](https://my.telegram.org) |
| `TELEGRAM_APP_HASH` | `api_hash` from my.telegram.org |
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `RELEASE_STORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key password |
| `GOOGLE_SERVICES_JSON` | optional; must contain a client for `com.kaminari.gram` |

Without `GOOGLE_SERVICES_JSON`, the `google-services` plugin is skipped and push
notifications fall back to the app's own MTProto connection. This works but increases
battery and wake-up latency compared to FCM.

Credentials are never committed. `release.keystore`, `*.jks`, and all `google-services.json`
files are in `.gitignore`; `BuildVars.APP_ID` and `APP_HASH` read from `BuildConfig`, which
Gradle populates from project properties.

### Local build

Optional, and not required to contribute. Requires JDK 17, Android SDK 35 with
Build-Tools 35.0.0, NDK 21.4.7075529 and CMake 3.10.2.

`local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
TELEGRAM_APP_ID=<api_id>
TELEGRAM_APP_HASH=<api_hash>
RELEASE_STORE_FILE=/abs/path/release.jks
RELEASE_STORE_PASSWORD=<password>
RELEASE_KEY_ALIAS=<alias>
RELEASE_KEY_PASSWORD=<password>
```

For push support, place `google-services.json` in `TMessagesProj_App/`. Then:

```bash
./gradlew :TMessagesProj_App:assembleAfatRelease
```

Or pass credentials per invocation instead of storing them:

```bash
./gradlew :TMessagesProj_App:assembleAfatRelease \
  -PTELEGRAM_APP_ID=<api_id> \
  -PTELEGRAM_APP_HASH=<api_hash> \
  -PRELEASE_STORE_FILE=/abs/path/release.jks \
  -PRELEASE_STORE_PASSWORD=<password> \
  -PRELEASE_KEY_ALIAS=<alias> \
  -PRELEASE_KEY_PASSWORD=<password>
```

Output: `TMessagesProj_App/build/outputs/apk/afat/release/app.apk`.

### Variants

Product flavours (`minApi` dimension), all four ABIs — `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`:

| Flavour | minSdk | Purpose | `abiVersionCode` |
| --- | --- | --- | --- |
| `afat` | 21 | universal APK; what CI ships | 9 |
| `bundleAfat` | 21 | App Bundle | 1 |
| `bundleAfat_SDK23` | 23 | App Bundle, SDK 23 baseline | 2 |

Build types: `debug` (`.beta` suffix), `standalone` (`.web` suffix, self-updating
distribution), `release`. A `variantFilter` keeps only `afat` for non-release types.

Effective version code is `APP_VERSION_CODE * 10 + abiVersionCode`, so
`APP_VERSION_CODE=1` yields `19` for an `afat` release. `APP_VERSION_CODE`,
`APP_VERSION_NAME` and `APP_PACKAGE` live in `gradle.properties`.

Targets: `compileSdk` 35, `targetSdk` 35, AGP 8.6.1, Gradle 8.7.

## Contributing

1. Fork, branch from `master`, and keep one logical change per pull request.
2. Prefer the theme layer (`ThemeColors.java`, `bluebubbles.attheme`, `Theme.java` drawable
   factories) over edits scattered across UI classes. Palette changes belong in both
   `ThemeColors.java` and the asset, or a fresh install and a themed install will disagree.
3. Match surrounding style: 4-space indent, no wildcard imports, comments only where intent
   is not obvious from the code.
4. Never commit `google-services.json`, keystores, `api_id`/`api_hash`, or `local.properties`.
   Run `git status` before committing; the ignore rules should already cover these.
5. Verify by pushing your branch and letting the workflow build it. Attach the run URL to
   the pull request. A green build plus the `aapt2`/`apksigner` output in the Analyze step
   is the minimum bar.
6. Commit messages: imperative subject under 72 characters, body explaining why. Note any
   upstream file you touched outside the theme layer, since those conflict on rebase.

Rebasing onto upstream Telegram is expected to conflict in `ThemeColors.java`, `Theme.java`,
the manifests and the Gradle files. Keep unrelated changes out of those files to limit the
blast radius.

## Reference

- Telegram API: https://core.telegram.org/api
- MTProto: https://core.telegram.org/mtproto
- Obtaining an `api_id`: https://core.telegram.org/api/obtaining_api_id

Per Telegram's terms for third-party clients, KamiGram ships its own `api_id`, name and
logo, and publishes its source as the licence requires.
