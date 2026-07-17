"""
Entrypoint cho Docker crawler container.
Chạy tất cả crawlers tuần tự, sau đó sleep CRAWL_INTERVAL giờ rồi lặp lại
(hoặc chạy ngay nếu nhận được trigger thủ công từ admin qua Redis).
Mỗi crawler chạy trong subprocess riêng để Chrome process không bị leak.
"""
import json
import os
import subprocess
import sys
import threading
import time
from datetime import datetime

import redis

CRAWL_INTERVAL_HOURS = int(os.getenv("CRAWL_INTERVAL_HOURS", "6"))
REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379")
TRIGGER_CHANNEL = "crawler:trigger"
STATUS_KEY = "crawler:status"
STATUS_TTL_SECONDS = 36000  # comfortably above the worst-case 8 x 3600s sequential run

CRAWLERS = [
    # Tin tức công nghệ
    "VNExpress.py",
    "GenK.py",
    "DanTri.py",
    "ICTNews.py",
    # Việc làm IT
    "TopCV.py",
    "ITviec.py",
    # API-based (không cần Selenium, chạy nhanh)
    "Viblo.py",
    "GitHub.py",
]

# redis-py connects lazily on first command, so building this unconditionally is safe
# even if Redis isn't reachable yet when the container starts.
redis_client = redis.Redis.from_url(REDIS_URL, socket_connect_timeout=5, socket_timeout=5)
wake_event = threading.Event()


def run_crawler(script: str) -> bool:
    print(f"\n[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Starting {script}...", flush=True)
    try:
        result = subprocess.run(
            [sys.executable, script],
            timeout=3600,
            cwd=os.path.dirname(os.path.abspath(__file__)),
        )
        if result.returncode == 0:
            print(f"[OK] {script} finished successfully.", flush=True)
            return True
        else:
            print(f"[WARN] {script} exited with code {result.returncode}.", flush=True)
            return False
    except subprocess.TimeoutExpired:
        print(f"[WARN] {script} timed out after 60 minutes.", flush=True)
        return False
    except Exception as e:
        print(f"[ERROR] {script} failed: {e}", flush=True)
        return False


def _write_status(state: str, started_at: str, finished_at: str = None, results: dict = None,
                   success_count: int = None, total: int = None) -> None:
    payload = {
        "state": state,
        "started_at": started_at,
        "finished_at": finished_at,
        "results": results,
        "success_count": success_count,
        "total": total,
    }
    try:
        redis_client.set(STATUS_KEY, json.dumps(payload), ex=STATUS_TTL_SECONDS)
    except redis.exceptions.RedisError as e:
        print(f"[WARN] Could not write crawler status to Redis: {e}", flush=True)


def _listen_for_trigger() -> None:
    """Runs in a background thread for the process lifetime; wakes the main crawl loop
    early on a manual admin trigger. Best-effort only — a trigger published while this
    isn't actively subscribed (e.g. mid Redis reconnect) is simply lost, same as any
    Redis pub/sub consumer; the fixed CRAWL_INTERVAL_HOURS sleep remains the reliability
    floor regardless."""
    while True:
        try:
            pubsub = redis_client.pubsub()
            pubsub.subscribe(TRIGGER_CHANNEL)
            print(f"[INFO] Subscribed to '{TRIGGER_CHANNEL}' for on-demand crawl triggers.", flush=True)
            for message in pubsub.listen():
                if message["type"] == "message":
                    print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] Manual trigger received, "
                          f"waking crawl loop.", flush=True)
                    wake_event.set()
        except redis.exceptions.RedisError as e:
            print(f"[WARN] Redis pubsub connection lost ({e}); retrying in 5s.", flush=True)
            time.sleep(5)


def main() -> None:
    print(f"Crawler service starting. Interval: {CRAWL_INTERVAL_HOURS}h", flush=True)
    threading.Thread(target=_listen_for_trigger, daemon=True, name="redis-trigger-listener").start()

    while True:
        start = datetime.now()
        print(f"\n{'='*60}", flush=True)
        print(f"Crawl run started at {start.strftime('%Y-%m-%d %H:%M:%S')}", flush=True)
        print(f"{'='*60}", flush=True)
        _write_status("running", started_at=start.isoformat())

        results = {script: run_crawler(script) for script in CRAWLERS}

        success = sum(1 for ok in results.values() if ok)
        finished = datetime.now()
        _write_status("idle", started_at=start.isoformat(), finished_at=finished.isoformat(),
                       results=results, success_count=success, total=len(CRAWLERS))
        print(f"\nCrawl run complete: {success}/{len(CRAWLERS)} crawlers succeeded.", flush=True)

        sleep_seconds = CRAWL_INTERVAL_HOURS * 3600
        print(f"Next run in up to {CRAWL_INTERVAL_HOURS}h (or immediately if triggered from admin).", flush=True)
        wake_event.wait(timeout=sleep_seconds)
        wake_event.clear()


if __name__ == "__main__":
    main()
