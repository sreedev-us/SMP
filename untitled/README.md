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
- Android packages `platforms;android-36`, `build-tools;36.0.0`, `platform-tools`, `extras;android;m2repository`, and `extras;google;m2repository`

Build an Android package:

```powershell
./mvnw -Pandroid -DskipTests clean gluonfx:build gluonfx:package
```

This produces an Android-native package using the same JavaFX codebase.

## Web Access

Harmony Pro also exposes a browser-based website while the app is hosting a sync session.

1. Start the app normally on desktop or Android-hosted builds.
2. Press `Start Sync` / `Host Session`.
3. Open the shared local link on the same network, or the generated public tunnel link on any device.
4. Use the website home page, then open `/player` for the live browser player.

This keeps the current JavaFX configurations intact while adding a website entry point for phones, tablets, and laptops.

## Standalone Web App

There is now a separate standalone web application that does not depend on the desktop player being open.

Start it with:

```powershell
./mvnw -DskipTests compile exec:java -Dexec.mainClass=com.musicplayer.StandaloneWebServer
```

Or use a custom port:

```powershell
./mvnw -DskipTests compile exec:java -Dexec.mainClass=com.musicplayer.StandaloneWebServer -Dexec.args="8090"
```

Then open:

- `http://localhost:8090/`
- `http://localhost:8090/player`

The standalone web app includes:

- browser search
- queue management
- play / pause / next / previous
- related songs / auto radio
- live lyrics
- direct browser playback

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
