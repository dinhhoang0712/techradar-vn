import gc
import json
import logging
import os
import re
import time
from datetime import datetime

from kafka_producer import CrawlerKafkaProducer
from selenium import webdriver
from selenium.common.exceptions import NoSuchElementException
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.common.by import By
from webdriver_manager.chrome import ChromeDriverManager

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)


def load_processed_urls(url_cache_file):
    if os.path.exists(url_cache_file):
        with open(url_cache_file, encoding="utf-8") as f:
            return set(line.strip() for line in f if line.strip())
    return set()


def save_processed_url(url_cache_file, url):
    with open(url_cache_file, "a", encoding="utf-8") as f:
        f.write(url + "\n")


def load_existing_posts(output_file):
    """Load existing posts from JSON file to avoid duplicates."""
    if os.path.exists(output_file):
        try:
            with open(output_file, encoding="utf-8") as f:
                data = json.load(f)
                return data.get("post_detail", [])
        except (json.JSONDecodeError, Exception):
            return []
    return []


def save_posts(output_file, posts):
    """Save all posts to JSON file with proper structure."""
    data = {"post_detail": posts}
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def main():
    # Initialize Kafka producer
    kafka_producer = CrawlerKafkaProducer()
    kafka_enabled = False
    try:
        kafka_enabled = kafka_producer.connect()
        if kafka_enabled:
            logger.info("Kafka connected for VNExpress")
        else:
            logger.warning("Kafka not available, data will only be saved to JSON")
    except Exception as e:
        logger.warning("Kafka connection failed: %s", e)
        kafka_enabled = False

    chrome_options = Options()
    chrome_options.add_argument("--headless=new")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--disable-gpu")
    chrome_options.add_argument("--window-size=1920,1080")
    chrome_options.add_argument("--disable-extensions")
    chrome_options.add_argument("--disable-images")
    chrome_options.add_argument("--disable-software-rasterizer")
    chrome_options.add_argument("--disable-background-networking")
    chrome_options.add_argument("--disable-default-apps")
    chrome_options.add_argument("--disable-sync")
    chrome_options.add_argument("--metrics-recording-only")
    chrome_options.add_argument("--mute-audio")
    # eager: return khi DOM ready, không chờ images/JS load xong → tránh timeout
    chrome_options.page_load_strategy = "eager"

    # Use webdriver-manager to automatically download and manage ChromeDriver
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=chrome_options)
    driver.set_page_load_timeout(60)

    source_url = "https://vnexpress.net/khoa-hoc-cong-nghe"
    num_pages = 2

    today_str = datetime.now().strftime("%d_%m_%Y")
    # data/raw/ under the container's /app — matches the crawler_data:/app/data volume mount
    # (docker-compose.yml), so output survives container restarts/recreates.
    base_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(base_dir, "data", "raw", "vnexpress")
    metadata_dir = os.path.join(output_dir, "metadata")
    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(metadata_dir, exist_ok=True)

    output_file = os.path.join(output_dir, f"{today_str}.json")
    url_cache_file = os.path.join(metadata_dir, f"{today_str}_urls.txt")

    processed_urls = load_processed_urls(url_cache_file)
    logger.info("Đã xử lý trước đó: %d bài", len(processed_urls))

    # Load existing posts
    existing_posts = load_existing_posts(output_file)
    existing_urls = {p.get("source_url") for p in existing_posts}
    logger.info("Bài đã có trong file: %d bài", len(existing_posts))

    logger.info("File output: %s", output_file)

    try:
        driver.get(source_url)
        time.sleep(3)
    except Exception as e:
        logger.error("Lỗi khi tải trang: %s", e)
        driver.quit()
        exit(1)

    seen_links = set()
    posts_info = []

    for page in range(1, num_pages + 1):
        logger.info("--- Trang %d ---", page)

        base_url = source_url.rstrip("/")
        if "-p" in base_url:
            base_url = base_url.rsplit("-p", 1)[0]
        page_url = base_url if page == 1 else f"{base_url}-p{page}"

        try:
            driver.get(page_url)
            time.sleep(2)
        except Exception:
            continue

        articles = driver.find_elements(By.CSS_SELECTOR, "article.item-news.item-news-common.thumb-left:not(.hidden)")
        if not articles:
            articles = driver.find_elements(By.CSS_SELECTOR, "article.item-news")

        added = 0
        for article in articles:
            title = ""
            link = ""

            title_els = article.find_elements(By.CSS_SELECTOR, "h2.title-news a")
            if title_els:
                title = title_els[0].text
                link = title_els[0].get_attribute("href")

            if not link:
                title_els = article.find_elements(By.CSS_SELECTOR, "h3.title-news a")
                if title_els:
                    title = title_els[0].text
                    link = title_els[0].get_attribute("href")

            if not link:
                link_els = article.find_elements(By.CSS_SELECTOR, 'a[href*="vnexpress.net"]')
                if link_els:
                    link = link_els[0].get_attribute("href")
                    title = link_els[0].text.strip()

            if not link or "eclick" in link or link in seen_links or link in processed_urls or link in existing_urls:
                continue

            seen_links.add(link)
            posts_info.append({"title": title, "link": link})
            added += 1

        logger.info("Thêm %d bài", added)
        del articles
        gc.collect()

    logger.info("Tổng: %d bài sẽ cào", len(posts_info))

    if not posts_info:
        logger.info("Không tìm thấy bài viết mới!")
        driver.quit()
        exit(0)

    new_posts = []

    for idx, post in enumerate(posts_info):
        logger.info("[%d/%d] %s...", idx + 1, len(posts_info), post["title"][:40] if post["title"] else "...")

        try:
            driver.get(post["link"])
            time.sleep(1)
        except Exception:
            continue

        content = ""
        try:
            article = driver.find_element(By.ID, "fck_detail_gallery")
            paragraphs = article.find_elements(By.CSS_SELECTOR, "p.Normal")
            content = "\n\n".join([p.text.strip() for p in paragraphs if p.text.strip()])
        except NoSuchElementException:
            try:
                article = driver.find_element(By.CSS_SELECTOR, "article.fck_detail")
                paragraphs = article.find_elements(By.CSS_SELECTOR, "p")
                content = "\n\n".join([p.text.strip() for p in paragraphs if p.text.strip()])
            except NoSuchElementException:
                try:
                    article = driver.find_element(By.CSS_SELECTOR, "div.detail-content")
                    paragraphs = article.find_elements(By.CSS_SELECTOR, "p")
                    content = "\n\n".join([p.text.strip() for p in paragraphs if p.text.strip()])
                except NoSuchElementException:
                    continue

        if not content:
            continue

        title = post["title"]
        if not title:
            try:
                title_el = driver.find_element(By.CSS_SELECTOR, "h1.title-detail")
                title = title_el.text.strip()
            except NoSuchElementException:
                try:
                    title_el = driver.find_element(By.CSS_SELECTOR, "h1")
                    title = title_el.text.strip()
                except NoSuchElementException:
                    pass

        publish_date = ""
        try:
            date_el = driver.find_element(By.CSS_SELECTOR, "span.date")
            match = re.search(r"(\d{1,2}/\d{1,2}/\d{4})", date_el.text.strip())
            if match:
                publish_date = match.group(1)
        except NoSuchElementException:
            try:
                date_el = driver.find_element(By.CSS_SELECTOR, "time")
                date_text = date_el.get_attribute("datetime") or date_el.text.strip()
                match = re.search(r"(\d{1,2}/\d{1,2}/\d{4})", date_text)
                if match:
                    publish_date = match.group(1)
            except NoSuchElementException:
                pass

        post_detail = {"title": title, "publish_date": publish_date, "content": content, "source_url": post["link"]}

        new_posts.append(post_detail)
        save_processed_url(url_cache_file, post["link"])

        # Send to Kafka
        if kafka_enabled:
            kafka_producer.send_article(
                title=title,
                content=content,
                source_url=post["link"],
                source_platform="VNExpress",
                publish_date=publish_date,
            )

        logger.info("Đã lưu (tổng: %d)", len(new_posts))

        del post_detail, content, paragraphs
        if idx % 10 == 0:
            gc.collect()

    driver.quit()

    # Close Kafka producer
    if kafka_producer:
        kafka_producer.flush()
        kafka_producer.close()

    # Save all posts (existing + new) to JSON file
    all_posts = existing_posts + new_posts
    save_posts(output_file, all_posts)

    logger.info("=" * 50)
    logger.info("Hoàn thành! Đã lưu: %d bài mới", len(new_posts))
    logger.info("Tổng cộng trong file: %d bài", len(all_posts))
    logger.info("File: %s", output_file)
    logger.info("=" * 50)


if __name__ == "__main__":
    main()
