@echo off
echo ========================================================
echo Starting BeatSync Music Streaming Backend Server...
echo ========================================================
python seed.py
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
pause
