# BeatSync - Live Cloud Hosting Guide (100% Free)

This guide walks you step-by-step through deploying the **BeatSync Music Streaming Server** online for **free** so anyone worldwide can stream, register, upload, and listen to music from your Android app.

---

## Option 1: Render.com (Recommended — 3 Minutes, Zero Cost)

Render provides a completely free cloud tier with automatic HTTPS certificates, HTTP/2 streaming, and Git-based continuous deployment.

### Step-by-Step Instructions:

1. **Push your BeatSync project to GitHub**:
   - Initialize git (if not already done):
     ```bash
     git init
     git add .
     git commit -m "Initial BeatSync commit"
     ```
   - Create a new repository on [GitHub](https://github.com/new) (public or private).
   - Push your code to GitHub:
     ```bash
     git remote add origin https://github.com/<your-username>/BeatSync.git
     git branch -M main
     git push -u origin main
     ```

2. **Create a Free Web Service on Render**:
   - Go to [render.com](https://render.com/) and sign up or log in (free).
   - In your Render Dashboard, click **New +** in the top right and select **Web Service**.
   - Select **Build and deploy from a Git repository** and connect your GitHub account.
   - Choose your **BeatSync** repository.

3. **Configure Service Settings**:
   - **Name**: `beatsync-api` (or any unique name you prefer)
   - **Region**: Choose the closest region (e.g. Frankfurt, Oregon, Singapore)
   - **Branch**: `main`
   - **Root Directory**: `server`
   - **Runtime**: `Python 3`
   - **Build Command**:
     ```bash
     pip install -r requirements.txt && python seed.py
     ```
   - **Start Command**:
     ```bash
     uvicorn main:app --host 0.0.0.0 --port $PORT
     ```
   - **Instance Type**: Select **Free** ($0/month).

4. **Deploy**:
   - Click **Deploy Web Service** at the bottom.
   - Wait 1–2 minutes while Render installs dependencies and seeds the database.
   - Once it says **Live**, copy your live HTTPS URL (e.g. `https://beatsync-api.onrender.com`).

5. **Connect Android App to Your Live Server**:
   - Open the **BeatSync Android App**.
   - On the Login screen or Library screen, tap the **Settings** (gear) icon in the top right.
   - In the "Server Endpoint" field, paste your live URL (e.g., `https://beatsync-api.onrender.com`).
   - Tap **Save & Connect**.
   - You are now streaming live from the cloud!

---

## Option 2: Koyeb (Free Docker Deployment)

Koyeb offers free serverless container hosting worldwide.

1. Go to [koyeb.com](https://www.koyeb.com/) and sign in.
2. Click **Create App**.
3. Choose **GitHub** as the deployment method and select your `BeatSync` repository.
4. Set the builder to **Dockerfile** (pointing to `server/Dockerfile`).
5. Choose the **Free** nano instance.
6. Click **Deploy**. Your live URL will be generated instantly (e.g., `https://beatsync-<user>.koyeb.app`).

---

## Option 3: Local Development (Instant Testing)

To run the server on your local machine for Android Studio or physical phone testing:

1. Double-click `server/run_server.bat` or run:
   ```powershell
   cd server
   python seed.py
   python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
   ```
2. In Android Emulator:
   - The app connects to `http://10.0.2.2:8000` by default.
3. On a Physical Android Phone connected to your Wi-Fi:
   - Find your PC's local IP address (`ipconfig`, e.g. `192.168.1.15`).
   - In the BeatSync app Settings dialog, enter `http://192.168.1.15:8000` and tap **Save & Connect**.

---

## Pre-Seeded Demo Credentials

The database automatically seeds with a demo account and royalty-free minimal soundscapes:
- **Email**: `demo@beatsync.com`
- **Password**: `password123`
