# AGENTS.md

Guidance for AI agents working in this repository.

## Project

MyTube — an Android app (Kotlin + Jetpack Compose) that wraps YouTube, YouTube
Music, Movies and Anime in WebViews, with ad blocking, background playback,
sleep timer, screen lock and Picture-in-Picture.

- Package / applicationId: `com.example.mytube`
- `minSdk 24`, `targetSdk 36`, `compileSdk 36`
- Gradle **9.7.1**, Android Gradle Plugin **9.4.1**, JDK 17+

## Layout

```
app/src/main/java/com/example/mytube/
  adblock/      hosts + scriptlet ad blocking
  browser/      WebView management, per-tab profiles
  data/         DataStore / Room persistence
  player/       MediaSession foreground playback service
  ui/           Compose screens, components, theme
  util/         helpers (PreferencesManager, etc.)
  viewmodel/    screen state holders
app/src/test/       JVM unit tests
app/src/androidTest/ instrumented tests
```

## Build & test

```bash
./gradlew assembleDebug          # debug APK
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (needs device/emulator)
./gradlew lint                   # Android lint
```

## Versioning

Version lives in `app/build.gradle.kts` (`defaultConfig`):

- `versionCode` — integer, must strictly increase on every release.
- `versionName` — semantic, e.g. `2.8.9`.

## Release process

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Update the download/install/verify references and changelog in `README.md`.
3. Build the signed APK:

   ```bash
   ./gradlew assembleRelease
   # app/build/outputs/apk/release/app-release.apk
   ```

4. Copy and rename to the release-asset convention at the repo root:

   ```bash
   cp app/build/outputs/apk/release/app-release.apk MyTube-v<version>-release.apk
   ```

5. Verify the signature (matches the historical cert):

   ```bash
   ~/Library/Android/sdk/build-tools/36.0.0/apksigner verify --print-certs \
     MyTube-v<version>-release.apk
   # expected SHA-256:
   # EB:37:C6:84:4E:4A:BC:DC:68:D7:63:8C:3D:9C:DA:78:
   # AE:C5:7A:1E:E1:30:1C:A8:31:4D:C9:F5:84:39:D8:F4
   ```

6. Commit the version bump + docs, tag, and push:

   ```bash
   git commit -am "chore(release): v<version> version bump"
   git tag -a v<version> -m "MyTube v<version>"
   git push origin main && git push origin v<version>
   ```

7. Publish the GitHub release with the APK attached:

   ```bash
   gh release create v<version> MyTube-v<version>-release.apk \
     --title "MyTube v<version>" --notes-file <notes.md>
   ```

## Signing

Signing config is read from `keystore.properties` (git-ignored):

```properties
storeFile=mytube-release.jks
storePassword=********
keyAlias=mytube
keyPassword=********
```

`keystore.properties`, `*.jks` and `*.keystore` must never be committed. The
release APK uses APK Signature Scheme v2.

## Conventions

- Commit messages follow Conventional Commits (`feat:`, `fix:`, `chore:`,
  `docs:`, `perf:`), with the area in parentheses when useful, e.g.
  `fix(adblock): ...`.
- Do not commit build outputs or APK assets; release APKs are uploaded to
  GitHub Releases only.
