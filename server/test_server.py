import os
import sys
import json
import urllib.request
import urllib.error
import time
import subprocess

def request(url, method="GET", data=None, headers=None):
    if headers is None:
        headers = {}
    body = None
    if data is not None:
        body = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            content = resp.read()
            return resp.status, content, resp.headers
    except urllib.error.HTTPError as e:
        return e.code, e.read(), e.headers

def run_tests():
    print("--- Starting Server in background ---")
    proc = subprocess.Popen([sys.executable, "-m", "uvicorn", "main:app", "--port", "8000"], cwd=os.path.dirname(os.path.abspath(__file__)))
    time.sleep(2.5)
    
    BASE_URL = "http://127.0.0.1:8000"
    try:
        # 1. Health check
        code, content, _ = request(f"{BASE_URL}/api/health")
        assert code == 200, f"Health check failed: {content}"
        print("✓ Health check passed:", json.loads(content))
        
        # 2. Login with seeded demo user
        code, content, _ = request(f"{BASE_URL}/api/auth/login", method="POST", data={"email": "demo@beatsync.com", "password": "password123"})
        assert code == 200, f"Demo login failed: {content}"
        demo_res = json.loads(content)
        demo_token = demo_res["token"]
        demo_id = demo_res["user"]["id"]
        print("✓ Demo login passed:", demo_res["user"]["name"])
        
        # 3. Register user B
        code, content, _ = request(f"{BASE_URL}/api/auth/register", method="POST", data={"email": "alice@beatsync.com", "password": "password123", "name": "Alice Cooper"})
        if code == 400 and b"exists" in content:
            code, content, _ = request(f"{BASE_URL}/api/auth/login", method="POST", data={"email": "alice@beatsync.com", "password": "password123"})
        assert code == 200, f"Alice auth failed: {content}"
        alice_res = json.loads(content)
        alice_token = alice_res["token"]
        alice_id = alice_res["user"]["id"]
        print("✓ Alice auth passed")
        
        # 4. Check track privacy
        # Anonymous user checks tracks
        code, content, _ = request(f"{BASE_URL}/api/tracks")
        anon_tracks = json.loads(content)
        for t in anon_tracks:
            assert t["is_public"] == True, f"Anonymous user saw a private track! {t}"
        print("✓ Anonymous user only sees public tracks (count:", len(anon_tracks), ")")
        
        # Demo user checks tracks (has 1 private track)
        code, content, _ = request(f"{BASE_URL}/api/tracks", headers={"Authorization": f"Bearer {demo_token}"})
        demo_tracks = json.loads(content)
        demo_private_count = sum(1 for t in demo_tracks if not t["is_public"])
        assert demo_private_count >= 1, "Demo user should see their own private tracks"
        print(f"✓ Demo user sees their own private tracks (count: {demo_private_count})")
        
        # Alice checks tracks - MUST NOT see Demo user's private tracks
        code, content, _ = request(f"{BASE_URL}/api/tracks", headers={"Authorization": f"Bearer {alice_token}"})
        alice_tracks = json.loads(content)
        for t in alice_tracks:
            if not t["is_public"]:
                assert t["user_id"] == alice_id, f"Alice saw another user's private track! {t}"
        print("✓ Alice cannot see Demo user's private tracks!")
        
        # 5. Test streaming endpoint with range header
        first_track = anon_tracks[0]
        code, content, headers = request(f"{BASE_URL}{first_track['file_url']}", headers={"Range": "bytes=0-1024"})
        assert code == 206, f"Expected 206 Partial Content, got {code}"
        assert len(content) == 1025, f"Expected 1025 bytes, got {len(content)}"
        print("✓ Streaming Range Request 206 Partial Content validated successfully!")
        
        # 6. Test Playlists
        code, content, _ = request(f"{BASE_URL}/api/playlists")
        assert code == 200
        print("✓ Playlists endpoint returned:", len(json.loads(content)), "playlists")
        
        print("\n==========================================")
        print("ALL BACKEND VERIFICATION TESTS PASSED 100%!")
        print("==========================================")
    finally:
        proc.terminate()

if __name__ == "__main__":
    run_tests()
