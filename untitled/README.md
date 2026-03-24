# Harmony Pro Cross-Platform Build

This project now runs from a single JavaFX app flow so the same codebase can target both Windows and Android.

## Windows

Run the desktop app:

```powershell
./mvnw javafx:run
```

Build the desktop runtime image:

```powershell
./mvnw -Pwindows clean javafx:jlink
```

Build a standalone Windows installer:

```powershell
./mvnw -Pwindows clean package jpackage:jpackage
```

The installer output is written to `target/installer`.

## Android

Required:

- JDK 17
- Android SDK with `ANDROID_HOME` or `ANDROID_SDK_ROOT`
- GraalVM with `GRAALVM_HOME`
- Gluon Substrate prerequisites installed

Build an Android package:

```powershell
./mvnw -Pandroid -DskipTests clean gluonfx:build gluonfx:package
```

This produces an Android-native package using the same JavaFX codebase.

## GitHub Actions

If Android packaging is blocked locally on Windows, use the workflow in `.github/workflows/android.yml`.

1. Push the project to GitHub.
2. Open the repository on GitHub.
3. Go to `Actions`.
4. Run `Android Build`.
5. Download the generated APK artifact from the workflow run.

The workflow builds on Ubuntu with GraalVM 21 and uploads the APK or AAB together with Gluon logs.

## Notes

- The main player now stays inside one primary window, which is safer for Android than opening extra desktop stages.
- Settings are rendered inside the app as an overlay, so they work on mobile and desktop from the same UI flow.
- Google sign-in still depends on valid OAuth credentials in `src/main/resources/com/musicplayer/credentials.json`.
