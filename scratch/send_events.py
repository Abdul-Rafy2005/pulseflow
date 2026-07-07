import time
import random
import requests

BACKEND_URL = "http://localhost:8082"
API_KEY = "pulseflow-dev-key-2026-xyz789"

EVENT_TYPES = [
    "LOGIN",
    "LOGOUT",
    "REGISTER",
    "SEARCH",
    "PAGE_VIEW",
    "BUTTON_CLICK",
    "PURCHASE",
    "VIDEO_PLAY",
    "LIKE",
    "COMMENT",
    "SHARE",
    "DOWNLOAD"
]

SOURCES = ["web", "ios", "android", "backend-service"]

METADATA_TEMPLATES = {
    "LOGIN": lambda: {"method": "password"},
    "LOGOUT": lambda: {"session_duration_sec": random.randint(30, 3600)},
    "REGISTER": lambda: {"referral": "google"},
    "SEARCH": lambda: {"query": random.choice(["analytics", "websocket", "docker", "spring boot", "react"])},
    "PAGE_VIEW": lambda: {"path": random.choice(["/home", "/pricing", "/features", "/docs", "/dashboard"])},
    "BUTTON_CLICK": lambda: {"button_id": random.choice(["signup-btn", "buy-now", "read-more", "nav-home"])},
    "PURCHASE": lambda: {"amount": round(random.uniform(5.0, 99.0), 2), "currency": "USD"},
    "VIDEO_PLAY": lambda: {"video_id": f"vid_{random.randint(100, 999)}", "duration_watched_sec": random.randint(10, 300)},
    "LIKE": lambda: {"content_id": f"post_{random.randint(1, 50)}"},
    "COMMENT": lambda: {"content_id": f"post_{random.randint(1, 50)}", "char_count": random.randint(5, 200)},
    "SHARE": lambda: {"content_id": f"post_{random.randint(1, 50)}", "platform": random.choice(["twitter", "linkedin", "whatsapp"])},
    "DOWNLOAD": lambda: {"file_id": f"doc_{random.randint(1, 10)}", "file_size_mb": round(random.uniform(0.5, 15.0), 2)}
}

def register_admin():
    print("Attempting to register default admin...")
    url = f"{BACKEND_URL}/auth/register"
    payload = {
        "username": "admin",
        "email": "admin@example.com",
        "password": "password123"
    }
    try:
        resp = requests.post(url, json=payload)
        if resp.status_code == 201:
            print("Successfully registered admin user!")
        elif resp.status_code == 409:
            print("Admin user already exists.")
        else:
            print(f"Register failed with status {resp.status_code}: {resp.text}")
    except Exception as e:
        print(f"Failed to connect for registration: {e}")

def send_events(count=50, delay=1.0):
    register_admin()
    
    print(f"\nSending {count} random events to {BACKEND_URL}/events...")
    
    for i in range(1, count + 1):
        event_type = random.choice(EVENT_TYPES)
        payload = {
            "eventType": event_type,
            "userId": random.choice([1, 2, 3, 4, 5, None]),
            "source": random.choice(SOURCES),
            "metadata": METADATA_TEMPLATES[event_type]()
        }
        
        headers = {
            "X-API-Key": API_KEY,
            "Content-Type": "application/json"
        }
        
        try:
            resp = requests.post(f"{BACKEND_URL}/events", json=payload, headers=headers)
            if resp.status_code == 202:
                print(f"[{i}/{count}] Sent {event_type} successfully.")
            else:
                print(f"[{i}/{count}] Failed to send event. Status: {resp.status_code}")
        except Exception as e:
            print(f"[{i}/{count}] Connection error: {e}")
            
        time.sleep(delay)

if __name__ == "__main__":
    import sys
    count = 50
    delay = 0.5
    if len(sys.argv) > 1:
        count = int(sys.argv[1])
    if len(sys.argv) > 2:
        delay = float(sys.argv[2])
    send_events(count, delay)
