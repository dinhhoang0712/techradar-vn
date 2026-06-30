"""
ICTNews crawler — ictnews.vn
Crawl tin công nghệ từ ICTNews, đẩy vào Kafka topic raw_articles.
"""
import gc
import json
import logging
import os
import re
import time
from datetime import datetime

from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from selenium.common.exceptions import NoSuchElementException, TimeoutException, WebDriverException
from webdriver_manager.chrome import ChromeDriverManager

from kafka_producer import CrawlerKafkaProducer

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

SOURCE_PLATFORM = "ICTNews"
MAX_ARTICLES = 150

CATEGORIES = [
    "https://ictnews.vietnamnet.vn/cong-nghe",
    "https://ictnews.vietnamnet.vn/vien-thong",
    "https://ictnews.vietnamnet.vn/khoi-nghiep",
]

DATA_DIR = os.path.join(os.path.dirname(__file__), "data", "raw", "ictnews")
URL_CACHE = os.path.join(DATA_DIR, "processed_urls.txt")


def _load_urls(path: str) -> set:
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return {l.strip() for l in f if l.strip()}
    return set()


def _save_url(path: str, url: str):
    with open(path, "a", encoding="utf-8") as f:
        f.write(url + "\n")


def _chrome_options() -> Options:
    opts = Options()
    opts.add_argument("--headless=new")
    opts.add_argument("--no-sandbox")
    opts.add_argument("--disable-dev-shm-usage")
    opts.add_argument("--disable-gpu")
    opts.add_argument("--window-size=1920,1080")
    opts.add_argument("--disable-background-networking")
    opts.add_argument("--disable-default-apps")
    opts.add_argument("--disable-sync")
    opts.add_argument("--metrics-recording-only")
    opts.add_argument("--mute-audio")
    opts.add_argument("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
    opts.page_load_strategy = "eager"
    return opts


def _extract_date(driver) -> str:
    for sel in ["time.time-post", "span.time", "p.time", "time"]:
        try:
            text = driver.find_element(By.CSS_SELECTOR, sel).text.strip()
            m = re.search(r"(\d{1,2}/\d{1,2}/\d{4})", text)
            if m:
                return m.group(1)
        except NoSuchElementException:
            continue
    return ""


def _extract_content(driver) -> str:
    for sel in ["div.maincontent", "div.article__body", "article", "div.content-detail"]:
        try:
            el = driver.find_element(By.CSS_SELECTOR, sel)
            paras = el.find_elements(By.TAG_NAME, "p")
            text = " ".join(p.text.strip() for p in paras if p.text.strip())
            if len(text) > 100:
                return text
        except NoSuchElementException:
            continue
    return ""


def _collect_article_urls(driver, category_url: str, processed: set, max_pages: int = 5) -> list:
    urls = []
    for page in range(1, max_pages + 1):
        url = category_url if page == 1 else f"{category_url}/p{page}"
        try:
            driver.get(url)
            time.sleep(2)
            links = driver.find_elements(By.CSS_SELECTOR, "h3 a, h2 a, .title-news a")
            for a in links:
                href = a.get_attribute("href") or ""
                if href and href.startswith("http") and href not in processed:
                    urls.append(href)
        except (TimeoutException, WebDriverException) as e:
            logger.warning("Failed to load category page %s: %s", url, e)
            break
    return list(dict.fromkeys(urls))  # dedup, preserve order


def main():
    os.makedirs(DATA_DIR, exist_ok=True)

    kafka = CrawlerKafkaProducer()
    kafka_enabled = False
    try:
        kafka_enabled = kafka.connect()
        logger.info("Kafka %s", "connected" if kafka_enabled else "not available (CSV only)")
    except Exception as e:
        logger.warning("Kafka error: %s", e)

    processed = _load_urls(URL_CACHE)
    output_file = os.path.join(DATA_DIR, f"{datetime.now().strftime('%d_%m_%Y')}.json")
    posts = []
    total = 0

    driver = webdriver.Chrome(
        service=Service(ChromeDriverManager().install()),
        options=_chrome_options(),
    )

    try:
        for category_url in CATEGORIES:
            if total >= MAX_ARTICLES:
                break
            article_urls = _collect_article_urls(driver, category_url, processed)
            logger.info("Category %s: %d new URLs", category_url, len(article_urls))

            for art_url in article_urls:
                if total >= MAX_ARTICLES:
                    break
                try:
                    driver.get(art_url)
                    time.sleep(1.5)

                    title = ""
                    for sel in ["h1.title-detail", "h1.article__title", "h1"]:
                        try:
                            title = driver.find_element(By.CSS_SELECTOR, sel).text.strip()
                            if title:
                                break
                        except NoSuchElementException:
                            continue

                    content = _extract_content(driver)
                    publish_date = _extract_date(driver)

                    if not title or len(content) < 100:
                        continue

                    posts.append({"title": title, "content": content,
                                  "source_url": art_url, "publish_date": publish_date})
                    _save_url(URL_CACHE, art_url)
                    processed.add(art_url)

                    if kafka_enabled:
                        kafka.send_article(
                            title=title, content=content,
                            source_url=art_url, source_platform=SOURCE_PLATFORM,
                            publish_date=publish_date,
                        )

                    total += 1
                    logger.info("[%d] %s", total, title[:60])

                except Exception as e:
                    logger.warning("Failed article %s: %s", art_url, type(e).__name__)

    finally:
        driver.quit()
        gc.collect()
        kafka.close()

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump({"source_platform": SOURCE_PLATFORM, "post_detail": posts}, f,
                  ensure_ascii=False, indent=2)

    logger.info("ICTNews done: %d articles", total)


if __name__ == "__main__":
    main()
