# Hucks Show — Firestick app

One place for your IPTV playlists (M3U or Xtream login) and your streaming apps.

- **Live TV / Movies / Series** from any number of M3U links or Xtream logins
- **Search** across your playlists *and* TMDB, which shows which installed app (Netflix, iPlayer, Prime Video…) has a title, with a button to open it
- **My Apps**: pin any installed app (Netflix, iPlayer, MovieBox, anything) to the home screen

---

## 1. Build the APK (free, on GitHub)

1. Make a free account at github.com if you don't have one.
2. Create a **new repository** (e.g. `hucksshow`) and set it to **Public**, so the Firestick can download the APK without logging in. Your playlist logins are never in the code; they stay on the Firestick.
3. Click **"uploading an existing file"**, drag in *everything inside* this folder (including the hidden `.github` folder), and commit.
   - Tip: on Windows/Mac the `.github` folder may be hidden. If it doesn't upload, create the file by hand: **Add file → Create new file**, name it `.github/workflows/build.yml`, and paste in the contents.
4. Add your TMDB key: **Settings → Secrets and variables → Actions → New repository secret**
   - Name: `TMDB_KEY`
   - Value: your TMDB API key (the short one) or the Read Access Token
5. Go to the **Actions** tab and wait about 5 minutes for "Build Hucks Show APK" to go green. If it ran before you added the secret, click it and choose **Re-run jobs**.
6. The APK is now at:
   `https://github.com/YOUR-USERNAME/hucksshow/releases/latest/download/HucksShow.apk`

Every time you change a file, it rebuilds, and the link always points to the newest version.

## 2. Install on the Firestick

1. Firestick: **Settings → My Fire TV → Developer options**
   - If you don't see Developer options: **Settings → My Fire TV → About**, click the device name 7 times.
   - Turn on **Install unknown apps** for **Downloader**.
2. Install **Downloader** (by AFTVnews) from the Amazon app store.
3. Open Downloader, type the link from step 6 above, and press Go → **Install**.
4. Hucks Show appears under **Your Apps & Channels**. Hold the menu button on it to move it to the front.

Updates: repeat step 3. It installs over the old version and keeps your playlists.

## 3. First use

1. **Playlists → + Add Xtream login** (server, username, password) or **+ Add M3U link**.
2. **My Apps**: pin Netflix, iPlayer, etc.
3. **Search** anything. You'll see where it's streaming in your country, plus matches from your playlists.
4. Settings: change the country used for "where to watch" (GB by default; use MT for Malta).

## Notes

- Netflix, iPlayer etc. don't let other apps search inside them or deep-link to a title, so "Open Netflix" opens the app and you search for the title there.
- Live Xtream channels use `.ts` streams. If a provider only works with HLS, tell Claude and it can add an option.
- Big playlists load once per session; use **Refresh** on the Playlists screen to reload.
- Building on your own PC instead: open this folder in Android Studio and choose **Build → Build APK(s)**.
