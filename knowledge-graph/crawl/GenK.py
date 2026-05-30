from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.common.exceptions import NoSuchElementException, StaleElementReferenceException, TimeoutException, WebDriverException
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service
from webdriver_manager.chrome import ChromeDriverManager
import time
import os
from datetime import datetime
import re
import json
import gc

# Kafka producer integration
from kafka_producer import CrawlerKafkaProducer


def extract_publish_date(driver):
    candidates = []
    # GenK specific selectors
    for selector in ["time-source-detail", "kbwcm-time", "time", "span.date", "span.date-time", "div.date-time"]:
        try:
            elements = driver.find_elements(By.CLASS_NAME, selector) if "." not in selector else driver.find_elements(By.CSS_SELECTOR, selector)
            for el in elements:
                text = el.text.strip()
                if text:
                    candidates.append(text)
        except NoSuchElementException:
            continue

    for text in candidates:
        match = re.search(r"(\d{1,2}/\d{1,2}/\d{4})", text)
        if match:
            return match.group(1)
    return ""


def load_processed_urls(url_cache_file):
    if os.path.exists(url_cache_file):
        with open(url_cache_file, "r", encoding="utf-8") as f:
            return set(line.strip() for line in f if line.strip())
    return set()


def save_processed_url(url_cache_file, url):
    with open(url_cache_file, "a", encoding="utf-8") as f:
        f.write(url + "\n")


def load_existing_posts(output_file):
    """Load existing posts from JSON file to avoid duplicates."""
    if os.path.exists(output_file):
        try:
            with open(output_file, "r", encoding="utf-8") as f:
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


def _view_more_visible(driver):
    for btn in driver.find_elements(By.CSS_SELECTOR, "a.btnviewmore"):
        try:
            text = (btn.text or "").strip()
            if "Xem thêm" not in text:
                continue
            driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", btn)
            time.sleep(0.3)
            if btn.is_displayed():
                return True, btn
        except StaleElementReferenceException:
            continue
    return False, None


MAX_ARTICLES = 150


def main():
    # Initialize Kafka producer
    kafka_producer = CrawlerKafkaProducer()
    kafka_enabled = False
    try:
        kafka_enabled = kafka_producer.connect()
        if kafka_enabled:
            print("✓ Kafka connected for GenK")
        else:
            print("⚠ Kafka not available, data will only be saved to JSON")
    except Exception as e:
        print(f"⚠ Kafka connection failed: {e}")
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
    chrome_options.add_argument("--remote-debugging-port=9222")
    
    # Use webdriver-manager to automatically download and manage ChromeDriver
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=chrome_options)
    driver.set_page_load_timeout(30)

    source_urls = [
        "https://genk.vn/ai.chn",
        "https://genk.vn/internet.chn",
        "https://genk.vn/tin-ict.chn",
    ]

    today_str = datetime.now().strftime("%d_%m_%Y")
    # Changed: Save to data/raw/ instead of crawl/data/raw/
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    output_dir = os.path.join(base_dir, "data", "raw", "genk")
    metadata_dir = os.path.join(output_dir, "metadata")
    os.makedirs(output_dir, exist_ok=True)
    os.makedirs(metadata_dir, exist_ok=True)
    
    output_file = os.path.join(output_dir, f"{today_str}.json")
    url_cache_file = os.path.join(metadata_dir, f"{today_str}_urls.txt")

    processed_urls = load_processed_urls(url_cache_file)
    print(f"Đã xử lý trước đó: {len(processed_urls)} bài")
    
    # Load existing posts
    existing_posts = load_existing_posts(output_file)
    existing_urls = {p.get("source_url") for p in existing_posts}
    print(f"Bài đã có trong file: {len(existing_posts)} bài")

    print(f"File output: {output_file}")

    seen_links = set()
    posts_info = []

    for source_url in source_urls:
        print(f"\n--- Cào: {source_url} ---")
        
        try:
            driver.get(source_url)
            time.sleep(2)
        except (TimeoutException, WebDriverException):
            continue

        max_scrolls = 50
        scroll_count = 0
        last_height = 0
        
        while scroll_count < max_scrolls:
            visible, _ = _view_more_visible(driver)
            if visible:
                break
            driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
            time.sleep(1)
            height = driver.execute_script("return document.body.scrollHeight;")
            if height <= last_height:
                break
            last_height = height
            scroll_count += 1

        driver.execute_script("window.scrollTo(0, 0);")
        time.sleep(1)

        articles = driver.find_elements(By.CSS_SELECTOR, "div.elp-list")
        added = 0
        
        for article in articles:
            if len(posts_info) >= MAX_ARTICLES:
                break

            try:
                link_els = article.find_elements(By.CSS_SELECTOR, "h4.knswli-title a")
                title_els = article.find_elements(By.CSS_SELECTOR, "h4.knswli-title")
                link = link_els[0].get_attribute("href") if link_els else ""
                title = title_els[0].text.strip() if title_els else ""

                if not link or "eclick" in link or link in seen_links or link in processed_urls or link in existing_urls:
                    continue
                seen_links.add(link)
                posts_info.append({"title": title, "link": link})
                added += 1
            except NoSuchElementException:
                continue

        print(f"  Thêm {added} bài")
        del articles
        gc.collect()

        if len(posts_info) >= MAX_ARTICLES:
            break

    posts_info = posts_info[:MAX_ARTICLES]
    print(f"\nTổng: {len(posts_info)} bài sẽ cào")

    if not posts_info:
        print("Không tìm thấy bài viết mới!")
        driver.quit()
        exit(0)

    new_posts = []
    
    for idx, post in enumerate(posts_info):
        print(f"\n[{idx + 1}/{len(posts_info)}] {post['title'][:40] if post['title'] else '...'}...")

        try:
            driver.get(post["link"])
            time.sleep(1)
        except (TimeoutException, WebDriverException):
            continue

        publish_date = extract_publish_date(driver)

        try:
            article = driver.find_element(By.ID, "ContentDetail")
            paragraphs = article.find_elements(By.CSS_SELECTOR, "p")
            content = "\n\n".join([p.text.strip() for p in paragraphs if p.text.strip()])
        except NoSuchElementException:
            continue

        post_detail = {
            "title": post["title"],
            "publish_date": publish_date,
            "content": content,
            "source_url": post["link"]
        }
        
        new_posts.append(post_detail)
        save_processed_url(url_cache_file, post["link"])
        
        # Send to Kafka
        if kafka_enabled:
            kafka_producer.send_article(
                title=post["title"],
                content=content,
                source_url=post["link"],
                source_platform="GenK",
                publish_date=publish_date
            )
        
        print(f"  ✓ Đã lưu (tổng: {len(new_posts)})")
        
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

    print(f"\n{'='*50}")
    print(f"Hoàn thành! Đã lưu: {len(new_posts)} bài mới")
    print(f"Tổng cộng trong file: {len(all_posts)} bài")
    print(f"File: {output_file}")
    print(f"{'='*50}")


if __name__ == "__main__":
    main()