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
- Android packages `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`, `extras;android;m2repository`, and `extras;google;m2repository`

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

## Cloud Server Deployment

The standalone web app can now be deployed to a real cloud server without relying on your laptop.

Files included:

- `Dockerfile`
- `docker-compose.yml`
- `deploy/nginx.conf`
- `deploy/harmony-web.service`
- `deploy/deploy-cloud.sh`

### Fastest path on a fresh Ubuntu VPS

```bash
git clone https://github.com/sreedev-us/SMP.git
cd SMP/untitled
chmod +x deploy/deploy-cloud.sh
./deploy/deploy-cloud.sh
```

This will:

- install Docker and Nginx
- clone or update the repo under `/opt/harmony-pro`
- build the standalone web app container
- start it on port `8090`
- install an Nginx reverse proxy config

### Manual container run

```bash
docker compose up -d --build
```

Then the app will be available on:

- `http://your-server-ip:8090/`

### Behind Nginx with your domain

1. Edit `deploy/nginx.conf`
2. Replace `your-domain.com` with your real domain
3. Reload Nginx
4. Add HTTPS with Certbot

Example:

```bash
sudo cp deploy/nginx.conf /etc/nginx/sites-available/harmony-web
sudo ln -s /etc/nginx/sites-available/harmony-web /etc/nginx/sites-enabled/harmony-web
sudo nginx -t
sudo systemctl reload nginx
sudo certbot --nginx -d your-domain.com
```

## Google Cloud Run

You can also deploy the standalone website to Google Cloud Run.

Files included:

- `Dockerfile`
- `deploy/deploy-cloudrun.sh`
- `deploy/deploy-cloudrun.ps1`

Quick deploy:

```bash
gcloud auth login
gcloud config set project YOUR_PROJECT_ID
cd untitled
chmod +x deploy/deploy-cloudrun.sh
./deploy/deploy-cloudrun.sh YOUR_PROJECT_ID us-central1 harmony-web
```

This deploys the site as a public Cloud Run service on port `8090`.

Windows PowerShell:

```powershell
cd untitled
.\deploy\deploy-cloudrun.ps1 -ProjectId YOUR_PROJECT_ID -Region us-central1 -ServiceName harmony-web
```

Recommended notes:

- Cloud Run is best for lightweight personal use of this web app.
- The current player state is in memory, so queue/playback state can reset on instance restart.
- `deploy-cloudrun.sh` uses `--concurrency 1` and `--max-instances 1` to keep behavior more predictable.

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
