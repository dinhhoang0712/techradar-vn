"""
JobsGO crawler — jobsgo.vn
Crawl tin tuyển dụng IT từ JobsGO, đẩy vào Kafka topic raw_jobs.

jobsgo.vn chặn Cloudflare JS challenge ("Cf-Mitigated: challenge") giống topdev.vn/topcv.vn
— cần Selenium thật (undetected_chromedriver), plain requests không qua được. Trang chi tiết
job không có URL chuyên biệt theo category id, mà theo tên vai trò (vd viec-lam-lap-trinh-vien.html);
category rộng nhất cho IT là "Công Nghệ Thông Tin" — verify thủ công có ~1700 job (nhiều hơn hẳn
các nguồn hiện có). Pagination qua ?page=N (đã verify: mỗi trang ra job khác nhau, không lặp).

Trang chi tiết implement JobPosting JSON-LD schema.org đầy đủ (title/hiringOrganization/
baseSalary/jobLocation/responsibilities/jobBenefits/industry) — dùng làm nguồn chính, không
cần bám CSS selector DOM. Riêng "requirement" (Yêu cầu công việc) không có field JSON-LD riêng,
nằm giữa "responsibilities" và "jobBenefits" trong field `description` gộp chung — tách bằng
regex theo header tiếng Việt.
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
from selenium.common.exceptions import TimeoutException, WebDriverException
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

SOURCE_PLATFORM = "JobsGO"
MAX_JOBS = 150
LISTING_URL = "https://jobsgo.vn/viec-lam-cong-nghe-thong-tin.html"
NUM_PAGES = 20  # ~53 job/trang -> đủ NUM_PAGES*53 >> MAX_JOBS, dừng sớm khi đạt cap

DATA_DIR = os.path.join(os.path.dirname(__file__), "data", "raw", "jobsgo")
URL_CACHE = os.path.join(DATA_DIR, "processed_urls.txt")

UA = UserAgent()
USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]

JOB_URL_RE = re.compile(r"https://jobsgo\.vn/viec-lam/[^/?\"']+-\d+\.html")


def _wait_for_page_ready(driver, css="a[href]", timeout=25):
    """Cloudflare JS challenge đẩy thời gian render thật lên tới ~30-35s (đã verify thủ
    công) — chờ có tín hiệu trang render xong thay vì sleep cố định, tránh đọc hụt nội
    dung như đã gặp ở TopCV.py trước khi có fix này."""
    try:
        WebDriverWait(driver, timeout).until(EC.presence_of_element_located((By.CSS_SELECTOR, css)))
    except TimeoutException:
        pass


def _load_urls(path: str) -> set:
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return {line.strip() for line in f if line.strip()}
    return set()


def _save_url(path: str, url: str):
    with open(path, "a", encoding="utf-8") as f:
        f.write(url + "\n")


def _job_posting_ld(driver) -> dict:
    try:
        for script in driver.find_elements(By.CSS_SELECTOR, "script[type='application/ld+json']"):
            raw = script.get_attribute("innerHTML")
            try:
                data = json.loads(raw)
            except (json.JSONDecodeError, TypeError):
                continue
            if isinstance(data, dict) and data.get("@type") == "JobPosting":
                return data
    except WebDriverException:
        pass
    return {}


def _salary_from_ld(job_posting: dict) -> str:
    base_salary = job_posting.get("baseSalary") or {}
    min_v, max_v = base_salary.get("minValue"), base_salary.get("maxValue")
    currency = base_salary.get("currency", "")
    if min_v and max_v:
        return f"{min_v} - {max_v} {currency}".strip()
    if min_v:
        return f"{min_v} {currency}".strip()
    return ""


def _location_from_ld(job_posting: dict) -> str:
    places = job_posting.get("jobLocation") or []
    if isinstance(places, dict):
        places = [places]
    regions = []
    for place in places:
        region = (place.get("address") or {}).get("addressRegion")
        if region and region not in regions:
            regions.append(region)
    return ", ".join(regions)


def _requirement_from_description(description: str) -> str:
    """description gộp chung 3 phần theo header tiếng Việt — responsibilities/jobBenefits
    đã có field riêng sạch, chỉ "Yêu cầu công việc" (nằm giữa) cần tách thủ công."""
    match = re.search(
        r"(?i)yêu cầu công việc\s*(.*?)(?=(?:quyền lợi được hưởng)|$)",
        description,
        re.DOTALL,
    )
    return match.group(1).strip() if match else ""


def parse_job_detail(driver, job_url: str) -> dict:
    job_posting = _job_posting_ld(driver)
    title = job_posting.get("title") or ""
    hiring_org = job_posting.get("hiringOrganization") or {}
    company = hiring_org.get("name") or ""
    description = job_posting.get("description") or ""

    return {
        "title": title,
        "company": company,
        "location": _location_from_ld(job_posting),
        "salary": _salary_from_ld(job_posting),
        "level": "",
        "description": job_posting.get("responsibilities") or description,
        "requirement": _requirement_from_description(description),
        "benefit": job_posting.get("jobBenefits") or "",
        "skills": [],
        "source_url": job_url,
        "posted_date": job_posting.get("datePosted") or "",
        "field": ", ".join(job_posting.get("industry") or []),
        "size": "",
    }


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
            except (TimeoutException, WebDriverException) as e:
                logger.warning("Failed to load page %d: %s", page, e)
                break
            _wait_for_page_ready(driver, "a[href]")
            time.sleep(random.uniform(1, 2))

            found = set(JOB_URL_RE.findall(driver.page_source))
            new_urls = [u for u in found if u not in processed and u not in job_urls]
            job_urls.extend(new_urls)
            logger.info("Page %d: %d job URLs collected so far", page, len(job_urls))

        job_urls = job_urls[:MAX_JOBS]

        for job_url in job_urls:
            try:
                driver.get(job_url)
            except (TimeoutException, WebDriverException) as e:
                logger.warning("Failed job %s: %s", job_url, e)
                continue
            _wait_for_page_ready(driver, "script[type='application/ld+json']")
            time.sleep(random.uniform(1, 2))

            job_data = parse_job_detail(driver, job_url)
            if not job_data["title"] or not job_data["company"]:
                continue

            jobs.append(job_data)
            _save_url(URL_CACHE, job_url)
            processed.add(job_url)

            if kafka_enabled:
                kafka.send_job(
                    job_title=job_data["title"],
                    company_name=job_data["company"],
                    location=job_data["location"],
                    salary=job_data["salary"],
                    level="",
                    description=job_data["description"],
                    requirement=job_data["requirement"],
                    benefit=job_data["benefit"],
                    skills=job_data["skills"],
                    source_url=job_url,
                    posted_date=job_data["posted_date"],
                    source_platform=SOURCE_PLATFORM,
                    company_size=job_data["size"],
                    company_field=job_data["field"],
                )

            total += 1
            logger.info("[%d] %s @ %s", total, job_data["title"][:50], job_data["company"][:30])

            if total % 10 == 0:
                gc.collect()

    finally:
        driver.quit()
        gc.collect()
        kafka.close()

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump({"source_platform": SOURCE_PLATFORM, "jobs": jobs}, f, ensure_ascii=False, indent=2)

    logger.info("JobsGO done: %d jobs", total)


if __name__ == "__main__":
    main()
