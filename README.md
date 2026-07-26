# Unchained for TorBox

An Android client for adding magnet links and `.torrent` files to [TorBox](https://torbox.app), browsing the files after they are cached, and downloading them from inside the app.

This is a TorBox-focused fork of [Unchained for Android](https://github.com/LivingWithHippos/unchained-android). Its interface and navigation are based on Unchained; the torrent flow was adapted for TorBox.

## What it does

- Appears in Android's app chooser for `magnet:` links.
- Sends magnets directly to TorBox.
- Uploads `.torrent` files from the app or through Android's Open with/Share actions.
- Shows TorBox torrent status and file lists.
- Downloads cached or completed files through TorBox's short-lived download URLs.
- Stores the TorBox API key locally using Android Keystore encryption.

Use the app only for content you are authorized to access.

## Install on Android

The GitHub Actions workflow builds an installable debug APK on every push to `master`.

1. Open the [Build TorBox APK workflow](https://github.com/ArunTS96/unchained-torbox/actions/workflows/build-torbox.yml).
2. Open a successful run and download the `unchained-torbox-debug` artifact.
3. Extract the artifact and install the APK on the phone. Android may require enabling **Install unknown apps** for the browser or file manager used to open it.
4. Launch **Unchained for TorBox** and enter your TorBox API key in the app.

The Actions artifact is debug-signed and intended for personal testing. It is not a Play Store release and should not be used as a release signing key.

## Use the app

### Add a magnet link

1. Tap a magnet link in a browser or another app.
2. Select **Unchained for TorBox** in Android's chooser.
3. The app submits the magnet to TorBox and shows the torrent in the Torrents section.
4. Wait for TorBox to report the torrent as cached, completed, or ready, then refresh the list.
5. Open the torrent, choose a file, and tap **Download**.

The download is resolved inside the app. The TorBox API key is not placed in the saved download reference or passed to the Android download worker.

### Upload a torrent file

Use **New Download** / **Upload Torrent** in the app, or choose **Open with** or **Share** from a file manager for a `.torrent` file. The file is uploaded directly to TorBox.

## Security and privacy

- Do not paste your TorBox API key into GitHub, an issue, a chat, or this README.
- The key is encrypted with Android Keystore-backed AES/GCM storage and is not committed to the repository.
- The implemented TorBox flow sends the key to TorBox requests only; it does not forward it to Torrentio or an app-owned relay.
- Request and response body logging is disabled.
- The app disables Android backup for its application data.
- The app uses an internal, non-shareable `torbox://` reference until it resolves a file through TorBox.

No application can provide an absolute security guarantee. Install APKs only from a build you trust, verify the artifact source, and revoke or rotate your TorBox key if you ever suspect it was exposed.

## Build locally

Requirements:

- Windows, macOS, or Linux
- Android SDK for API 37
- JDK 17 recommended

From the repository root:

```powershell
cd app
.\gradlew.bat :app:assembleDebug --no-daemon
```

On macOS or Linux:

```bash
cd app
./gradlew :app:assembleDebug --no-daemon
```

The debug APK is written to:

```text
app/app/build/outputs/apk/debug/debug-1.0.0-dev.apk
```

The workflow uses Temurin JDK 17 and runs the same debug build through [`.github/workflows/build-torbox.yml`](.github/workflows/build-torbox.yml). A release build requires your own signing configuration; never commit a keystore or signing password.

## Project layout

- [`TorboxKeyStore.kt`](app/app/src/main/java/com/github/livingwithhippos/unchained/data/local/TorboxKeyStore.kt) — encrypted local key storage.
- [`TorboxClient.kt`](app/app/src/main/java/com/github/livingwithhippos/unchained/data/remote/TorboxClient.kt) — TorBox API client for magnets, torrent uploads, status, files, and download URL resolution.
- [`TorrentsRepository.kt`](app/app/src/main/java/com/github/livingwithhippos/unchained/data/repository/TorrentsRepository.kt) — app-facing torrent repository backed by TorBox.
- [`DownloadWorker.kt`](app/app/src/main/java/com/github/livingwithhippos/unchained/utilities/download/DownloadWorker.kt) — in-app file download worker.

## Troubleshooting

### The app does not appear for a magnet link

Install the APK fully, then try the link's **Share** or **Open with** action. If Android has remembered another default app, clear that app's supported-link defaults in Android settings.

### The API key is rejected

Check that the key was copied without spaces or line breaks and that it is a TorBox API key. Do not post the key when asking for help; revoke it and create a replacement if it may have been exposed.

### The torrent is still downloading

The app cannot make TorBox cache a torrent faster. Refresh the torrent after TorBox changes its status to cached, completed, or ready.

### Downloads do not start

Grant notification permission if Android asks, check the selected download folder, and retry after refreshing the torrent. TorBox download URLs are short-lived and are regenerated when needed.
