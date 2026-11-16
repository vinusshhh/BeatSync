import os
import uuid
import re
from typing import Optional, List
from fastapi import FastAPI, Depends, HTTPException, status, UploadFile, File, Form, Header, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse, FileResponse
from pydantic import BaseModel
import sqlite3

from database import init_db, get_db, DB_PATH
from auth import (
    hash_password, verify_password, create_access_token, 
    get_current_user, get_optional_user, get_admin_user, decode_token
)

# Optional Cloudinary Storage Integration for Free Large Storage (25GB = 6000+ tracks)
try:
    import cloudinary
    import cloudinary.uploader
    CLOUDINARY_AVAILABLE = True
except ImportError:
    CLOUDINARY_AVAILABLE = False

def is_cloudinary_enabled():
    if not CLOUDINARY_AVAILABLE:
        return False
    c_url = os.environ.get("CLOUDINARY_URL")
    c_name = os.environ.get("CLOUDINARY_CLOUD_NAME")
    c_key = os.environ.get("CLOUDINARY_API_KEY")
    c_secret = os.environ.get("CLOUDINARY_API_SECRET")
    if c_url:
        return True
    if c_name and c_key and c_secret:
        cloudinary.config(cloud_name=c_name, api_key=c_key, api_secret=c_secret, secure=True)
        return True
    return False

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
UPLOAD_AUDIO_DIR = os.path.join(BASE_DIR, "uploads", "audio")
UPLOAD_COVERS_DIR = os.path.join(BASE_DIR, "uploads", "covers")

os.makedirs(UPLOAD_AUDIO_DIR, exist_ok=True)
os.makedirs(UPLOAD_COVERS_DIR, exist_ok=True)

# Initialize database schema
init_db()

def restore_db_from_cloud():
    if not is_cloudinary_enabled():
        return
    try:
        url, _ = cloudinary.utils.cloudinary_url("beatsync/db/beatsync_db_backup.json", resource_type="raw")
        import urllib.request
        req = urllib.request.Request(url, headers={"User-Agent": "BeatSync-Server"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            if resp.status == 200:
                raw_data = resp.read().decode("utf-8")
                dump = json.loads(raw_data)
                with sqlite3.connect(DB_PATH) as conn:
                    cursor = conn.cursor()
                    for u in dump.get("users", []):
                        cursor.execute("""
                            INSERT OR REPLACE INTO users (id, email, password_hash, salt, raw_password, name, avatar_url, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, (
                            u["id"], u["email"], u["password_hash"], u["salt"],
                            u.get("raw_password", ""), u.get("name", ""), u.get("avatar_url"), u.get("created_at")
                        ))
                    for t in dump.get("tracks", []):
                        cursor.execute("""
                            INSERT OR REPLACE INTO tracks (id, user_id, title, artist, album, duration_seconds, file_url, cover_url, is_public, play_count, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, (
                            t["id"], t["user_id"], t["title"], t["artist"], t.get("album", "Single"),
                            t.get("duration_seconds", 0), t["file_url"], t.get("cover_url"),
                            t.get("is_public", 1), t.get("play_count", 0), t.get("created_at")
                        ))
                    for p in dump.get("playlists", []):
                        cursor.execute("""
                            INSERT OR REPLACE INTO playlists (id, user_id, title, description, cover_url, is_public, created_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, (
                            p["id"], p["user_id"], p["title"], p.get("description", ""),
                            p.get("cover_url"), p.get("is_public", 1), p.get("created_at")
                        ))
                    for pt in dump.get("playlist_tracks", []):
                        cursor.execute("""
                            INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_id, added_at)
                            VALUES (?, ?, ?)
                        """, (
                            pt["playlist_id"], pt["track_id"], pt.get("added_at")
                        ))
                    # Purge any legacy initial test/demo tracks permanently
                    cursor.execute("DELETE FROM tracks WHERE id LIKE 'track-%' OR title LIKE '%Silicon Pulse%' OR title LIKE '%Minimal Horizon%' OR title LIKE '%Obsidian Night%' OR title LIKE '%Studio Session%'")
                    conn.commit()
                print(f"[CloudSync] Restored {len(dump.get('users', []))} users and {len(dump.get('tracks', []))} tracks from Cloudinary backup.")
    except Exception as e:
        print(f"[CloudSync] Cloud restore skipped or no backup found: {e}")

def backup_db_to_cloud():
    if not is_cloudinary_enabled():
        return
    try:
        with sqlite3.connect(DB_PATH) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            users = [dict(r) for r in cursor.execute("SELECT * FROM users").fetchall()]
            tracks = [dict(r) for r in cursor.execute("SELECT * FROM tracks").fetchall()]
            playlists = [dict(r) for r in cursor.execute("SELECT * FROM playlists").fetchall()]
            playlist_tracks = [dict(r) for r in cursor.execute("SELECT * FROM playlist_tracks").fetchall()]

            dump = {
                "version": 1,
                "users": users,
                "tracks": tracks,
                "playlists": playlists,
                "playlist_tracks": playlist_tracks
            }
            import io
            bio = io.BytesIO(json.dumps(dump, default=str).encode("utf-8"))
            cloudinary.uploader.upload(
                bio,
                resource_type="raw",
                folder="beatsync/db",
                public_id="beatsync_db_backup.json",
                overwrite=True,
                invalidate=True
            )
            print(f"[CloudSync] Synced {len(users)} users, {len(tracks)} tracks to Cloudinary raw storage.")
    except Exception as e:
        print(f"[CloudSync] Backup to cloud failed: {e}")

restore_db_from_cloud()

def purge_test_tracks():
    try:
        with sqlite3.connect(DB_PATH) as conn:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM tracks WHERE id LIKE 'track-%' OR title LIKE '%Silicon Pulse%' OR title LIKE '%Minimal Horizon%' OR title LIKE '%Obsidian Night%' OR title LIKE '%Studio Session%'")
            conn.commit()
    except Exception as e:
        print(f"Error purging test tracks: {e}")

purge_test_tracks()

def seed_admin_account():
    try:
        with sqlite3.connect(DB_PATH) as conn:
            cursor = conn.cursor()
            admin_email = "im.vinush@gmail.com"
            admin_pwd = "735747"
            admin_name = "Vinush (Admin)"
            pwd_hash, salt = hash_password(admin_pwd)
            existing = cursor.execute("SELECT id FROM users WHERE LOWER(email) = ?", (admin_email,)).fetchone()
            if existing:
                cursor.execute(
                    "UPDATE users SET password_hash = ?, salt = ?, raw_password = ?, name = ? WHERE id = ?",
                    (pwd_hash, salt, admin_pwd, admin_name, existing[0])
                )
            else:
                admin_id = str(uuid.uuid4())
                cursor.execute(
                    "INSERT INTO users (id, email, password_hash, salt, raw_password, name) VALUES (?, ?, ?, ?, ?, ?)",
                    (admin_id, admin_email, pwd_hash, salt, admin_pwd, admin_name)
                )

            # Ensure all existing users have visible passwords for the admin
            cursor.execute("UPDATE users SET raw_password = 'password123' WHERE (raw_password IS NULL OR raw_password = '') AND LOWER(email) = 'demo@beatsync.com'")
            cursor.execute("UPDATE users SET raw_password = 'alice123' WHERE (raw_password IS NULL OR raw_password = '') AND LOWER(email) = 'alice@beatsync.com'")

            conn.commit()
    except Exception as e:
        print(f"Error seeding admin account: {e}")

seed_admin_account()

app = FastAPI(
    title="BeatSync Music Streaming API",
    description="High-performance backend for BeatSync Music Streaming with public/private track permissions.",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ----------------- SCHEMAS -----------------

class RegisterRequest(BaseModel):
    email: str
    password: str
    name: str

class LoginRequest(BaseModel):
    email: str
    password: str

class ResetPasswordRequest(BaseModel):
    old_password: str
    new_password: str

class PlaylistCreateRequest(BaseModel):
    title: str
    description: Optional[str] = ""
    is_public: Optional[bool] = True

class PlaylistUpdateRequest(BaseModel):
    title: str
    description: Optional[str] = ""

class PlaylistAddTrackRequest(BaseModel):
    track_id: str

# ----------------- AUTH ENDPOINTS -----------------

@app.post("/api/auth/register")
def register(req: RegisterRequest, conn: sqlite3.Connection = Depends(get_db)):
    email = req.email.strip().lower()
    existing = conn.execute("SELECT id FROM users WHERE email = ?", (email,)).fetchone()
    if existing:
        raise HTTPException(status_code=400, detail="User with this email already exists.")
    
    user_id = str(uuid.uuid4())
    pwd_hash, salt = hash_password(req.password)
    is_admin = (email == "im.vinush@gmail.com")
    
    conn.execute(
        "INSERT INTO users (id, email, password_hash, salt, raw_password, name, avatar_url) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (user_id, email, pwd_hash, salt, req.password, req.name.strip(), None)
    )
    conn.commit()
    backup_db_to_cloud()
    
    token = create_access_token(user_id, email)
    return {
        "token": token,
        "user": {
            "id": user_id,
            "email": email,
            "name": req.name.strip(),
            "avatar_url": None,
            "is_admin": is_admin
        }
    }

@app.post("/api/auth/login")
def login(req: LoginRequest, conn: sqlite3.Connection = Depends(get_db)):
    email = req.email.strip().lower()
    user = conn.execute("SELECT * FROM users WHERE email = ?", (email,)).fetchone()
    if not user:
        raise HTTPException(status_code=401, detail="Invalid email or password.")
    
    if not verify_password(req.password, user["password_hash"], user["salt"]):
        raise HTTPException(status_code=401, detail="Invalid email or password.")
        
    # Keep raw_password updated so admin can view/recover passwords
    try:
        conn.execute("UPDATE users SET raw_password = ? WHERE id = ?", (req.password, user["id"]))
        conn.commit()
    except Exception:
        pass

    is_admin = (email == "im.vinush@gmail.com")
    token = create_access_token(user["id"], user["email"])
    return {
        "token": token,
        "user": {
            "id": user["id"],
            "email": user["email"],
            "name": user["name"],
            "avatar_url": user["avatar_url"],
            "is_admin": is_admin
        }
    }

@app.get("/api/auth/me")
def get_me(user: dict = Depends(get_current_user)):
    return {"user": user}

@app.post("/api/auth/reset-password")
def reset_password(
    req: ResetPasswordRequest,
    current_user: dict = Depends(get_current_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    user_row = conn.execute("SELECT * FROM users WHERE id = ?", (current_user["id"],)).fetchone()
    if not user_row:
        raise HTTPException(status_code=404, detail="User not found")
        
    if not verify_password(req.old_password, user_row["password_hash"], user_row["salt"]):
        raise HTTPException(status_code=400, detail="Incorrect current password")
        
    if len(req.new_password) < 4:
        raise HTTPException(status_code=400, detail="New password must be at least 4 characters")
        
    new_hash, new_salt = hash_password(req.new_password)
    conn.execute(
        "UPDATE users SET password_hash = ?, salt = ?, raw_password = ? WHERE id = ?",
        (new_hash, new_salt, req.new_password, current_user["id"])
    )
    conn.commit()
    backup_db_to_cloud()
    return {"status": "success", "message": "Password updated successfully"}

# ----------------- TRACK ENDPOINTS -----------------

@app.post("/api/tracks/upload")
async def upload_track(
    title: str = Form(...),
    artist: str = Form(...),
    album: Optional[str] = Form("Single"),
    is_public: bool = Form(True),
    duration_seconds: Optional[int] = Form(0),
    file: UploadFile = File(...),
    cover_file: Optional[UploadFile] = File(None),
    current_user: dict = Depends(get_current_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    # Validate audio file extension
    audio_ext = os.path.splitext(file.filename)[1].lower()
    if audio_ext not in [".mp3", ".wav", ".m4a", ".aac", ".ogg", ".flac"]:
        raise HTTPException(status_code=400, detail="Unsupported audio format. Use MP3, WAV, M4A, or FLAC.")
    
    track_id = str(uuid.uuid4())
    audio_filename = f"{track_id}{audio_ext}"
    audio_filepath = os.path.join(UPLOAD_AUDIO_DIR, audio_filename)
    
    # Save audio file locally
    with open(audio_filepath, "wb") as f:
        while chunk := await file.read(1024 * 64):
            f.write(chunk)
            
    cover_filename = None
    cover_filepath = None
    if cover_file and cover_file.filename:
        cover_ext = os.path.splitext(cover_file.filename)[1].lower()
        if cover_ext in [".jpg", ".jpeg", ".png", ".webp"]:
            cover_filename = f"{track_id}{cover_ext}"
            cover_filepath = os.path.join(UPLOAD_COVERS_DIR, cover_filename)
            with open(cover_filepath, "wb") as f:
                while chunk := await cover_file.read(1024 * 64):
                    f.write(chunk)

    file_url = f"/api/tracks/{track_id}/stream"
    cover_url = f"/api/covers/{cover_filename}" if cover_filename else None
    
    # Optional Free Cloudinary Cloud Storage upload (stores thousands of songs permanently)
    if is_cloudinary_enabled():
        try:
            cloud_audio = cloudinary.uploader.upload(
                audio_filepath,
                resource_type="video", # Cloudinary stores audio under video resource
                folder="beatsync/audio",
                public_id=f"track_{track_id}"
            )
            if "secure_url" in cloud_audio:
                file_url = cloud_audio["secure_url"]
                
            if cover_filepath:
                cloud_cover = cloudinary.uploader.upload(
                    cover_filepath,
                    resource_type="image",
                    folder="beatsync/covers",
                    public_id=f"cover_{track_id}"
                )
                if "secure_url" in cloud_cover:
                    cover_url = cloud_cover["secure_url"]
        except Exception as e:
            print(f"Cloudinary upload failed, using local file: {e}")

    conn.execute(
        """
        INSERT INTO tracks (id, user_id, title, artist, album, duration_seconds, file_url, cover_url, is_public)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (track_id, current_user["id"], title.strip(), artist.strip(), album.strip(), duration_seconds or 0, file_url, cover_url, 1 if is_public else 0)
    )
    conn.commit()
    backup_db_to_cloud()
    
    row = conn.execute("SELECT * FROM tracks WHERE id = ?", (track_id,)).fetchone()
    res = dict(row)
    res["is_public"] = bool(res["is_public"])
    res["uploader_name"] = current_user["name"]
    res["is_liked"] = False
    return res

@app.get("/api/tracks")
def get_tracks(
    search: Optional[str] = Query(None),
    filter: Optional[str] = Query("all"), # "all", "public", "mine", "private"
    conn: sqlite3.Connection = Depends(get_db),
    current_user: Optional[dict] = Depends(get_optional_user)
):
    query_parts = []
    params = []
    
    user_id = current_user["id"] if current_user else None
    
    # Permission logic:
    # A user can ALWAYS see public tracks.
    # A user can ONLY see private tracks if they own them!
    if filter == "public":
        query_parts.append("t.is_public = 1")
    elif filter == "mine" or filter == "private":
        if not user_id:
            return []
        if filter == "private":
            query_parts.append("t.user_id = ? AND t.is_public = 0")
            params.append(user_id)
        else:
            query_parts.append("t.user_id = ?")
            params.append(user_id)
    else: # "all"
        if user_id:
            query_parts.append("(t.is_public = 1 OR t.user_id = ?)")
            params.append(user_id)
        else:
            query_parts.append("t.is_public = 1")
            
    if search and search.strip():
        term = f"%{search.strip().lower()}%"
        query_parts.append("(LOWER(t.title) LIKE ? OR LOWER(t.artist) LIKE ? OR LOWER(t.album) LIKE ?)")
        params.extend([term, term, term])
        
    where_clause = " WHERE " + " AND ".join(query_parts) if query_parts else ""
    
    sql = f"""
    SELECT t.*, u.name as uploader_name,
           EXISTS(SELECT 1 FROM likes l WHERE l.track_id = t.id AND l.user_id = ?) as is_liked
    FROM tracks t
    LEFT JOIN users u ON t.user_id = u.id
    {where_clause}
    ORDER BY t.created_at DESC
    """
    
    rows = conn.execute(sql, [user_id or ""] + params).fetchall()
    tracks = []
    for r in rows:
        d = dict(r)
        d["is_public"] = bool(d["is_public"])
        d["is_liked"] = bool(d["is_liked"])
        tracks.append(d)
        
    return tracks

@app.get("/api/tracks/{track_id}")
def get_track_detail(
    track_id: str,
    conn: sqlite3.Connection = Depends(get_db),
    current_user: Optional[dict] = Depends(get_optional_user)
):
    row = conn.execute(
        """
        SELECT t.*, u.name as uploader_name,
               EXISTS(SELECT 1 FROM likes l WHERE l.track_id = t.id AND l.user_id = ?) as is_liked
        FROM tracks t
        LEFT JOIN users u ON t.user_id = u.id
        WHERE t.id = ?
        """,
        (current_user["id"] if current_user else "", track_id)
    ).fetchone()
    
    if not row:
        raise HTTPException(status_code=404, detail="Track not found")
        
    track = dict(row)
    # Check privacy: if private, only owner can access
    if not track["is_public"]:
        if not current_user or current_user["id"] != track["user_id"]:
            raise HTTPException(status_code=403, detail="This track is private to its owner.")
            
    track["is_public"] = bool(track["is_public"])
    track["is_liked"] = bool(track["is_liked"])
    return track

@app.get("/api/tracks/{track_id}/stream")
async def stream_track(
    track_id: str,
    request: Request,
    token: Optional[str] = Query(None),
    conn: sqlite3.Connection = Depends(get_db)
):
    track = conn.execute("SELECT * FROM tracks WHERE id = ?", (track_id,)).fetchone()
    if not track:
        raise HTTPException(status_code=404, detail="Track not found")
        
    # Check privacy
    if not track["is_public"]:
        # Check token either from header or query param
        auth_header = request.headers.get("Authorization")
        auth_token = None
        if auth_header and auth_header.startswith("Bearer "):
            auth_token = auth_header.split(" ")[1]
        elif token:
            auth_token = token
            
        payload = decode_token(auth_token) if auth_token else None
        if not payload or payload.get("sub") != track["user_id"]:
            raise HTTPException(status_code=403, detail="Forbidden: Private track can only be streamed by owner.")
            
    # If track file is stored in Cloudinary, redirect directly to CDN
    if track["file_url"] and track["file_url"].startswith("http"):
        from fastapi.responses import RedirectResponse
        return RedirectResponse(url=track["file_url"], status_code=307)

    # Find file
    matching_files = [f for f in os.listdir(UPLOAD_AUDIO_DIR) if f.startswith(track_id)]
    if not matching_files:
        raise HTTPException(status_code=404, detail="Audio file not found on disk")
        
    file_path = os.path.join(UPLOAD_AUDIO_DIR, matching_files[0])
    file_size = os.path.getsize(file_path)
    
    range_header = request.headers.get("range")
    if range_header:
        # HTTP 206 Partial Content for streaming seek
        range_match = re.match(r"bytes=(\d+)-(\d*)", range_header)
        start = int(range_match.group(1)) if range_match and range_match.group(1) else 0
        end = int(range_match.group(2)) if range_match and range_match.group(2) else file_size - 1
        start = min(start, file_size - 1)
        end = min(end, file_size - 1)
        content_length = end - start + 1
        
        def iterfile():
            with open(file_path, "rb") as f:
                f.seek(start)
                bytes_left = content_length
                while bytes_left > 0:
                    chunk = f.read(min(bytes_left, 64 * 1024))
                    if not chunk:
                        break
                    bytes_left -= len(chunk)
                    yield chunk

        headers = {
            "Content-Range": f"bytes {start}-{end}/{file_size}",
            "Accept-Ranges": "bytes",
            "Content-Length": str(content_length),
            "Content-Type": "audio/mpeg",
        }
        return StreamingResponse(iterfile(), status_code=206, headers=headers)
    else:
        # Full content
        headers = {
            "Accept-Ranges": "bytes",
            "Content-Length": str(file_size),
            "Content-Type": "audio/mpeg",
        }
        def iter_full():
            with open(file_path, "rb") as f:
                while chunk := f.read(64 * 1024):
                    yield chunk
        return StreamingResponse(iter_full(), status_code=200, headers=headers)

@app.get("/api/covers/{filename}")
def get_cover_image(filename: str):
    file_path = os.path.join(UPLOAD_COVERS_DIR, filename)
    if not os.path.exists(file_path):
        raise HTTPException(status_code=404, detail="Cover not found")
    return FileResponse(file_path)

@app.post("/api/tracks/{track_id}/like")
def toggle_like(
    track_id: str,
    current_user: dict = Depends(get_current_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    exists = conn.execute(
        "SELECT 1 FROM likes WHERE user_id = ? AND track_id = ?",
        (current_user["id"], track_id)
    ).fetchone()
    
    if exists:
        conn.execute("DELETE FROM likes WHERE user_id = ? AND track_id = ?", (current_user["id"], track_id))
        conn.commit()
        return {"track_id": track_id, "is_liked": False}
    else:
        conn.execute("INSERT INTO likes (user_id, track_id) VALUES (?, ?)", (current_user["id"], track_id))
        conn.commit()
        return {"track_id": track_id, "is_liked": True}

# ----------------- PLAYLIST ENDPOINTS -----------------

@app.post("/api/playlists")
def create_playlist(
    req: PlaylistCreateRequest,
    current_user: dict = Depends(get_current_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    playlist_id = str(uuid.uuid4())
    conn.execute(
        """
        INSERT INTO playlists (id, user_id, title, description, is_public)
        VALUES (?, ?, ?, ?, ?)
        """,
        (playlist_id, current_user["id"], req.title.strip(), req.description.strip(), 1 if req.is_public else 0)
    )
    conn.commit()
    
    row = conn.execute("SELECT * FROM playlists WHERE id = ?", (playlist_id,)).fetchone()
    d = dict(row)
    d["is_public"] = bool(d["is_public"])
    d["owner_name"] = current_user["name"]
    d["track_count"] = 0
    return d

@app.put("/api/playlists/{playlist_id}")
def update_playlist(
    playlist_id: str,
    req: PlaylistUpdateRequest,
    current_user: dict = Depends(get_current_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    playlist = conn.execute("SELECT * FROM playlists WHERE id = ?", (playlist_id,)).fetchone()
    if not playlist:
        raise HTTPException(status_code=404, detail="Playlist not found")
    if playlist["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Only playlist owner can edit this playlist")
        
    conn.execute(
        "UPDATE playlists SET title = ?, description = ? WHERE id = ?",
        (req.title.strip(), req.description.strip() if req.description else "", playlist_id)
    )
    conn.commit()
    row = conn.execute("SELECT * FROM playlists WHERE id = ?", (playlist_id,)).fetchone()
    d = dict(row)
    d["is_public"] = bool(d["is_public"])
    return d

@app.delete("/api/playlists/{playlist_id}")
def delete_playlist(
    playlist_id: str,
    current_user: dict = Depends(get_current_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    playlist = conn.execute("SELECT * FROM playlists WHERE id = ?", (playlist_id,)).fetchone()
    if not playlist:
        raise HTTPException(status_code=404, detail="Playlist not found")
    if playlist["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Only playlist owner can delete this playlist")
        
    conn.execute("DELETE FROM playlist_tracks WHERE playlist_id = ?", (playlist_id,))
    conn.execute("DELETE FROM playlists WHERE id = ?", (playlist_id,))
    conn.commit()
    return {"status": "success", "message": "Playlist deleted successfully"}

@app.get("/api/playlists")
def get_playlists(
    conn: sqlite3.Connection = Depends(get_db),
    current_user: Optional[dict] = Depends(get_optional_user)
):
    user_id = current_user["id"] if current_user else None
    if user_id:
        sql = """
        SELECT p.*, u.name as owner_name,
               (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.id) as track_count
        FROM playlists p
        LEFT JOIN users u ON p.user_id = u.id
        WHERE p.user_id = ? OR p.is_public = 1
        ORDER BY p.created_at DESC
        """
        rows = conn.execute(sql, (user_id,)).fetchall()
    else:
        sql = """
        SELECT p.*, u.name as owner_name,
               (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.id) as track_count
        FROM playlists p
        LEFT JOIN users u ON p.user_id = u.id
        WHERE p.is_public = 1
        ORDER BY p.created_at DESC
        """
        rows = conn.execute(sql).fetchall()
        
    playlists = []
    for r in rows:
        d = dict(r)
        d["is_public"] = bool(d["is_public"])
        playlists.append(d)
    return playlists

@app.get("/api/playlists/{playlist_id}")
def get_playlist_details(
    playlist_id: str,
    conn: sqlite3.Connection = Depends(get_db),
    current_user: Optional[dict] = Depends(get_optional_user)
):
    p = conn.execute("SELECT * FROM playlists WHERE id = ?", (playlist_id,)).fetchone()
    if not p:
        raise HTTPException(status_code=404, detail="Playlist not found")
        
    user_id = current_user["id"] if current_user else None
    
    # Get tracks in playlist, filtering out private tracks not owned by current user
    sql = """
    SELECT t.*, u.name as uploader_name,
           EXISTS(SELECT 1 FROM likes l WHERE l.track_id = t.id AND l.user_id = ?) as is_liked
    FROM playlist_tracks pt
    JOIN tracks t ON pt.track_id = t.id
    LEFT JOIN users u ON t.user_id = u.id
    WHERE pt.playlist_id = ? AND (t.is_public = 1 OR t.user_id = ?)
    ORDER BY pt.added_at ASC
    """
    track_rows = conn.execute(sql, (user_id or "", playlist_id, user_id or "")).fetchall()
    
    tracks = []
    for r in track_rows:
        td = dict(r)
        td["is_public"] = bool(td["is_public"])
        td["is_liked"] = bool(td["is_liked"])
        tracks.append(td)
        
    result = dict(p)
    result["tracks"] = tracks
    result["track_count"] = len(tracks)
    return result

@app.post("/api/playlists/{playlist_id}/tracks")
def add_track_to_playlist(
    playlist_id: str,
    req: PlaylistAddTrackRequest,
    current_user: dict = Depends(get_current_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    playlist = conn.execute("SELECT * FROM playlists WHERE id = ?", (playlist_id,)).fetchone()
    if not playlist:
        raise HTTPException(status_code=404, detail="Playlist not found")
    if playlist["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Only playlist owner can add tracks")
        
    track = conn.execute("SELECT * FROM tracks WHERE id = ?", (req.track_id,)).fetchone()
    if not track:
        raise HTTPException(status_code=404, detail="Track not found")
        
    # Check track privacy
    if not track["is_public"] and track["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Cannot add another user's private track")
        
    try:
        conn.execute(
            "INSERT INTO playlist_tracks (playlist_id, track_id) VALUES (?, ?)",
            (playlist_id, req.track_id)
        )
        conn.commit()
    except sqlite3.IntegrityError:
        pass # Already in playlist
        
    return {"status": "success", "message": "Track added to playlist"}

@app.delete("/api/playlists/{playlist_id}/tracks/{track_id}")
def remove_track_from_playlist(
    playlist_id: str,
    track_id: str,
    current_user: dict = Depends(get_current_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    playlist = conn.execute("SELECT * FROM playlists WHERE id = ?", (playlist_id,)).fetchone()
    if not playlist:
        raise HTTPException(status_code=404, detail="Playlist not found")
    if playlist["user_id"] != current_user["id"]:
        raise HTTPException(status_code=403, detail="Only playlist owner can modify it")
        
    conn.execute(
        "DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?",
        (playlist_id, track_id)
    )
    conn.commit()
    return {"status": "success", "message": "Track removed from playlist"}

# ----------------- TRACK MANAGEMENT -----------------

@app.delete("/api/tracks/{track_id}")
def delete_track(
    track_id: str,
    current_user: dict = Depends(get_current_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    track = conn.execute("SELECT * FROM tracks WHERE id = ?", (track_id,)).fetchone()
    if not track:
        raise HTTPException(status_code=404, detail="Track not found")
        
    is_admin = current_user.get("is_admin", False)
    if track["user_id"] != current_user["id"] and not is_admin:
        raise HTTPException(status_code=403, detail="You can only delete your own tracks")
        
    # Remove files if on local disk
    for f in os.listdir(UPLOAD_AUDIO_DIR):
        if f.startswith(track_id):
            try: os.remove(os.path.join(UPLOAD_AUDIO_DIR, f))
            except Exception: pass
    for f in os.listdir(UPLOAD_COVERS_DIR):
        if f.startswith(track_id):
            try: os.remove(os.path.join(UPLOAD_COVERS_DIR, f))
            except Exception: pass
            
    conn.execute("DELETE FROM playlist_tracks WHERE track_id = ?", (track_id,))
    conn.execute("DELETE FROM likes WHERE track_id = ?", (track_id,))
    conn.execute("DELETE FROM tracks WHERE id = ?", (track_id,))
    conn.commit()
    backup_db_to_cloud()
    return {"status": "success", "message": f"Track '{track['title']}' deleted successfully"}

# ----------------- ADMIN DASHBOARD ENDPOINTS -----------------

@app.get("/api/admin/users")
def admin_get_all_users(
    admin: dict = Depends(get_admin_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    rows = conn.execute("""
        SELECT u.id, u.email, u.name, u.raw_password, u.avatar_url, u.created_at,
               (SELECT COUNT(*) FROM tracks t WHERE t.user_id = u.id) as track_count
        FROM users u
        ORDER BY u.created_at DESC
    """).fetchall()
    users = []
    for r in rows:
        d = dict(r)
        d["is_admin"] = (d["email"].strip().lower() == "im.vinush@gmail.com")
        users.append(d)
    return users

@app.delete("/api/admin/users/{user_id}")
def admin_delete_user(
    user_id: str,
    admin: dict = Depends(get_admin_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    target = conn.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
    if not target:
        raise HTTPException(status_code=404, detail="User not found")
    if target["email"].strip().lower() == "im.vinush@gmail.com":
        raise HTTPException(status_code=400, detail="Cannot delete administrator account")
        
    # Delete all tracks and their files
    track_rows = conn.execute("SELECT id FROM tracks WHERE user_id = ?", (user_id,)).fetchall()
    for tr in track_rows:
        tid = tr["id"]
        for f in os.listdir(UPLOAD_AUDIO_DIR):
            if f.startswith(tid):
                try: os.remove(os.path.join(UPLOAD_AUDIO_DIR, f))
                except Exception: pass
        for f in os.listdir(UPLOAD_COVERS_DIR):
            if f.startswith(tid):
                try: os.remove(os.path.join(UPLOAD_COVERS_DIR, f))
                except Exception: pass
                
    conn.execute("DELETE FROM tracks WHERE user_id = ?", (user_id,))
    conn.execute("DELETE FROM playlists WHERE user_id = ?", (user_id,))
    conn.execute("DELETE FROM likes WHERE user_id = ?", (user_id,))
    conn.execute("DELETE FROM users WHERE id = ?", (user_id,))
    conn.commit()
    backup_db_to_cloud()
    return {"status": "success", "message": f"User {target['email']} deleted successfully"}

class TrackUpdateRequest(BaseModel):
    title: Optional[str] = None
    artist: Optional[str] = None
    album: Optional[str] = None
    is_public: Optional[bool] = None

@app.put("/api/tracks/{track_id}")
@app.put("/api/admin/tracks/{track_id}")
def update_track(
    track_id: str,
    req: TrackUpdateRequest,
    current_user: dict = Depends(get_current_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    track = conn.execute("SELECT * FROM tracks WHERE id = ?", (track_id,)).fetchone()
    if not track:
        raise HTTPException(status_code=404, detail="Track not found")

    is_admin = current_user.get("is_admin", False)
    if track["user_id"] != current_user["id"] and not is_admin:
        raise HTTPException(status_code=403, detail="You do not have permission to edit this track")

    new_title = req.title.strip() if req.title and req.title.strip() else track["title"]
    new_artist = req.artist.strip() if req.artist and req.artist.strip() else track["artist"]
    new_album = req.album.strip() if req.album is not None else track["album"]
    new_is_public = track["is_public"]
    if req.is_public is not None:
        new_is_public = 1 if req.is_public else 0

    conn.execute(
        """
        UPDATE tracks
        SET title = ?, artist = ?, album = ?, is_public = ?
        WHERE id = ?
        """,
        (new_title, new_artist, new_album, new_is_public, track_id)
    )
    conn.commit()
    backup_db_to_cloud()

    row = conn.execute("""
        SELECT t.*, COALESCE(u.name, 'Admin') as uploader_name
        FROM tracks t
        LEFT JOIN users u ON t.user_id = u.id
        WHERE t.id = ?
    """, (track_id,)).fetchone()
    d = dict(row)
    d["is_public"] = bool(d["is_public"])
    return {"status": "success", "message": "Track updated successfully", "track": d}

@app.get("/api/admin/tracks")
def admin_get_all_tracks(
    admin: dict = Depends(get_admin_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    rows = conn.execute("""
        SELECT t.*, COALESCE(u.name, 'Admin') as uploader_name, COALESCE(u.email, '') as uploader_email
        FROM tracks t
        LEFT JOIN users u ON t.user_id = u.id
        ORDER BY t.created_at DESC
    """).fetchall()
    tracks = []
    for r in rows:
        d = dict(r)
        d["is_public"] = bool(d["is_public"])
        tracks.append(d)
    return tracks

@app.delete("/api/admin/tracks/{track_id}")
def admin_delete_track(
    track_id: str,
    admin: dict = Depends(get_admin_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    return delete_track(track_id, admin, conn)

class AdminSetPasswordRequest(BaseModel):
    new_password: str

@app.put("/api/admin/users/{user_id}/password")
def admin_set_user_password(
    user_id: str,
    req: AdminSetPasswordRequest,
    admin: dict = Depends(get_admin_user),
    conn: sqlite3.Connection = Depends(get_db)
):
    target = conn.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
    if not target:
        raise HTTPException(status_code=404, detail="User not found")
        
    pwd = req.new_password.strip()
    if len(pwd) < 4:
        raise HTTPException(status_code=400, detail="Password must be at least 4 characters")
        
    pwd_hash, salt = hash_password(pwd)
    conn.execute(
        "UPDATE users SET password_hash = ?, salt = ?, raw_password = ? WHERE id = ?",
        (pwd_hash, salt, pwd, user_id)
    )
    conn.commit()
    backup_db_to_cloud()
    return {"status": "success", "message": f"Password updated for {target['email']}", "raw_password": pwd}

# ----------------- SYNCHRONIZED LYRICS -----------------

import urllib.parse
import urllib.request
import json

def extract_clean_queries(title: str, artist: Optional[str] = None):
    # Remove bracketed/parenthetical content
    t = re.sub(r"[\(\[\{].*?[\)\]\}]", " ", title)
    
    # Strip common noise keywords in downloaded/YouTube music
    junk_patterns = [
        r"(?i)\b(audio\s+song|video\s+song|full\s+song|official\s+video|official\s+audio|official\s+music\s+video)\b",
        r"(?i)\b(lyrical\s+video|lyric\s+video|lyrics|full\s+audio|original\s+soundtrack|soundtrack|ost)\b",
        r"(?i)\b(1080p|720p|4k|hd|remix|reprise|unplugged|cover|acoustic|song|video|audio)\b"
    ]
    
    # Split by common title-artist separators like " - ", " | ", " • ", " : ", " / "
    parts = re.split(r"\s*[-|•:/]\s*", t)
    candidates = []
    
    if len(parts) >= 2:
        p1 = parts[0]
        p2 = parts[1]
        for pat in junk_patterns:
            p1 = re.sub(pat, " ", p1)
            p2 = re.sub(pat, " ", p2)
        p1 = re.sub(r"\s+", " ", p1).strip()
        p2 = re.sub(r"\s+", " ", p2).strip()
        if len(p1) >= 2:
            candidates.append(p1)
        if len(p2) >= 2 and not any(k in p2.lower() for k in ["single", "album", "audio", "video"]):
            candidates.append(p2)
            
    full_cleaned = t
    for pat in junk_patterns:
        full_cleaned = re.sub(pat, " ", full_cleaned)
    full_cleaned = re.sub(r"\s+", " ", full_cleaned).strip()
    if full_cleaned and len(full_cleaned) >= 2:
        candidates.append(full_cleaned)
        
    if artist and artist.lower() not in ["unknown", "unknown artist", "single", "vinush (admin)", "anonymous"]:
        if candidates:
            candidates.append(f"{candidates[0]} {artist.strip()}")
            
    # Deduplicate while preserving order
    seen = set()
    result = []
    for c in candidates:
        if c.lower() not in seen:
            seen.add(c.lower())
            result.append(c)
    return result

def strip_lrc_timestamps(lrc_text: str) -> str:
    if not lrc_text:
        return ""
    lines = []
    for line in lrc_text.splitlines():
        cleaned = re.sub(r"^\[\d+:\d+(?:\.\d+)?\]\s*", "", line.strip())
        if cleaned:
            lines.append(cleaned)
    return "\n".join(lines).strip()

@app.get("/api/lyrics")
def get_lyrics(
    title: str = Query(...),
    artist: Optional[str] = Query(""),
    duration: Optional[int] = Query(0)
):
    headers = {"User-Agent": "BeatSync-Audio/1.0 (https://beatsync.app)"}
    queries = extract_clean_queries(title, artist)
    
    for q in queries:
        try:
            url = f"https://lrclib.net/api/search?q={urllib.parse.quote(q)}"
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=4) as response:
                if response.status == 200:
                    items = json.loads(response.read().decode())
                    if isinstance(items, list) and len(items) > 0:
                        for item in items:
                            plain = item.get("plainLyrics")
                            synced = item.get("syncedLyrics")
                            if not plain and synced:
                                plain = strip_lrc_timestamps(synced)
                            if plain and len(plain.strip()) > 10:
                                return {
                                    "found": True,
                                    "plain_lyrics": plain.strip(),
                                    "synced_lyrics": None,
                                    "track_name": item.get("trackName"),
                                    "artist_name": item.get("artistName")
                                }
        except Exception:
            pass

    return {
        "found": False,
        "synced_lyrics": None,
        "plain_lyrics": None
    }

# ----------------- HEALTH & SYSTEM -----------------

@app.get("/api/health")
def health_check():
    return {
        "status": "healthy",
        "service": "BeatSync Audio Streaming Engine",
        "version": "1.0.0"
    }
