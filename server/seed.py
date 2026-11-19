import os
import sqlite3
import uuid
from auth import hash_password
from database import init_db, DB_PATH

def seed():
    init_db()
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # 1. Permanently delete all test and demo accounts
    cursor.execute("DELETE FROM users WHERE LOWER(email) IN ('demo@beatsync.com', 'alice@beatsync.com')")
    cursor.execute("DELETE FROM tracks WHERE id LIKE 'track-%' OR title LIKE '%Silicon Pulse%' OR title LIKE '%Minimal Horizon%' OR title LIKE '%Obsidian Night%' OR title LIKE '%Studio Session%'")
    cursor.execute("DELETE FROM playlists WHERE id LIKE 'playlist-%'")
    
    # 2. Ensure Administrator account exists with clean name 'Vinush'
    admin_email = "im.vinush@gmail.com"
    admin_pwd = "735747"
    admin_name = "Vinush"
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
        
    conn.commit()
    conn.close()
    print("Database verified: test accounts purged, admin account ensured.")

if __name__ == "__main__":
    seed()
