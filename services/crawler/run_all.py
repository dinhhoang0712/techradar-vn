"""
Entrypoint cho Docker crawler container.
Chạy tất cả crawlers tuần tự, sau đó sleep CRAWL_INTERVAL giờ rồi lặp lại.
Mỗi crawler chạy trong subprocess riêng để Chrome process không bị leak.
"""
import os
import subprocess
import sys
import time
from datetime import datetime

CRAWL_INTERVAL_HOURS = int(os.getenv("CRAWL_INTERVAL_HOURS", "6"))
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


def main() -> None:
    print(f"Crawler service starting. Interval: {CRAWL_INTERVAL_HOURS}h", flush=True)

    while True:
        start = datetime.now()
        print(f"\n{'='*60}", flush=True)
        print(f"Crawl run started at {start.strftime('%Y-%m-%d %H:%M:%S')}", flush=True)
        print(f"{'='*60}", flush=True)

        results = {script: run_crawler(script) for script in CRAWLERS}

        success = sum(1 for ok in results.values() if ok)
        print(f"\nCrawl run complete: {success}/{len(CRAWLERS)} crawlers succeeded.", flush=True)

        sleep_seconds = CRAWL_INTERVAL_HOURS * 3600
        next_run = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"Next run in {CRAWL_INTERVAL_HOURS}h (at approx {next_run}).", flush=True)
        time.sleep(sleep_seconds)


if __name__ == "__main__":
    main()
