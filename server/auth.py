import os
import hmac
import hashlib
import base64
import json
import time
from typing import Optional
from fastapi import Header, HTTPException, status, Depends
from database import get_db

SECRET_KEY = os.environ.get("JWT_SECRET", "beatsync-secure-jwt-key-928472918471203948123")
TOKEN_EXPIRY = 60 * 60 * 24 * 30  # 30 days

def hash_password(password: str) -> tuple[str, str]:
    salt = base64.b64encode(os.urandom(16)).decode('utf-8')
    key = hashlib.pbkdf2_hmac(
        'sha256',
        password.encode('utf-8'),
        salt.encode('utf-8'),
        100000
    )
    pwd_hash = base64.b64encode(key).decode('utf-8')
    return pwd_hash, salt

def verify_password(password: str, pwd_hash: str, salt: str) -> bool:
    key = hashlib.pbkdf2_hmac(
        'sha256',
        password.encode('utf-8'),
        salt.encode('utf-8'),
        100000
    )
    calc_hash = base64.b64encode(key).decode('utf-8')
    return hmac.compare_digest(calc_hash, pwd_hash)

def create_access_token(user_id: str, email: str) -> str:
    payload = {
        "sub": user_id,
        "email": email,
        "exp": int(time.time()) + TOKEN_EXPIRY
    }
    payload_b64 = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    sig = hmac.new(SECRET_KEY.encode(), payload_b64.encode(), hashlib.sha256).digest()
    sig_b64 = base64.urlsafe_b64encode(sig).decode().rstrip("=")
    return f"{payload_b64}.{sig_b64}"

def decode_token(token: str) -> Optional[dict]:
    try:
        parts = token.split(".")
        if len(parts) != 2:
            return None
        payload_b64, sig_b64 = parts
        
        # Verify signature
        expected_sig = hmac.new(SECRET_KEY.encode(), payload_b64.encode(), hashlib.sha256).digest()
        calc_sig_b64 = base64.urlsafe_b64encode(expected_sig).decode().rstrip("=")
        if not hmac.compare_digest(sig_b64, calc_sig_b64):
            return None
            
        # Add padding back
        padded = payload_b64 + "=" * (-len(payload_b64) % 4)
        payload = json.loads(base64.urlsafe_b64decode(padded.encode()).decode())
        
        if payload.get("exp", 0) < time.time():
            return None
            
        return payload
    except Exception:
        return None

def get_optional_user(authorization: Optional[str] = Header(None), token: Optional[str] = None):
    auth_token = None
    if authorization and authorization.startswith("Bearer "):
        auth_token = authorization.split(" ")[1]
    elif token:
        auth_token = token
        
    if not auth_token:
        return None
        
    payload = decode_token(auth_token)
    if not payload:
        return None
        
    conn_gen = get_db()
    conn = next(conn_gen)
    try:
        user = conn.execute("SELECT id, email, name, avatar_url FROM users WHERE id = ?", (payload["sub"],)).fetchone()
        if user:
            return dict(user)
        return None
    finally:
        conn.close()

def get_current_user(authorization: Optional[str] = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid authentication token"
        )
    token = authorization.split(" ")[1]
    payload = decode_token(token)
    if not payload:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token has expired or is invalid"
        )
        
    conn_gen = get_db()
    conn = next(conn_gen)
    try:
        user = conn.execute("SELECT id, email, name, avatar_url FROM users WHERE id = ?", (payload["sub"],)).fetchone()
        if not user:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="User not found"
            )
        return dict(user)
    finally:
        conn.close()
