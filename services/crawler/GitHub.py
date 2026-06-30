"""
GitHub crawler — github.com (Vietnamese tech focus)
Lấy repos công khai từ các công ty tech Việt Nam nổi tiếng via GitHub API.
Không cần Selenium — dùng requests thuần.
Đẩy metadata repo vào Kafka topic raw_articles (dạng tech article).
"""
import json
import logging
import os
import time
from datetime import datetime, timezone

import requests

from kafka_producer import CrawlerKafkaProducer

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

SOURCE_PLATFORM = "GitHub"
MAX_REPOS = 200
GITHUB_API = "https://api.github.com"

# Các công ty / tổ chức tech Việt Nam nổi tiếng trên GitHub
VN_ORGS = [
    "vngcloud",           # VNG Cloud
    "zalopay",            # ZaloPay
    "tiki-miniapp",       # Tiki
    "momo-developer",     # MoMo
    "shopee",             # Shopee (HQ SG nhưng lớn ở VN)
    "fpt-corp",           # FPT
    "sun-asterisk-vn",    # Sun Asterisk VN
    "vnpay",              # VNPAY
    "techvify-software",  # TechVify
    "got-it-global",      # Got It
    "axon-active",        # Axon Active VN
    "nashtech-global",    # NashTech
    "nal-vn",             # NAL VN
    "framgia",            # Framgia / Sun* VN
]

# Query tìm repos trending về tech phổ biến tại VN
SEARCH_QUERIES = [
    "language:python topic:vietnamese",
    "language:golang org:vngcloud",
    "language:java topic:vietnam",
    "topic:laravel language:php stars:>50",
    "topic:react language:typescript stars:>100 pushed:>2024-01-01",
]

DATA_DIR = os.path.join(os.path.dirname(__file__), "data", "raw", "github")
URL_CACHE = os.path.join(DATA_DIR, "processed_urls.txt")

HEADERS = {
    "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
}
# Optional: set GITHUB_TOKEN env var to increase rate limit (5000 req/h vs 60)
_token = os.getenv("GITHUB_TOKEN")
if _token:
    HEADERS["Authorization"] = f"Bearer {_token}"


def _load_urls(path: str) -> set:
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return {l.strip() for l in f if l.strip()}
    return set()


def _save_url(path: str, url: str):
    with open(path, "a", encoding="utf-8") as f:
        f.write(url + "\n")


def _repo_to_article(repo: dict) -> dict:
    """Chuyển đổi repo metadata thành dạng article để gửi Kafka."""
    name = repo.get("full_name", "")
    desc = repo.get("description") or ""
    topics = repo.get("topics") or []
    lang = repo.get("language") or ""
    stars = repo.get("stargazers_count", 0)
    forks = repo.get("forks_count", 0)
    pushed = repo.get("pushed_at", "")

    title = f"{name}: {desc}" if desc else name
    content = (
        f"Repository: {name}\n"
        f"Mô tả: {desc}\n"
        f"Ngôn ngữ chính: {lang}\n"
        f"Topics: {', '.join(topics)}\n"
        f"Stars: {stars} | Forks: {forks}\n"
        f"Cập nhật lần cuối: {pushed}\n"
        f"URL: {repo.get('html_url', '')}"
    )
    return {
        "title": title,
        "content": content,
        "source_url": repo.get("html_url", ""),
        "publish_date": pushed[:10] if pushed else "",
    }


def _fetch_org_repos(org: str, processed: set) -> list:
    repos = []
    page = 1
    while True:
        try:
            r = requests.get(
                f"{GITHUB_API}/orgs/{org}/repos",
                headers=HEADERS,
                params={"type": "public", "sort": "pushed", "per_page": 30, "page": page},
                timeout=15,
            )
            if r.status_code == 404:
                logger.warning("Org not found: %s", org)
                break
            if r.status_code == 403:
                logger.warning("GitHub rate limit hit, sleeping 60s")
                time.sleep(60)
                continue
            r.raise_for_status()
            data = r.json()
            if not data:
                break
            for repo in data:
                url = repo.get("html_url", "")
                if url and url not in processed and not repo.get("fork"):
                    repos.append(repo)
            page += 1
            time.sleep(0.5)
        except requests.RequestException as e:
            logger.warning("Failed to fetch %s repos: %s", org, e)
            break
    return repos


def _fetch_search_repos(query: str, processed: set) -> list:
    repos = []
    try:
        r = requests.get(
            f"{GITHUB_API}/search/repositories",
            headers=HEADERS,
            params={"q": query, "sort": "updated", "order": "desc", "per_page": 30},
            timeout=15,
        )
        if r.status_code == 403:
            logger.warning("GitHub rate limit hit on search")
            return repos
        r.raise_for_status()
        items = r.json().get("items", [])
        for repo in items:
            url = repo.get("html_url", "")
            if url and url not in processed:
                repos.append(repo)
        time.sleep(1)
    except requests.RequestException as e:
        logger.warning("Search query '%s' failed: %s", query, e)
    return repos


def main():
    os.makedirs(DATA_DIR, exist_ok=True)

    kafka = CrawlerKafkaProducer()
    kafka_enabled = False
    try:
        kafka_enabled = kafka.connect()
        logger.info("Kafka %s", "connected" if kafka_enabled else "not available")
    except Exception as e:
        logger.warning("Kafka error: %s", e)

    processed = _load_urls(URL_CACHE)
    output_file = os.path.join(DATA_DIR, f"{datetime.now().strftime('%d_%m_%Y')}.json")
    articles = []
    total = 0

    # 1. Lấy repos từ các org Việt Nam
    for org in VN_ORGS:
        if total >= MAX_REPOS:
            break
        logger.info("Fetching org: %s", org)
        repos = _fetch_org_repos(org, processed)
        for repo in repos:
            if total >= MAX_REPOS:
                break
            art = _repo_to_article(repo)
            articles.append(art)
            _save_url(URL_CACHE, art["source_url"])
            processed.add(art["source_url"])
            if kafka_enabled:
                kafka.send_article(
                    title=art["title"], content=art["content"],
                    source_url=art["source_url"], source_platform=SOURCE_PLATFORM,
                    publish_date=art["publish_date"],
                )
            total += 1
            logger.info("[%d] %s", total, repo.get("full_name"))

    # 2. Tìm kiếm thêm qua search API
    for query in SEARCH_QUERIES:
        if total >= MAX_REPOS:
            break
        logger.info("Search: %s", query)
        repos = _fetch_search_repos(query, processed)
        for repo in repos:
            if total >= MAX_REPOS:
                break
            art = _repo_to_article(repo)
            articles.append(art)
            _save_url(URL_CACHE, art["source_url"])
            processed.add(art["source_url"])
            if kafka_enabled:
                kafka.send_article(
                    title=art["title"], content=art["content"],
                    source_url=art["source_url"], source_platform=SOURCE_PLATFORM,
                    publish_date=art["publish_date"],
                )
            total += 1
            logger.info("[%d] %s", total, repo.get("full_name"))

    kafka.close()

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump({"source_platform": SOURCE_PLATFORM, "post_detail": articles}, f,
                  ensure_ascii=False, indent=2)

    logger.info("GitHub done: %d repos crawled", total)


if __name__ == "__main__":
    main()
