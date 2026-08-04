"""
ITviec crawler — itviec.com
Crawl tin tuyển dụng IT từ ITviec, đẩy vào Kafka topic raw_jobs.

ITviec redesign sang Rails Turbo/Stimulus (phát hiện 2026-07): Cloudflare trả 403
cho undetected_chromedriver dù đã spoof User-Agent — headless Chrome tự nó bị flag
bất kể UA header. Nhưng 1 request HTTP thường (requests + UA trình duyệt) lại pass
200 ngay lập tức: listing/detail render sẵn ở server, không cần JS. Vì vậy bỏ hẳn
Selenium, dùng requests + BeautifulSoup — vừa né được block, vừa nhẹ/nhanh hơn.
Selector DOM cũ (div.job-content/a.job-title/div.employer-name/div.tag-list...)
không còn khớp cấu trúc mới (job-card + data attribute), nên phải viết lại theo
cấu trúc hiện tại: job slug lấy từ data-search--job-selection-job-slug-value trên
trang listing; title/company/location/salary/skills lấy từ JSON nhúng sẵn
(data-jobs--save-data-layer-value + script JobPosting ld+json) trên trang chi
tiết — đáng tin hơn scrape DOM vì đây chính là data Rails render ra; riêng
description/requirement/benefit vẫn phải gọi thêm endpoint {slug}/content vì
trang chi tiết chính không chứa 3 phần này (site load qua Turbo Frame).
"""

import html
import json
import logging
import os
import re
import time
from datetime import datetime

import requests
from bs4 import BeautifulSoup
from fake_useragent import UserAgent
from kafka_producer import CrawlerKafkaProducer

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

SOURCE_PLATFORM = "ITviec"
MAX_JOBS = 150
BASE_URL = "https://itviec.com/it-jobs"
NUM_PAGES = 10
REQUEST_DELAY_SECONDS = 1.5

DATA_DIR = os.path.join(os.path.dirname(__file__), "data", "raw", "itviec")
URL_CACHE = os.path.join(DATA_DIR, "processed_urls.txt")

_ua = UserAgent()


def _headers() -> dict:
    return {"User-Agent": _ua.random}


def _load_urls(path: str) -> set:
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return {line.strip() for line in f if line.strip()}
    return set()


def _save_url(path: str, url: str):
    with open(path, "a", encoding="utf-8") as f:
        f.write(url + "\n")


def _fetch(url: str) -> str | None:
    try:
        resp = requests.get(url, headers=_headers(), timeout=15)
        resp.raise_for_status()
        return resp.text
    except requests.RequestException as e:
        logger.warning("Failed to fetch %s: %s", url, e)
        return None


def _parse_job_slugs(listing_html: str) -> list[str]:
    return re.findall(r"data-search--job-selection-job-slug-value=['\"]([^'\"]+)['\"]", listing_html)


def _extract_data_layer(detail_html: str) -> dict:
    """JSON nhúng sẵn trong attribute data-jobs--save-data-layer-value (title/company/
    location/skills) — đáng tin hơn scrape DOM vì đây chính là data Rails render ra."""
    m = re.search(r"data-jobs--save-data-layer-value=['\"](.*?)['\"]\s", detail_html)
    if not m:
        return {}
    try:
        return json.loads(html.unescape(m.group(1)))
    except json.JSONDecodeError:
        return {}


def _extract_job_posting_jsonld(detail_html: str) -> dict:
    soup = BeautifulSoup(detail_html, "html.parser")
    for script in soup.find_all("script", type="application/ld+json"):
        try:
            data = json.loads(script.string or "")
        except (json.JSONDecodeError, TypeError):
            continue
        if data.get("@type") == "JobPosting":
            return data
    return {}


def _format_salary(job_posting: dict) -> str:
    base_salary = job_posting.get("baseSalary") or {}
    value = base_salary.get("value")
    if not isinstance(value, dict):
        return ""
    min_v, max_v, single_v = value.get("minValue"), value.get("maxValue"), value.get("value")
    currency = base_salary.get("currency", "")
    if min_v and max_v:
        return f"{min_v} - {max_v} {currency}".strip()
    if isinstance(single_v, int | float):
        return f"{single_v} {currency}".strip()
    return ""  # placeholder kiểu "You'll love it" (lương ẩn) -> coi như không có


def _format_experience(job_posting: dict) -> str:
    """schema.org JobPosting.experienceRequirements — Text hoặc object tuỳ trang implement. Trả
    raw text thô (chưa chuẩn hoá) — normalize_level() ở silver layer xử lý tiếp, giống cách
    VietnamWorks.py gửi jobLevelVI thô."""
    exp = job_posting.get("experienceRequirements")
    if isinstance(exp, str):
        return exp
    if isinstance(exp, dict):
        return exp.get("name") or exp.get("description") or ""
    return ""


def _format_location(job_posting: dict) -> str:
    places = job_posting.get("jobLocation") or []
    regions = []
    for place in places:
        region = (place.get("address") or {}).get("addressRegion")
        if region and region not in regions:
            regions.append(region)
    return ", ".join(regions)


def _extract_content_sections(content_html: str) -> dict:
    soup = BeautifulSoup(content_html, "html.parser")
    result = {"description": "", "requirement": "", "benefit": ""}
    mapping = {
        "job-description": "description",
        "job-experiences": "requirement",
        "job-why-love-working": "benefit",
    }
    for css_class, field in mapping.items():
        block = soup.find(class_=css_class)
        if block:
            result[field] = block.get_text("\n", strip=True)
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

    try:
        job_slugs = []
        for page in range(1, NUM_PAGES + 1):
            if total + len(job_slugs) >= MAX_JOBS:
                break
            listing_html = _fetch(f"{BASE_URL}?page={page}")
            time.sleep(REQUEST_DELAY_SECONDS)
            if not listing_html:
                break
            slugs = [s for s in _parse_job_slugs(listing_html) if f"{BASE_URL}/{s}" not in processed]
            job_slugs.extend(slugs)
            logger.info("Page %d: %d job slugs collected so far", page, len(job_slugs))

        job_slugs = list(dict.fromkeys(job_slugs))[:MAX_JOBS]

        for slug in job_slugs:
            job_url = f"{BASE_URL}/{slug}"
            detail_html = _fetch(job_url)
            time.sleep(REQUEST_DELAY_SECONDS)
            if not detail_html:
                continue

            data_layer = _extract_data_layer(detail_html)
            job_posting = _extract_job_posting_jsonld(detail_html)

            title = data_layer.get("job_title") or job_posting.get("title", "")
            company = data_layer.get("job_by_company", "")
            if not title or not company:
                continue

            content_html = _fetch(f"{job_url}/content?locale=en")
            time.sleep(REQUEST_DELAY_SECONDS)
            sections = (
                _extract_content_sections(content_html)
                if content_html
                else {"description": "", "requirement": "", "benefit": ""}
            )

            skills_raw = data_layer.get("job_required_skill") or job_posting.get("skills", "")
            skills = [s.strip() for s in skills_raw.split(",") if s.strip()]

            location = _format_location(job_posting) or data_layer.get("job_by_city", "")
            salary = _format_salary(job_posting)
            posted_date = job_posting.get("datePosted", "")
            level = _format_experience(job_posting)

            job_data = {
                "title": title,
                "company": company,
                "location": location,
                "salary": salary,
                "level": level,
                "description": sections["description"],
                "requirement": sections["requirement"],
                "benefit": sections["benefit"],
                "skills": skills,
                "source_url": job_url,
                "posted_date": posted_date,
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
                    level=level,
                    description=sections["description"],
                    requirement=sections["requirement"],
                    benefit=sections["benefit"],
                    skills=skills,
                    source_url=job_url,
                    posted_date=posted_date,
                    source_platform=SOURCE_PLATFORM,
                )

            total += 1
            logger.info("[%d] %s @ %s", total, title[:50], company[:30])

    finally:
        kafka.close()

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump({"source_platform": SOURCE_PLATFORM, "jobs": jobs}, f, ensure_ascii=False, indent=2)

    logger.info("ITviec done: %d jobs", total)


if __name__ == "__main__":
    main()
