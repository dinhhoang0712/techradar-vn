import gc
import logging
import os
import random
import re
import time
from datetime import datetime

import undetected_chromedriver as uc
from chrome_utils import installed_chrome_major_version
from fake_useragent import UserAgent
from kafka_producer import CrawlerKafkaProducer
from selenium.common.exceptions import NoSuchElementException, TimeoutException
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait


def _wait_for_page_ready(driver, css="h1", timeout=20):
    """Cloudflare challenge/JS render can push real load time well past a fixed sleep(3-5s)
    (đã thấy trang mất 30-40s mới render xong trong thực tế) — chờ tới khi có tín hiệu trang
    đã render thay vì đoán 1 khoảng sleep cố định, để các field render muộn (mô tả công việc,
    nằm dưới company-info trong DOM) không bị đọc hụt."""
    try:
        WebDriverWait(driver, timeout).until(EC.presence_of_element_located((By.CSS_SELECTOR, css)))
    except TimeoutException:
        pass

# Configure logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
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


def safe_find(driver, css):
    try:
        return driver.find_element(By.CSS_SELECTOR, css).text.strip()
    except NoSuchElementException:
        return ""


def safe_find_from(root, css):
    if root is None:
        return ""
    try:
        return root.find_element(By.CSS_SELECTOR, css).text.strip()
    except NoSuchElementException:
        return ""


MAX_LABEL_VALUE_LEN = 200


def extract_label_value(text, label, stop_labels=None):
    stop_labels = stop_labels or []
    pattern = rf"(?i){re.escape(label)}\s*[:\-–]?\s*(.*?)(?=(?:{'|'.join(re.escape(lbl) for lbl in stop_labels)})|$)"
    match = re.search(pattern, text, re.IGNORECASE | re.DOTALL)
    if not match:
        return ""
    value = match.group(1).strip()
    value = re.sub(r"^[|\s:]+|[|\s:]+$", "", value)
    # Khi không stop_label nào thực sự xuất hiện sau `label`, group(1) chạy tới hết chuỗi ($) —
    # trên trang có sidebar "việc làm tương tự" nằm chung root với company-info, việc này từng
    # vơ nguyên cả block nhiều job khác thành 1 "company_name" dài cả nghìn ký tự. Company/
    # location/size/field thật không bao giờ dài vậy — dài bất thường nghĩa là match sai vùng,
    # trả "" còn an toàn hơn để lại data rác cho bước clustering/embedding phía sau.
    if len(value) > MAX_LABEL_VALUE_LEN:
        return ""
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
    # None (not `driver`) — `driver.text` doesn't exist and used to crash the whole job entry
    # (AttributeError -> caught by the outer except -> job silently dropped even though
    # title/description/requirement/benefit had already been scraped successfully above).
    logger.warning("Company info container not found for %s", driver.current_url)
    return None


def clean_field_value(value):
    value = re.sub(r"(?i)L[ií]nh v[uụ]c\s*[:\-–]?\s*", "", value).strip()
    parts = value.split("\n")
    return parts[1] if len(parts) > 1 else value


def load_processed_urls(url_cache_file):
    if os.path.exists(url_cache_file):
        with open(url_cache_file, encoding="utf-8") as f:
            return set(line.strip() for line in f if line.strip())
    return set()


def save_processed_url(url_cache_file, url):
    with open(url_cache_file, "a", encoding="utf-8") as f:
        f.write(url + "\n")


# CSV fieldnames for jobs
JOB_FIELDNAMES = [
    "title",
    "description",
    "requirement",
    "benefit",
    "location",
    "salary",
    "due_date",
    "Company",
    "size",
    "field",
    "source_url",
]


def scrape_job_details(driver):
    details = {}
    root = find_job_info_root(driver)

    details["description"] = extract_section(driver, ["mô tả công việc", "mô tả"]) or ""
    details["requirement"] = extract_section(driver, ["yêu cầu ứng viên", "yêu cầu"]) or ""
    details["benefit"] = extract_section(driver, ["quyền lợi", "phúc lợi"]) or ""

    details["location"] = (
        safe_find_from(root, ".job-detail__info--location")
        or find_label_value(root, ["Địa điểm"], ["Kinh nghiệm", "Hạn nộp"])
        or ""
    )

    details["salary"] = (
        safe_find_from(root, ".job-detail__info--salary")
        or find_label_value(root, ["Mức lương", "Lương"], ["Kinh nghiệm", "Địa điểm", "Hạn nộp"])
        or ""
    )

    due_date_raw = safe_find_from(root, ".job-detail__info--deadline") or ""
    if due_date_raw:
        match = re.search(r"\d{1,2}/\d{1,2}/\d{4}", due_date_raw)
        details["due_date"] = match.group(0) if match else due_date_raw.strip()
    else:
        details["due_date"] = ""

    company_root = find_company_info_root(driver)
    company_text = company_root.text.strip() if company_root is not None else ""

    details["Company"] = (
        safe_find_from(company_root, ".company-name-label")
        or extract_label_value(company_text, "Công ty", ["Quy mô", "Lĩnh vực"])
        or ""
    )

    details["size"] = extract_label_value(company_text, "Quy mô", ["Lĩnh vực"]) or ""

    field_value = extract_label_value(company_text, "Lĩnh vực", []) or ""
    details["field"] = clean_field_value(field_value)

    return details


def main():
    # Initialize Kafka producer
    kafka_producer = CrawlerKafkaProducer()
    kafka_enabled = False
    try:
        kafka_enabled = kafka_producer.connect()
        if kafka_enabled:
            logger.info("Kafka connected for TopCV")
        else:
            logger.warning("Kafka not available, data will only be saved to CSV")
    except Exception as e:
        logger.warning("Kafka connection failed: %s", e)
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
        driver = uc.Chrome(
            options=_build_options(uc.ChromeOptions),
            version_main=installed_chrome_major_version(),
        )
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
        driver.execute_cdp_cmd("Page.addScriptToEvaluateOnNewDocument", {"source": stealth_js})
    except Exception:
        pass

    base_url_page1 = "https://www.topcv.vn/tim-viec-lam-cong-nghe-thong-tin-cr257?type_keyword=1&category_family=r257&saturday_status=0"
    base_url_paged = "https://www.topcv.vn/tim-viec-lam-cong-nghe-thong-tin-cr257?type_keyword=1&page={page}&category_family=r257&saturday_status=0"
    # TopCV ghim tin "nổi bật" ở đầu danh sách nên các trang đầu gần như không
    # đổi qua từng ngày; phải đi đủ sâu mới chạm tới tin thật sự mới.
    num_pages = 20

    today_str = datetime.now().strftime("%d_%m_%Y")
    base_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(base_dir, "data", "raw", "topcv")
    os.makedirs(output_dir, exist_ok=True)
    output_file = os.path.join(output_dir, f"{today_str}.csv")
    # Cache tích lũy xuyên suốt (không reset theo ngày) để không quét lại các
    # tin cũ đã thấy ở lần chạy trước — nếu reset theo ngày, mỗi ngày crawler
    # lại tốn hết ngân sách request vào đúng các tin "nổi bật" bất biến đó.
    url_cache_file = os.path.join(output_dir, "processed_urls.txt")

    processed_urls = load_processed_urls(url_cache_file)
    logger.info("Đã xử lý trước đó: %d bài", len(processed_urls))

    # Initialize CSV file with headers if not exists
    import csv

    if not os.path.exists(output_file):
        with open(output_file, "w", encoding="utf-8", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=JOB_FIELDNAMES)
            writer.writeheader()

    logger.info("File output: %s", output_file)

    try:
        driver.get(base_url_page1)
    except Exception as e:
        logger.error("Failed to load TopCV listing page: %s", type(e).__name__)
        driver.quit()
        exit(1)

    seen_links = set()
    total_articles = 0

    for page in range(1, num_pages + 1):
        logger.info("--- Trang %d/%d ---", page, num_pages)

        page_url = base_url_page1 if page == 1 else base_url_paged.format(page=page)
        driver.get(page_url)
        _wait_for_page_ready(driver, "h3.title a")
        time.sleep(random.uniform(1, 2))

        elements_links = driver.find_elements(By.CSS_SELECTOR, "h3.title a")
        links = []
        for e in elements_links:
            href = e.get_attribute("href")
            if href and href not in seen_links and href not in processed_urls:
                seen_links.add(href)
                links.append(href)

        logger.info("Tìm thấy %d bài", len(links))

        for idx, link in enumerate(links):
            logger.info("[%d/%d] %s...", idx + 1, len(links), link.split("/")[-1][:30])

            try:
                driver.get(link)
                _wait_for_page_ready(driver, "h1")
                time.sleep(random.uniform(1, 2))

                title = safe_find(driver, "h1.job-detail__info--title")
                if not title:
                    title = safe_find(driver, "h1")
                    if not title:
                        continue

                details = scrape_job_details(driver)
                post_detail = {
                    "title": title,
                    "description": details.get("description", ""),
                    "requirement": details.get("requirement", ""),
                    "benefit": details.get("benefit", ""),
                    "location": details.get("location", ""),
                    "salary": details.get("salary", ""),
                    "due_date": details.get("due_date", ""),
                    "Company": details.get("Company", ""),
                    "size": details.get("size", ""),
                    "field": details.get("field", ""),
                    "source_url": link,
                }

                # Save to CSV
                with open(output_file, "a", encoding="utf-8", newline="") as f:
                    writer = csv.DictWriter(f, fieldnames=JOB_FIELDNAMES, extrasaction="ignore")
                    writer.writerow(post_detail)

                save_processed_url(url_cache_file, link)

                # Send to Kafka (job data)
                if kafka_enabled:
                    kafka_producer.send_job(
                        job_title=title,
                        company_name=details.get("Company", ""),
                        location=details.get("location", ""),
                        salary=details.get("salary", ""),
                        level="",  # Could be extracted from title
                        description=details.get("description", ""),
                        requirement=details.get("requirement", ""),
                        benefit=details.get("benefit", ""),
                        skills=[],  # Could be extracted from description
                        source_url=link,
                        posted_date="",
                        source_platform="TopCV",
                        company_size=details.get("size", ""),
                        company_field=details.get("field", ""),
                    )

                total_articles += 1
                logger.info("Đã lưu (tổng: %d)", total_articles)

                del post_detail, details
                if idx % 5 == 0:
                    gc.collect()

            except Exception as e:
                logger.warning("Lỗi: %s", str(e)[:50])
                continue

    driver.quit()

    # Close Kafka producer
    if kafka_producer:
        kafka_producer.flush()
        kafka_producer.close()

    logger.info("=" * 50)
    logger.info("Hoàn thành! Đã lưu: %d bài", total_articles)
    logger.info("File: %s", output_file)
    logger.info("=" * 50)


if __name__ == "__main__":
    main()
