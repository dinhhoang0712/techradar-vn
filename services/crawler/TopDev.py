"""
TopDev crawler — topdev.vn
Crawl tin tuyển dụng IT từ TopDev, đẩy vào Kafka topic raw_jobs.

CSS selector chưa verify được với DOM thật: topdev.vn (không phải www.)
chặn kết nối từ các tool fetch không phải browser thật (WAF/anti-bot),
nên không lấy được HTML mẫu trước khi viết file này. Vì vậy ưu tiên trích
xuất qua JSON-LD JobPosting (schema.org — chuẩn Google for Jobs, nhiều khả
năng site lớn như TopDev có implement để lên rich snippet) làm nguồn chính,
fallback sang trích theo label text tiếng Việt (kiểu TopCV.py) vốn ít phụ
thuộc đúng tên class CSS. Cần 1 lần chạy thật để verify/chỉnh lại selector
listing page (link-collection) — đây là phần rủi ro nhất vì không có schema
chuẩn để bám vào.
"""

import gc
import json
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
from selenium.common.exceptions import NoSuchElementException, TimeoutException, WebDriverException
from selenium.webdriver.common.by import By

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

SOURCE_PLATFORM = "TopDev"
MAX_JOBS = 150
LISTING_URL = "https://topdev.vn/viec-lam-it"
NUM_PAGES = 15

DATA_DIR = os.path.join(os.path.dirname(__file__), "data", "raw", "topdev")
URL_CACHE = os.path.join(DATA_DIR, "processed_urls.txt")

UA = UserAgent()
USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]


def _load_urls(path: str) -> set:
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return {line.strip() for line in f if line.strip()}
    return set()


def _save_url(path: str, url: str):
    with open(path, "a", encoding="utf-8") as f:
        f.write(url + "\n")


def _safe(root, css: str) -> str:
    try:
        return root.find_element(By.CSS_SELECTOR, css).text.strip()
    except NoSuchElementException:
        return ""


def _extract_label_value(text: str, label: str, stop_labels: list) -> str:
    pattern = rf"(?i){re.escape(label)}\s*[:\-–]?\s*(.*?)(?=(?:{'|'.join(re.escape(s) for s in stop_labels)})|$)"
    match = re.search(pattern, text, re.IGNORECASE | re.DOTALL)
    if not match:
        return ""
    return re.sub(r"^[|\s:]+|[|\s:]+$", "", match.group(1).strip())


def _job_posting_ld(driver) -> dict:
    """Trích JSON-LD JobPosting nếu site implement schema.org cho Google for
    Jobs. An toàn hơn CSS selector vì không phụ thuộc tên class."""
    try:
        for script in driver.find_elements(By.CSS_SELECTOR, "script[type='application/ld+json']"):
            raw = script.get_attribute("innerHTML")
            try:
                data = json.loads(raw)
            except (json.JSONDecodeError, TypeError):
                continue
            candidates = data if isinstance(data, list) else [data]
            for item in candidates:
                if isinstance(item, dict) and item.get("@type") == "JobPosting":
                    return item
    except Exception:
        pass
    return {}


def _salary_from_ld(job_posting: dict) -> str:
    base_salary = job_posting.get("baseSalary") or {}
    value = base_salary.get("value") or {}
    min_v, max_v = value.get("minValue"), value.get("maxValue")
    currency = base_salary.get("currency", "")
    if min_v and max_v:
        return f"{min_v} - {max_v} {currency}".strip()
    if min_v:
        return f"{min_v} {currency}".strip()
    return ""


def _location_from_ld(job_posting: dict) -> str:
    location = job_posting.get("jobLocation") or {}
    if isinstance(location, list):
        location = location[0] if location else {}
    address = location.get("address") or {}
    parts = [address.get("addressLocality"), address.get("addressRegion")]
    return ", ".join(p for p in parts if p)


def _extract_section(driver, keywords: list) -> str:
    for header_tag in ("h2", "h3", "strong", "b"):
        for h in driver.find_elements(By.TAG_NAME, header_tag):
            text = h.text.strip().lower()
            if any(kw in text for kw in keywords):
                try:
                    sibling = h.find_element(By.XPATH, "following-sibling::*[1]")
                    content = sibling.text.strip()
                    if content:
                        return content
                except NoSuchElementException:
                    continue
    return ""


def _parse_skills(driver) -> list:
    try:
        tags = driver.find_elements(By.CSS_SELECTOR, "div.job-tags a, span.skill-tag, a.tag, div.tag-list a")
        return [t.text.strip() for t in tags if t.text.strip()]
    except Exception:
        return []


def _extract_company_info(driver) -> dict:
    """TopDev hiển thị Quy mô/Lĩnh vực trên trang công ty giống TopCV — bám
    theo label text tiếng Việt vì không biết chắc tên class CSS thật."""
    result = {"size": "", "field": ""}
    try:
        body_text = driver.find_element(By.TAG_NAME, "body").text
    except NoSuchElementException:
        return result
    result["size"] = _extract_label_value(body_text, "Quy mô", ["Lĩnh vực", "Địa điểm", "Website"])
    field_value = _extract_label_value(body_text, "Lĩnh vực", ["Quy mô", "Địa điểm", "Website"])
    result["field"] = field_value.split("\n")[0].strip() if field_value else ""
    return result


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
    jobs = []
    total = 0

    user_agent = random.choice(USER_AGENTS)

    def _make_options(factory):
        opts = factory()
        opts.add_argument("--headless=new")
        opts.add_argument("--no-sandbox")
        opts.add_argument("--disable-dev-shm-usage")
        opts.add_argument("--disable-gpu")
        opts.add_argument("--window-size=1920,1080")
        opts.add_argument("--disable-blink-features=AutomationControlled")
        opts.add_argument("--disable-extensions")
        opts.add_argument(f"--user-agent={user_agent}")
        opts.add_argument("--disable-background-networking")
        opts.add_argument("--disable-sync")
        opts.add_argument("--metrics-recording-only")
        opts.add_argument("--mute-audio")
        opts.page_load_strategy = "eager"
        return opts

    try:
        driver = uc.Chrome(
            options=_make_options(uc.ChromeOptions),
            version_main=installed_chrome_major_version(),
        )
        logger.info("Undetected ChromeDriver OK")
    except Exception as e:
        logger.warning("Undetected ChromeDriver failed: %s, fallback to regular Chrome", e)
        from selenium import webdriver as _wd
        from selenium.webdriver.chrome.options import Options as _Opts

        driver = _wd.Chrome(options=_make_options(_Opts))

    try:
        job_urls = []
        for page in range(1, NUM_PAGES + 1):
            if total + len(job_urls) >= MAX_JOBS:
                break
            list_url = LISTING_URL if page == 1 else f"{LISTING_URL}?page={page}"
            try:
                driver.get(list_url)
                time.sleep(random.uniform(2, 4))
                # Chưa verify được class thật của job card; bám theo pattern
                # URL /it-jobs/ hoặc /viec-lam-it/-kt<id> quan sát được qua
                # tìm kiếm ngoài, quét toàn bộ <a> trên trang thay vì 1 CSS
                # selector cụ thể để không phụ thuộc đúng tên class.
                anchors = driver.find_elements(By.CSS_SELECTOR, "a[href]")
                for a in anchors:
                    href = a.get_attribute("href") or ""
                    if not href or "topdev.vn" not in href:
                        continue
                    if not re.search(r"/(it-jobs|viec-lam-it)/[^/?]+-kt\d+", href):
                        continue
                    if href not in processed and href not in job_urls:
                        job_urls.append(href)
                logger.info("Page %d: %d job URLs collected so far", page, len(job_urls))
            except (TimeoutException, WebDriverException) as e:
                logger.warning("Failed to load page %d: %s", page, e)
                break

        job_urls = job_urls[:MAX_JOBS]

        for job_url in job_urls:
            try:
                driver.get(job_url)
                time.sleep(random.uniform(2, 3))

                job_posting = _job_posting_ld(driver)

                title = job_posting.get("title") or _safe(driver, "h1")
                hiring_org = job_posting.get("hiringOrganization") or {}
                company = hiring_org.get("name") or _safe(
                    driver, "a.company-name, div.company-name a, span.company-name"
                )
                location = _location_from_ld(job_posting) or _safe(
                    driver, "div.job-location, span.location, div.address"
                )
                salary = _salary_from_ld(job_posting) or _safe(driver, "div.job-salary, span.salary, div.salary-box")
                posted_date = job_posting.get("datePosted") or ""

                description = job_posting.get("description") or _extract_section(
                    driver, ["mô tả công việc", "mô tả", "description"]
                )
                requirement = _extract_section(driver, ["yêu cầu ứng viên", "yêu cầu", "requirement"])
                benefit = _extract_section(driver, ["quyền lợi", "phúc lợi", "benefit"])
                skills = _parse_skills(driver)
                company_info = _extract_company_info(driver)

                if not title or not company:
                    continue

                job_data = {
                    "title": title,
                    "company": company,
                    "location": location,
                    "salary": salary,
                    "level": "",
                    "description": description,
                    "requirement": requirement,
                    "benefit": benefit,
                    "skills": skills,
                    "source_url": job_url,
                    "posted_date": posted_date,
                    "size": company_info["size"],
                    "field": company_info["field"],
                }
                jobs.append(job_data)
                _save_url(URL_CACHE, job_url)
                processed.add(job_url)

                if kafka_enabled:
                    kafka.send_job(
                        job_title=title,
                        company_name=company,
                        location=location,
                        salary=salary,
                        level="",
                        description=description,
                        requirement=requirement,
                        benefit=benefit,
                        skills=skills,
                        source_url=job_url,
                        posted_date=posted_date,
                        source_platform=SOURCE_PLATFORM,
                        company_size=company_info["size"],
                        company_field=company_info["field"],
                    )

                total += 1
                logger.info("[%d] %s @ %s", total, title[:50], company[:30])

                if total % 5 == 0:
                    gc.collect()

            except (TimeoutException, WebDriverException) as e:
                logger.warning("Failed job %s: %s", job_url, e)

    finally:
        driver.quit()
        gc.collect()
        kafka.close()

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump({"source_platform": SOURCE_PLATFORM, "jobs": jobs}, f, ensure_ascii=False, indent=2)

    logger.info("TopDev done: %d jobs", total)


if __name__ == "__main__":
    main()
