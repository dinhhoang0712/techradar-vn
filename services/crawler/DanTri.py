from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.common.exceptions import NoSuchElementException, TimeoutException, WebDriverException
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import Select
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
    """Trích xuất ngày đăng từ trang DanTri."""
    try:
        time_elem = driver.find_element(By.CSS_SELECTOR, "time")
        date_text = time_elem.text.strip()
    except NoSuchElementException:
        try:
            time_elem = driver.find_element(By.CSS_SELECTOR, "div.article-time")
            date_text = time_elem.text.strip()
        except NoSuchElementException:
            return ""

    match = re.search(r"(\d{1,2}/\d{1,2}/\d{4})", date_text)
    if match:
        return match.group(1)
    return ""


def load_processed_urls(url_cache_file):
    """Load URLs đã xử lý từ file cache."""
    if os.path.exists(url_cache_file):
        with open(url_cache_file, "r", encoding="utf-8") as f:
            return set(line.strip() for line in f if line.strip())
    return set()


def save_processed_url(url_cache_file, url):
    """Lưu URL đã xử lý vào file cache."""
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


MAX_ARTICLES = 150


def main():
    # Initialize Kafka producer
    kafka_producer = CrawlerKafkaProducer()
    kafka_enabled = False
    try:
        kafka_enabled = kafka_producer.connect()
        if kafka_enabled:
            print("✓ Kafka connected for DanTri")
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
    chrome_options.add_argument("--disable-background-networking")
    chrome_options.add_argument("--disable-default-apps")
    chrome_options.add_argument("--disable-sync")
    chrome_options.add_argument("--metrics-recording-only")
    chrome_options.add_argument("--mute-audio")
    chrome_options.page_load_strategy = "eager"

    # Use webdriver-manager to automatically download and manage ChromeDriver
    service = Service(ChromeDriverManager().install())
    driver = webdriver.Chrome(service=service, options=chrome_options)
    driver.set_page_load_timeout(60)

    sources = [
        {"url": "https://dantri.com.vn/cong-nghe/ai-internet.htm", "name": "DanTri_AI", "num_pages": 1},
        {"url": "https://dantri.com.vn/cong-nghe.htm", "name": "DanTri_CongNghe", "num_pages": 1}
    ]

    today_str = datetime.now().strftime("%d_%m_%Y")
    # Changed: Save to data/raw/ instead of crawl/data/raw/
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    output_dir = os.path.join(base_dir, "data", "raw", "dantri")
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

    all_posts = []
    seen_links_global = set()

    for source in sources:
        print(f"\n--- Thu thập link từ: {source['name']} ---")
        
        try:
            driver.get(source["url"])
            time.sleep(3)
        except Exception:
            print(f"  ❌ Lỗi kết nối {source['name']}")
            continue

        posts_info = []
        for page in range(1, source["num_pages"] + 1):
            if len(all_posts) + len(posts_info) >= MAX_ARTICLES:
                break

            page_url = source["url"] if page == 1 else source["url"].replace(".htm", f"/trang-{page}.htm")
            
            try:
                driver.get(page_url)
                time.sleep(2)
            except Exception:
                continue

            articles = driver.find_elements(By.CSS_SELECTOR, "div.article-content")
            for article in articles:
                try:
                    title_els = article.find_elements(By.CSS_SELECTOR, 'h3.article-title')
                    link = article.find_element(By.CSS_SELECTOR, 'a[data-prop="sapo"]').get_attribute("href")
                    title = title_els[0].text if title_els else ""

                    if not link or "eclick" in link or link in seen_links_global or link in processed_urls or link in existing_urls:
                        continue

                    seen_links_global.add(link)
                    posts_info.append({"title": title, "link": link})
                except NoSuchElementException:
                    continue

        print(f"  Thu thập: {len(posts_info)} link")
        all_posts.extend(posts_info)
        
        del posts_info
        gc.collect()

    all_posts = all_posts[:MAX_ARTICLES]
    print(f"\nTổng: {len(all_posts)} bài sẽ cào")

    if not all_posts:
        print("Không tìm thấy bài viết mới!")
        driver.quit()
        exit(0)

    new_posts = []
    
    for idx, post in enumerate(all_posts):
        print(f"\n[{idx + 1}/{len(all_posts)}] {post['title'][:40] if post['title'] else '...'}...")

        try:
            driver.get(post['link'])
            time.sleep(1)
        except Exception:
            print(f"  ❌ Lỗi kết nối")
            continue

        publish_date = extract_publish_date(driver)

        try:
            article = driver.find_element(By.ID, "desktop-in-article")
            paragraphs = article.find_elements(By.CSS_SELECTOR, "p")
            content = "\n\n".join([p.text.strip() for p in paragraphs if p.text.strip()])
        except NoSuchElementException:
            print(f"  ⚠ Không có nội dung")
            continue

        post_detail = {
            "title": post['title'],
            "publish_date": publish_date,
            "content": content,
            "source_url": post['link']
        }

        new_posts.append(post_detail)
        save_processed_url(url_cache_file, post['link'])
        processed_urls.add(post['link'])
        
        # Send to Kafka
        if kafka_enabled:
            kafka_producer.send_article(
                title=post['title'],
                content=content,
                source_url=post['link'],
                source_platform="DanTri",
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
    all_saved_posts = existing_posts + new_posts
    save_posts(output_file, all_saved_posts)

    print(f"\n{'='*50}")
    print(f"Hoàn thành! Đã lưu: {len(new_posts)} bài mới")
    print(f"Tổng cộng trong file: {len(all_saved_posts)} bài")
    print(f"File: {output_file}")
    print(f"{'='*50}")


if __name__ == "__main__":
    main()