"""
Viblo crawler — viblo.asia
Cộng đồng lập trình viên Việt Nam lớn nhất.
Lấy bài viết kỹ thuật qua Viblo REST API (không cần Selenium).
Đẩy vào Kafka topic raw_articles.
"""
import json
import logging
import os
import time
from datetime import datetime

import requests

from kafka_producer import CrawlerKafkaProducer

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

SOURCE_PLATFORM = "Viblo"
MAX_ARTICLES = 150
VIBLO_API = "https://viblo.asia/api"

# Các tag/category tech phổ biến trên Viblo
TAGS = [
    "python", "golang", "java", "javascript", "typescript",
    "docker", "kubernetes", "aws", "devops", "machine-learning",
    "ai", "backend", "frontend", "database", "microservices",
    "react", "nodejs", "laravel", "spring-boot", "fastapi",
]

DATA_DIR = os.path.join(os.path.dirname(__file__), "data", "raw", "viblo")
URL_CACHE = os.path.join(DATA_DIR, "processed_urls.txt")

HEADERS = {
    "Accept": "application/json",
    "User-Agent": "Mozilla/5.0 (compatible; TechRadarVN-bot/1.0)",
}


def _load_urls(path: str) -> set:
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return {l.strip() for l in f if l.strip()}
    return set()


def _save_url(path: str, url: str):
    with open(path, "a", encoding="utf-8") as f:
        f.write(url + "\n")


def _fetch_posts_by_tag(tag: str, page: int = 1) -> list:
    try:
        r = requests.get(
            f"{VIBLO_API}/tags/{tag}/posts",
            headers=HEADERS,
            params={"page": page, "limit": 20},
            timeout=15,
        )
        if r.status_code == 404:
            return []
        r.raise_for_status()
        return r.json().get("data", [])
    except requests.RequestException as e:
        logger.warning("Failed to fetch Viblo tag %s page %d: %s", tag, page, e)
        return []


def _fetch_post_content(slug: str) -> str:
    try:
        r = requests.get(
            f"{VIBLO_API}/posts/{slug}",
            headers=HEADERS,
            timeout=15,
        )
        r.raise_for_status()
        data = r.json().get("data", {})
        # content_html or content_markdown
        content = data.get("content", "") or data.get("content_html", "")
        # Strip basic HTML tags for plain text
        import re
        content = re.sub(r"<[^>]+>", " ", content)
        content = re.sub(r"\s+", " ", content).strip()
        return content
    except requests.RequestException as e:
        logger.warning("Failed to fetch content for %s: %s", slug, e)
        return ""


def _post_to_article(post: dict) -> dict:
    slug = post.get("slug", "")
    title = post.get("title", "")
    # API trả url trực tiếp
    source_url = post.get("url") or f"https://viblo.asia/p/{slug}"
    published = post.get("published_at", "") or post.get("created_at", "")
    # contents_short là snippet duy nhất từ API (không expose full content)
    content = post.get("contents_short", "") or post.get("transliterated", "")
    # Thêm tags vào content để tăng entity extraction
    tags_data = post.get("tags", {})
    if isinstance(tags_data, dict):
        tags = [t.get("name", "") for t in tags_data.get("data", [])]
        if tags:
            content += f"\nTags: {', '.join(tags)}"
    return {
        "slug": slug,
        "title": title,
        "source_url": source_url,
        "publish_date": published[:10] if published else "",
        "content": content,
    }


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

    for tag in TAGS:
        if total >= MAX_ARTICLES:
            break
        for page in range(1, 4):  # tối đa 3 trang mỗi tag
            if total >= MAX_ARTICLES:
                break
            posts = _fetch_posts_by_tag(tag, page)
            if not posts:
                break

            for post in posts:
                if total >= MAX_ARTICLES:
                    break

                art = _post_to_article(post)
                if not art["title"] or art["source_url"] in processed:
                    continue

                content = art["content"]
                if len(content) < 20:
                    continue

                articles.append(art)
                _save_url(URL_CACHE, art["source_url"])
                processed.add(art["source_url"])

                if kafka_enabled:
                    kafka.send_article(
                        title=art["title"], content=content,
                        source_url=art["source_url"], source_platform=SOURCE_PLATFORM,
                        publish_date=art["publish_date"],
                    )

                total += 1
                logger.info("[%d] [%s] %s", total, tag, art["title"][:60])

            time.sleep(0.5)

    kafka.close()

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump({"source_platform": SOURCE_PLATFORM, "post_detail": articles}, f,
                  ensure_ascii=False, indent=2)

    logger.info("Viblo done: %d articles", total)


if __name__ == "__main__":
    main()
