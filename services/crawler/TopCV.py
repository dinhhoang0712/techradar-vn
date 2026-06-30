from selenium import webdriver
import undetected_chromedriver as uc
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.common.exceptions import NoSuchElementException, TimeoutException, WebDriverException
from selenium.webdriver.chrome.options import Options
from fake_useragent import UserAgent
import time
import random
import os
import re
from datetime import datetime
import gc
import logging

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# Random user agents
UA = UserAgent()

# Anti-detection user agents list
USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
]

# Kafka producer integration
from kafka_producer import CrawlerKafkaProducer


def safe_find(driver, css):
    try:
        return driver.find_element(By.CSS_SELECTOR, css).text.strip()
    except NoSuchElementException:
        return ""


def safe_find_from(root, css):
    try:
        return root.find_element(By.CSS_SELECTOR, css).text.strip()
    except NoSuchElementException:
        return ""


def extract_label_value(text, label, stop_labels=None):
    stop_labels = stop_labels or []
    pattern = rf"(?i){re.escape(label)}\s*[:\-–]?\s*(.*?)(?=(?:{'|'.join(re.escape(lbl) for lbl in stop_labels)})|$)"
    match = re.search(pattern, text, re.IGNORECASE | re.DOTALL)
    if not match:
        return ""
    value = match.group(1).strip()
    value = re.sub(r"^[|\s:]+|[|\s:]+$", "", value)
    return value


def find_label_value(root, labels, stop_labels=None):
    for label in labels:
        for el in root.find_elements(By.XPATH, f".//*[contains(normalize-space(string()), '{label}')]"):
            text = el.text.strip()
            if label.lower() in text.lower():
                value = extract_label_value(text, label, stop_labels)
                if value:
                    return value
    return ""


def extract_section(driver, keywords):
    for h3 in driver.find_elements(By.TAG_NAME, "h3"):
        text = h3.text.strip().lower()
        for kw in keywords:
            if kw in text:
                try:
                    section = h3.find_element(By.XPATH, "following-sibling::*[1]")
                    return section.text.strip()
                except NoSuchElementException:
                    continue
    return ""


def find_job_info_root(driver):
    for selector in [".job-detail__info", ".job-detail__info-wrap", ".job-detail__header", ".job-detail"]:
        try:
            return driver.find_element(By.CSS_SELECTOR, selector)
        except NoSuchElementException:
            continue
    return driver


def find_company_info_root(driver):
    for selector in [".job-detail__body-right", ".company-profile", ".company-info"]:
        try:
            return driver.find_element(By.CSS_SELECTOR, selector)
        except NoSuchElementException:
            continue
    return driver


def clean_field_value(value):
    value = re.sub(r'(?i)L[ií]nh v[uụ]c\s*[:\-–]?\s*', '', value).strip()
    parts = value.split("\n")
    return parts[1] if len(parts) > 1 else value


def load_processed_urls(url_cache_file):
    if os.path.exists(url_cache_file):
        with open(url_cache_file, "r", encoding="utf-8") as f:
            return set(line.strip() for line in f if line.strip())
    return set()


def save_processed_url(url_cache_file, url):
    with open(url_cache_file, "a", encoding="utf-8") as f:
        f.write(url + "\n")


# CSV fieldnames for jobs
JOB_FIELDNAMES = ["title", "description", "requirement", "benefit", "location", "due_date", "Company", "size", "field", "source_url"]


def scrape_job_details(driver):
    details = {}
    root = find_job_info_root(driver)
    
    details['description'] = extract_section(driver, ["mô tả công việc", "mô tả"]) or ""
    details['requirement'] = extract_section(driver, ["yêu cầu ứng viên", "yêu cầu"]) or ""
    details['benefit'] = extract_section(driver, ["quyền lợi", "phúc lợi"]) or ""
    
    details['location'] = (
        safe_find_from(root, ".job-detail__info--location")
        or find_label_value(root, ["Địa điểm"], ["Kinh nghiệm", "Hạn nộp"])
        or ""
    )
    
    due_date_raw = safe_find_from(root, ".job-detail__info--deadline") or ""
    if due_date_raw:
        match = re.search(r"\d{1,2}/\d{1,2}/\d{4}", due_date_raw)
        details['due_date'] = match.group(0) if match else due_date_raw.strip()
    else:
        details['due_date'] = ""
    
    company_root = find_company_info_root(driver)
    company_text = company_root.text.strip()
    
    details['Company'] = (
        safe_find_from(company_root, ".company-name")
        or extract_label_value(company_text, "Công ty", ["Quy mô", "Lĩnh vực"])
        or ""
    )
    
    details['size'] = extract_label_value(company_text, "Quy mô", ["Lĩnh vực"]) or ""
    
    field_value = extract_label_value(company_text, "Lĩnh vực", []) or ""
    details['field'] = clean_field_value(field_value)
    
    return details


def main():
    # Initialize Kafka producer
    kafka_producer = CrawlerKafkaProducer()
    kafka_enabled = False
    try:
        kafka_enabled = kafka_producer.connect()
        if kafka_enabled:
            print("✓ Kafka connected for TopCV")
        else:
            print("⚠ Kafka not available, data will only be saved to CSV")
    except Exception as e:
        print(f"⚠ Kafka connection failed: {e}")
        kafka_enabled = False
    
    # Configure Chrome options with anti-detection.
    chrome_args = [
        # Basic options
        "--headless=new",
        "--no-sandbox",
        "--disable-dev-shm-usage",
        "--disable-gpu",
        "--window-size=1920,1080",
        # Anti-detection options
        "--disable-blink-features=AutomationControlled",
        "--disable-infobars",
        "--disable-extensions",
        "--disable-popup-blocking",
        "--disable-notifications",
    ]
    # Random user agent
    user_agent = random.choice(USER_AGENTS)
    logger.info(f"Using User-Agent: {user_agent[:50]}...")
    # Disable images for faster loading (simplified prefs)
    prefs = {
        "profile.managed_default_content_settings.images": 2,
    }

    def _build_options(factory):
        """Build a fresh Options object — an Options instance can only be
        consumed by one driver, so each attempt needs its own."""
        opts = factory()
        for arg in chrome_args:
            opts.add_argument(arg)
        opts.add_argument(f"--user-agent={user_agent}")
        opts.add_argument("--disable-background-networking")
        opts.add_argument("--metrics-recording-only")
        opts.add_argument("--mute-audio")
        try:
            opts.add_experimental_option("prefs", prefs)
        except Exception:
            pass  # uc.ChromeOptions may not support experimental options
        opts.page_load_strategy = "eager"
        return opts

    try:
        driver = uc.Chrome(options=_build_options(uc.ChromeOptions), version_main=None)
        logger.info("✓ Undetected ChromeDriver initialized successfully")
    except Exception as e:
        logger.warning(f"⚠ Undetected ChromeDriver failed: {e}, trying regular Chrome")
        # Fallback to regular Chrome with a fresh selenium Options object.
        from selenium import webdriver
        from selenium.webdriver.chrome.options import Options as ChromeOptions
        driver = webdriver.Chrome(options=_build_options(ChromeOptions))
    
    # Execute stealth scripts
    stealth_js = """
    Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
    Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});
    Object.defineProperty(navigator, 'languages', {get: () => ['vi-VN', 'vi', 'en']});
    window.chrome = {runtime: {}};
    """
    try:
        driver.execute_cdp_cmd('Page.addScriptToEvaluateOnNewDocument', {'source': stealth_js})
    except:
        pass

    base_url_page1 = "https://www.topcv.vn/tim-viec-lam-cong-nghe-thong-tin-cr257?type_keyword=1&category_family=r257&saturday_status=0"
    base_url_paged = "https://www.topcv.vn/tim-viec-lam-cong-nghe-thong-tin-cr257?type_keyword=1&page={page}&category_family=r257&saturday_status=0"
    num_pages = 1

    today_str = datetime.now().strftime("%d_%m_%Y")
    base_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(base_dir, "data", "raw", "topcv")
    os.makedirs(output_dir, exist_ok=True)
    output_file = os.path.join(output_dir, f"{today_str}.csv")
    url_cache_file = os.path.join(output_dir, f"{today_str}_urls.txt")

    processed_urls = load_processed_urls(url_cache_file)
    print(f"Đã xử lý trước đó: {len(processed_urls)} bài")

    # Initialize CSV file with headers if not exists
    import csv
    if not os.path.exists(output_file):
        with open(output_file, "w", encoding="utf-8", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=JOB_FIELDNAMES)
            writer.writeheader()

    print(f"File output: {output_file}")

    try:
        driver.get(base_url_page1)
    except Exception as e:
        print(f"[ERROR] Failed to load TopCV listing page: {type(e).__name__}")
        driver.quit()
        exit(1)

    seen_links = set()
    total_articles = 0

    for page in range(1, num_pages + 1):
        print(f"\n--- Trang {page}/{num_pages} ---")

        page_url = base_url_page1 if page == 1 else base_url_paged.format(page=page)
        driver.get(page_url)
        time.sleep(random.uniform(2, 4))

        elements_links = driver.find_elements(By.CSS_SELECTOR, "h3.title a")
        links = []
        for e in elements_links:
            href = e.get_attribute("href")
            if href and href not in seen_links and href not in processed_urls:
                seen_links.add(href)
                links.append(href)

        print(f"  Tìm thấy {len(links)} bài")

        for idx, link in enumerate(links):
            print(f"\n  [{idx + 1}/{len(links)}] {link.split('/')[-1][:30]}...")

            try:
                driver.get(link)
                time.sleep(random.uniform(3, 5))

                title = safe_find(driver, "h1.job-detail__info--title")
                if not title:
                    title = safe_find(driver, "h1")
                    if not title:
                        continue

                details = scrape_job_details(driver)
                post_detail = {
                    "title": title,
                    "description": details.get('description', ''),
                    "requirement": details.get('requirement', ''),
                    "benefit": details.get('benefit', ''),
                    "location": details.get('location', ''),
                    "due_date": details.get('due_date', ''),
                    "Company": details.get('Company', ''),
                    "size": details.get('size', ''),
                    "field": details.get('field', ''),
                    "source_url": link
                }
                
                # Save to CSV
                with open(output_file, "a", encoding="utf-8", newline="") as f:
                    writer = csv.DictWriter(f, fieldnames=JOB_FIELDNAMES, extrasaction='ignore')
                    writer.writerow(post_detail)
                
                save_processed_url(url_cache_file, link)
                
                # Send to Kafka (job data)
                if kafka_enabled:
                    kafka_producer.send_job(
                        job_title=title,
                        company_name=details.get('Company', ''),
                        location=details.get('location', ''),
                        salary="",  # TopCV doesn't show salary in listing
                        level="",   # Could be extracted from title
                        description=details.get('description', ''),
                        requirement=details.get('requirement', ''),
                        benefit=details.get('benefit', ''),
                        skills=[],  # Could be extracted from description
                        source_url=link,
                        posted_date="",
                        source_platform="TopCV"
                    )
                
                total_articles += 1
                print(f"    ✓ Đã lưu (tổng: {total_articles})")
                
                del post_detail, details
                if idx % 5 == 0:
                    gc.collect()

            except Exception as e:
                print(f"    ❌ Lỗi: {str(e)[:50]}")
                continue

    driver.quit()
    
    # Close Kafka producer
    if kafka_producer:
        kafka_producer.flush()
        kafka_producer.close()

    print(f"\n{'='*50}")
    print(f"Hoàn thành! Đã lưu: {total_articles} bài")
    print(f"File: {output_file}")
    print(f"{'='*50}")


if __name__ == "__main__":
    main()