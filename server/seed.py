import os
import wave
import math
import struct
import sqlite3
import uuid
from auth import hash_password
from database import init_db, DB_PATH

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
UPLOAD_AUDIO_DIR = os.path.join(BASE_DIR, "uploads", "audio")
os.makedirs(UPLOAD_AUDIO_DIR, exist_ok=True)

def generate_musical_track(filename, chords, duration_seconds=12, sample_rate=44100):
    filepath = os.path.join(UPLOAD_AUDIO_DIR, filename)
    if os.path.exists(filepath):
        return filepath
        
    num_samples = int(duration_seconds * sample_rate)
    with wave.open(filepath, "w") as wav_file:
        wav_file.setnchannels(1) # mono
        wav_file.setsampwidth(2) # 16-bit
        wav_file.setframerate(sample_rate)
        
        chord_duration = duration_seconds / len(chords)
        data = bytearray()
        
        for i in range(num_samples):
            t = i / sample_rate
            chord_idx = min(int(t / chord_duration), len(chords) - 1)
            frequencies = chords[chord_idx]
            
            # Harmonic blend with soft envelope
            val = 0.0
            for freq in frequencies:
                val += 0.25 * math.sin(2 * math.pi * freq * t)
                val += 0.10 * math.sin(4 * math.pi * freq * t) # harmonic
                
            # Soft drum pulse every 0.5s
            pulse = math.exp(-25.0 * ((t % 0.5) ** 1.2))
            val += 0.20 * pulse * math.sin(2 * math.pi * 70 * t)
            
            # Master volume envelope
            fade = min(1.0, t / 0.5) * min(1.0, (duration_seconds - t) / 0.5)
            sample = int(max(-32767, min(32767, val * fade * 32767 * 0.7)))
            data.extend(struct.pack("<h", sample))
            
        wav_file.writeframes(data)
    return filepath

def seed():
    init_db()
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # Check if demo user exists
    user = cursor.execute("SELECT id FROM users WHERE email = 'demo@beatsync.com'").fetchone()
    if not user:
        user_id = str(uuid.uuid4())
        pwd_hash, salt = hash_password("password123")
        cursor.execute(
            "INSERT INTO users (id, email, password_hash, salt, name) VALUES (?, ?, ?, ?, ?)",
            (user_id, "demo@beatsync.com", pwd_hash, salt, "Acoustic Minimalist")
        )
        conn.commit()
    else:
        user_id = user[0]
        
    # Seed sample tracks
    demo_tracks = [
        {
            "id": "track-minimal-horizon",
            "title": "Minimal Horizon",
            "artist": "OneTap Soundscapes",
            "album": "Architecture Vol. 1",
            "duration": 15,
            "is_public": 1,
            "chords": [
                [261.63, 329.63, 392.00], # C Major
                [220.00, 261.63, 329.63], # A Minor
                [174.61, 220.00, 261.63], # F Major
                [196.00, 246.94, 293.66], # G Major
            ]
        },
        {
            "id": "track-silicon-pulse",
            "title": "Silicon Pulse",
            "artist": "Quantum Core",
            "album": "Deep Tech Synth",
            "duration": 16,
            "is_public": 1,
            "chords": [
                [146.83, 220.00, 293.66], # Dm
                [164.81, 246.94, 329.63], # Em
                [174.61, 261.63, 349.23], # F
                [196.00, 293.66, 392.00], # G
            ]
        },
        {
            "id": "track-obsidian-night",
            "title": "Obsidian Night",
            "artist": "Monochrome Beats",
            "album": "Dark Canvas",
            "duration": 18,
            "is_public": 1,
            "chords": [
                [220.00, 261.63, 329.63], # Am
                [196.00, 246.94, 293.66], # G
                [174.61, 220.00, 261.63], # F
                [164.81, 207.65, 246.94], # E
            ]
        },
        {
            "id": "track-private-draft",
            "title": "Private Studio Session (Demo)",
            "artist": "Acoustic Minimalist",
            "album": "Unreleased Archives",
            "duration": 14,
            "is_public": 0, # PRIVATE! Only demo@beatsync.com can see this!
            "chords": [
                [130.81, 196.00, 261.63],
                [146.83, 220.00, 293.66],
                [164.81, 246.94, 329.63],
                [174.61, 261.63, 349.23],
            ]
        }
    ]
    
    for t in demo_tracks:
        filename = f"{t['id']}.wav"
        generate_musical_track(filename, t["chords"], t["duration"])
        
        # Check if already in DB
        exists = cursor.execute("SELECT id FROM tracks WHERE id = ?", (t["id"],)).fetchone()
        if not exists:
            cursor.execute(
                """
                INSERT INTO tracks (id, user_id, title, artist, album, duration_seconds, file_url, cover_url, is_public)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    t["id"],
                    user_id,
                    t["title"],
                    t["artist"],
                    t["album"],
                    t["duration"],
                    f"/api/tracks/{t['id']}/stream",
                    None,
                    t["is_public"]
                )
            )
            
    # Seed a demo playlist
    playlist = cursor.execute("SELECT id FROM playlists WHERE title = 'Minimal Focus Flow'").fetchone()
    if not playlist:
        pl_id = "pl-minimal-focus"
        cursor.execute(
            """
            INSERT INTO playlists (id, user_id, title, description, is_public)
            VALUES (?, ?, ?, ?, 1)
            """,
            (pl_id, user_id, "Minimal Focus Flow", "Deep focus sounds designed for architectural flow states.")
        )
        cursor.execute(
            "INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_id) VALUES (?, ?)",
            (pl_id, "track-minimal-horizon")
        )
        cursor.execute(
            "INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_id) VALUES (?, ?)",
            (pl_id, "track-silicon-pulse")
        )
        
    conn.commit()
    conn.close()
    print("Database successfully seeded with demo tracks and user!")

if __name__ == "__main__":
    seed()
