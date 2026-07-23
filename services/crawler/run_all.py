"""
Entrypoint cho Docker crawler container.
Chạy tất cả crawlers tuần tự, sau đó sleep CRAWL_INTERVAL giờ rồi lặp lại
(hoặc chạy ngay nếu nhận được trigger thủ công từ admin qua Redis).
Mỗi crawler chạy trong subprocess riêng để Chrome process không bị leak.
"""

import json
import logging
import os
import subprocess
import sys
import threading
import time
from datetime import datetime

import redis

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

CRAWL_INTERVAL_HOURS = int(os.getenv("CRAWL_INTERVAL_HOURS", "6"))
REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379")
TRIGGER_CHANNEL = "crawler:trigger"
COMPLETED_CHANNEL = "crawler:completed"
STATUS_KEY = "crawler:status"
STATUS_TTL_SECONDS = 36000  # comfortably above the worst-case 9 x 3600s sequential run

CRAWLERS = [
    # Tin tức công nghệ
    "VNExpress.py",
    "GenK.py",
    "DanTri.py",
    "ICTNews.py",
    # Việc làm IT
    "TopCV.py",
    "ITviec.py",
    "VietnamWorks.py",
    "JobsGO.py",
    # TopDev.py: viết xong nhưng CHƯA đăng ký — topdev.vn (không www) chặn/treo
    # kết nối từ container này, www.topdev.vn thì route /viec-lam-it redirect
    # sang login-wall. Cần retry sau (mạng khác, hoặc liên hệ TopDev) trước khi
    # đưa vào loop production. Xem TopDev.py để chạy thử lại.
    # API-based (không cần Selenium, chạy nhanh)
    "Viblo.py",
    "GitHub.py",
]

# redis-py connects lazily on first command, so building this unconditionally is safe
# even if Redis isn't reachable yet when the container starts.
redis_client = redis.Redis.from_url(REDIS_URL, socket_connect_timeout=5, socket_timeout=5)
wake_event = threading.Event()


def run_crawler(script: str) -> bool:
    logger.info("Starting %s...", script)
    try:
        result = subprocess.run(
            [sys.executable, script],
            timeout=3600,
            cwd=os.path.dirname(os.path.abspath(__file__)),
        )
        if result.returncode == 0:
            logger.info("%s finished successfully.", script)
            return True
        else:
            logger.warning("%s exited with code %d.", script, result.returncode)
            return False
    except subprocess.TimeoutExpired:
        logger.warning("%s timed out after 60 minutes.", script)
        return False
    except Exception:
        logger.exception("%s failed", script)
        return False


def _write_status(
    state: str,
    started_at: str,
    finished_at: str = None,
    results: dict = None,
    success_count: int = None,
    total: int = None,
) -> None:
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
        logger.warning("Could not write crawler status to Redis: %s", e)


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
            logger.info("Subscribed to '%s' for on-demand crawl triggers.", TRIGGER_CHANNEL)
            for message in pubsub.listen():
                if message["type"] == "message":
                    logger.info("Manual trigger received, waking crawl loop.")
                    wake_event.set()
        except redis.exceptions.RedisError as e:
            logger.warning("Redis pubsub connection lost (%s); retrying in 5s.", e)
            time.sleep(5)


def main() -> None:
    logger.info("Crawler service starting. Interval: %dh", CRAWL_INTERVAL_HOURS)
    threading.Thread(target=_listen_for_trigger, daemon=True, name="redis-trigger-listener").start()

    while True:
        start = datetime.now()
        logger.info("=" * 60)
        logger.info("Crawl run started at %s", start.strftime("%Y-%m-%d %H:%M:%S"))
        logger.info("=" * 60)
        _write_status("running", started_at=start.isoformat())

        results = {script: run_crawler(script) for script in CRAWLERS}

        success = sum(1 for ok in results.values() if ok)
        finished = datetime.now()
        _write_status(
            "idle",
            started_at=start.isoformat(),
            finished_at=finished.isoformat(),
            results=results,
            success_count=success,
            total=len(CRAWLERS),
        )
        # Fire-and-forget: an admin notification is a nice-to-have, must never block/fail the
        # crawl loop itself.
        try:
            redis_client.publish(COMPLETED_CHANNEL, json.dumps({
                "success_count": success,
                "total": len(CRAWLERS),
            }))
        except redis.exceptions.RedisError as e:
            logger.warning("Could not publish crawl completion to Redis: %s", e)
        logger.info("Crawl run complete: %d/%d crawlers succeeded.", success, len(CRAWLERS))

        sleep_seconds = CRAWL_INTERVAL_HOURS * 3600
        logger.info("Next run in up to %dh (or immediately if triggered from admin).", CRAWL_INTERVAL_HOURS)
        wake_event.wait(timeout=sleep_seconds)
        wake_event.clear()


if __name__ == "__main__":
    main()
