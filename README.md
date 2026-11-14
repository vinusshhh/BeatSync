# BeatSync — Minimalist Music Streaming Platform

> Inspired by the raw, industrial, and hyper-minimalist design ethos of [OneTapLabs](https://onetaplabs.com/). Engineered in pure **Java** for Android with a high-performance **Python FastAPI** cloud streaming backend.

---

## 🌟 Overview & Key Features

BeatSync provides a Spotify-grade music streaming experience with a major improvement: **Every user account can upload their own tracks either Publicly or Privately**.

- **Stark, Minimalist Aesthetic**:
  - Monochromatic dark obsidian palette (`#0A0C10`, `#12151C`, `#171B24`) with hairline precision borders and subtle cyan/emerald accents.
  - Crisp typography, zero visual bloat, purposeful whitespace.
- **Account Authentication**:
  - Secure Email & Password Sign In and Sign Up with PBKDF2-HMAC-SHA256 password hashing & JWT token sessions.
- **Public vs. Private Upload Vault**:
  - **Public Tracks**: Available in the global catalog. Any user across any account can discover, search, stream, and playlist them.
  - **Private Tracks**: Strictly locked to the uploader's account. Completely hidden from other accounts and anonymous users.
  - File picker for `.mp3`, `.wav`, `.m4a`, and optional custom album art.
- **Interactive Music Player Engine**:
  - **Persistent Floating Mini Player**: Floats above bottom navigation with track title, artist, live mini progress bar, Play/Pause, and Skip Next.
  - **Full-Screen Now Playing View**: Large album artwork, title marquee, artist, status badge (`PUBLIC` / `PRIVATE`), timeline seekbar, Shuffle, Previous, Play/Pause, Next, Repeat mode, Like button, and Add to Playlist.
  - **Audio Streaming with HTTP 206 Range Requests**: Enables instant seeking, smooth playback buffering, and low latency.
- **Search & Discovery**:
  - Real-time search by song title, artist, or album.
  - Filter chips: `All Tracks`, `Public Catalog`, `My Private Vault`.
- **Custom Playlists**:
  - Create and manage personalized playlists.
  - Add and remove public tracks or your own private vault tracks.
  - One-click "Play All" queueing.

---

## 📂 Project Architecture

```
BeatSync/
├── app/                                 # Android Java Application
│   ├── build.gradle.kts                 # App dependencies & SDK configuration
│   └── src/main/
│       ├── AndroidManifest.xml          # Permissions & activity declarations
│       ├── java/com/beatsync/app/
│       │   ├── api/                     # ApiClient & network models (OkHttp + Gson)
│       │   ├── auth/                    # AuthManager (JWT storage & session management)
│       │   ├── models/                  # User, Track, Playlist data models
│       │   ├── player/                  # MusicPlayerManager (playback queue, seek, shuffle)
│       │   └── ui/                      # Activities, Fragments, and Adapters
│       │       ├── MainActivity.java
│       │       ├── SplashActivity.java
│       │       ├── LoginActivity.java
│       │       ├── RegisterActivity.java
│       │       ├── NowPlayingActivity.java
│       │       ├── PlaylistDetailActivity.java
│       │       ├── HomeFragment.java
│       │       ├── SearchFragment.java
│       │       ├── UploadFragment.java
│       │       ├── LibraryFragment.java
│       │       └── adapters/            # TrackAdapter, PlaylistAdapter
│       └── res/                         # Minimal layouts, drawables, colors & themes
├── server/                              # Cloud Streaming Backend (FastAPI + SQLite/Postgres)
│   ├── main.py                          # REST API & Range-request audio streaming endpoints
│   ├── auth.py                          # PBKDF2 hashing & JWT token verification
│   ├── database.py                      # Database schema & connection management
│   ├── seed.py                          # Pre-seeds demo soundscapes and accounts
│   ├── test_server.py                   # Automated test suite
│   ├── requirements.txt                 # Python dependencies
│   ├── Dockerfile                       # Container deployment
│   ├── render.yaml                      # 1-Click Render.com free deployment config
│   ├── Procfile                         # Cloud deployment process declaration
│   ├── run_server.bat                   # 1-Click local server launcher
│   └── HOSTING_GUIDE.md                 # Complete 100% free online deployment guide
├── build/outputs/apk/debug/app-debug.apk # Compiled ready-to-install Android APK
└── README.md
```

---

## 🚀 Running the Project

### 1. Start the Backend Server (Locally or Live Online)

#### Local Server:
Double click `server/run_server.bat` or run:
```powershell
cd server
python seed.py
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

#### Free Live Online Server (Render.com):
See [server/HOSTING_GUIDE.md](file:///c:/Users/VINUSH/Documents/Project/BeatSync/server/HOSTING_GUIDE.md) for 3-minute deployment instructions to get your live `https://beatsync-api.onrender.com` URL.

---

### 2. Run the Android App

- **Direct APK Installation**:
  The compiled debug APK is ready at:
  `app/build/outputs/apk/debug/app-debug.apk`
  Install onto an emulator or physical device with:
  ```powershell
  adb install -r app\build\outputs\apk\debug\app-debug.apk
  ```

- **Open in Android Studio**:
  1. Open Android Studio.
  2. Select **Open** and choose `c:\Users\VINUSH\Documents\Project\BeatSync`.
  3. Click **Run 'app'** (`Shift + F10`).

---

## 🔑 Demo Account Credentials

- **Email**: `demo@beatsync.com`
- **Password**: `password123`
*(Or create your own new account anytime from the Sign Up screen).*
