import os
import uuid
import re
from typing import Optional, List
from fastapi import FastAPI, Depends, HTTPException, status, UploadFile, File, Form, Header, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse, FileResponse
from pydantic import BaseModel
import sqlite3

from database import init_db, get_db
from auth import (
    hash_password, verify_password, create_access_token, 
    get_current_user, get_optional_user, decode_token
)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
UPLOAD_AUDIO_DIR = os.path.join(BASE_DIR, "uploads", "audio")
UPLOAD_COVERS_DIR = os.path.join(BASE_DIR, "uploads", "covers")

os.makedirs(UPLOAD_AUDIO_DIR, exist_ok=True)
os.makedirs(UPLOAD_COVERS_DIR, exist_ok=True)

# Initialize database schema
init_db()

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

class PlaylistCreateRequest(BaseModel):
    title: str
    description: Optional[str] = ""
    is_public: Optional[bool] = True

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
    
    conn.execute(
        "INSERT INTO users (id, email, password_hash, salt, name, avatar_url) VALUES (?, ?, ?, ?, ?, ?)",
        (user_id, email, pwd_hash, salt, req.name.strip(), None)
    )
    conn.commit()
    
    token = create_access_token(user_id, email)
    return {
        "token": token,
        "user": {
            "id": user_id,
            "email": email,
            "name": req.name.strip(),
            "avatar_url": None
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
        
    token = create_access_token(user["id"], user["email"])
    return {
        "token": token,
        "user": {
            "id": user["id"],
            "email": user["email"],
            "name": user["name"],
            "avatar_url": user["avatar_url"]
        }
    }

@app.get("/api/auth/me")
def get_me(user: dict = Depends(get_current_user)):
    return {"user": user}

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
    
    # Save audio file
    with open(audio_filepath, "wb") as f:
        while chunk := await file.read(1024 * 64):
            f.write(chunk)
            
    cover_filename = None
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
    
    conn.execute(
        """
        INSERT INTO tracks (id, user_id, title, artist, album, duration_seconds, file_url, cover_url, is_public)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (track_id, current_user["id"], title.strip(), artist.strip(), album.strip(), duration_seconds or 0, file_url, cover_url, 1 if is_public else 0)
    )
    conn.commit()
    
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
    d["track_count"] = 0
    return d

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
        
    return [dict(r) for r in rows]

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

# ----------------- HEALTH & SYSTEM -----------------

@app.get("/api/health")
def health_check():
    return {
        "status": "healthy",
        "service": "BeatSync Audio Streaming Engine",
        "version": "1.0.0"
    }
