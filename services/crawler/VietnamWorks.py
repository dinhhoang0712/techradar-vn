"""
VietnamWorks crawler — vietnamworks.com
Crawl tin tuyển dụng ngành CNTT/Viễn thông (category g=5), đẩy vào Kafka topic raw_jobs.

Không cần Selenium: job data nhúng sẵn trong HTML server-render dưới dạng Next.js
RSC "flight" stream — nhiều <script>self.__next_f.push([1,"<id>:<payload>"])</script>
ghép lại thành 1 buffer, tách theo dòng "<id>:<payload>". Object chính của job nằm
trong 1 chunk dạng JSON thuần (có key "jobId"); một số field (skills, benefits,
jobDescription, workingLocations) chỉ là string tham chiếu "$<id>" tới chunk khác,
_resolve() đệ quy giải các tham chiếu đó. Đã verify thủ công với job thật qua
requests trần (không bị chặn như topdev.vn) trước khi viết file này.

Category id 5 = "Information Technology/Telecommunications" lấy từ block
hotCategories nhúng trong HTML trang chủ (không có URL slug /it-jobs cố định,
category chỉ lọc qua query param ?g=<id>).
"""

import gc
import json
import logging
import os
import random
import re
import time
from datetime import datetime

import requests
from kafka_producer import CrawlerKafkaProducer

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

SOURCE_PLATFORM = "VietnamWorks"
MAX_JOBS = 150
IT_CATEGORY_ID = 5
LISTING_URL = "https://www.vietnamworks.com/viec-lam"
NUM_PAGES = 40  # ~5 job server-render sẵn mỗi trang -> cần nhiều trang mới đủ MAX_JOBS

DATA_DIR = os.path.join(os.path.dirname(__file__), "data", "raw", "vietnamworks")
URL_CACHE = os.path.join(DATA_DIR, "processed_urls.txt")

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]

JOB_URL_RE = re.compile(r"https://www\.vietnamworks\.com/[a-zA-Z0-9\-]+-(\d+)-jv")
FLIGHT_CHUNK_RE = re.compile(r'self\.__next_f\.push\(\[1,(".*?")\]\)', re.S)
LINE_RE = re.compile(r"^([0-9a-zA-Z]+):(.*)$", re.S)
REF_RE = re.compile(r"^\$([0-9a-zA-Z]+)$")
TEXT_SEGMENT_RE = re.compile(r"^T[0-9a-fA-F]+,")


def _load_urls(path: str) -> set:
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return {line.strip() for line in f if line.strip()}
    return set()


def _save_url(path: str, url: str):
    with open(path, "a", encoding="utf-8") as f:
        f.write(url + "\n")


def _flight_chunks(html: str) -> dict:
    chunks = {}
    for raw in FLIGHT_CHUNK_RE.findall(html):
        try:
            text = json.loads(raw)
        except (json.JSONDecodeError, ValueError):
            continue
        for line in text.split("\n"):
            m = LINE_RE.match(line)
            if m:
                chunks[m.group(1)] = m.group(2)
    return chunks


def _resolve(chunks: dict, value, depth: int = 0):
    if depth > 6:
        return value
    if isinstance(value, str):
        m = REF_RE.match(value)
        if not m:
            return value
        raw = chunks.get(m.group(1))
        if raw is None:
            return None
        try:
            parsed = json.loads(raw)
        except (json.JSONDecodeError, ValueError):
            # Next.js RSC chunk chở text thô (không phải JSON) có prefix
            # "T<hex-length>," báo độ dài byte cho streaming — bỏ đi, giữ nội dung.
            return TEXT_SEGMENT_RE.sub("", raw, count=1)
        return _resolve(chunks, parsed, depth + 1)
    if isinstance(value, list):
        return [_resolve(chunks, v, depth + 1) for v in value]
    if isinstance(value, dict):
        return {k: _resolve(chunks, v, depth + 1) for k, v in value.items()}
    return value


def _find_job_object(chunks: dict, job_id: int) -> dict:
    """Nhiều chunk có thể chứa 1 object job (job chính + job liên quan ở sidebar
    đều có key jobId) — match đúng jobId trong URL để không lấy nhầm job khác."""
    fallback = {}
    for raw in chunks.values():
        if '"jobId"' not in raw or '"jobTitle"' not in raw:
            continue
        try:
            data = json.loads(raw)
        except (json.JSONDecodeError, ValueError):
            continue
        if not isinstance(data, dict):
            continue
        if data.get("jobId") == job_id:
            return data
        if not fallback:
            fallback = data
    return fallback


def _text_join(items, key_primary: str, key_fallback: str = "") -> str:
    if not isinstance(items, list):
        return ""
    out = []
    for it in items:
        if not isinstance(it, dict):
            continue
        val = it.get(key_primary) or (it.get(key_fallback) if key_fallback else "")
        if val:
            out.append(str(val))
    return ", ".join(out)


def _salary_text(job: dict) -> str:
    salary_min, salary_max = job.get("salaryMin") or 0, job.get("salaryMax") or 0
    if salary_min and salary_max:
        currency = job.get("salaryCurrency") or ""
        return f"{salary_min} - {salary_max} {currency}".strip()
    return job.get("prettySalaryVI") or job.get("prettySalary") or ""


def _benefits_text(chunks: dict, job: dict) -> str:
    resolved = _resolve(chunks, job.get("benefits"))
    if not isinstance(resolved, list):
        return ""
    lines = []
    for b in resolved:
        if not isinstance(b, dict):
            continue
        name = b.get("benefitNameVI") or b.get("benefitName") or ""
        value = b.get("benefitValue") or ""
        if name:
            lines.append(f"{name}: {value}" if value else name)
    return "\n".join(lines)


def _skills_list(chunks: dict, job: dict) -> list:
    resolved = _resolve(chunks, job.get("skills"))
    if not isinstance(resolved, list):
        return []
    return [s.get("skillName") for s in resolved if isinstance(s, dict) and s.get("skillName")]


def _location_text(chunks: dict, job: dict) -> str:
    if job.get("address"):
        return job["address"]
    resolved = _resolve(chunks, job.get("workingLocations"))
    if isinstance(resolved, list):
        return _text_join(resolved, "address")
    return ""


def _text_or_html(chunks: dict, value) -> str:
    resolved = _resolve(chunks, value)
    return resolved if isinstance(resolved, str) else ""


def parse_job_detail(html: str, job_url: str, job_id: int) -> dict:
    chunks = _flight_chunks(html)
    job = _find_job_object(chunks, job_id)
    if not job:
        return {}

    industries = _resolve(chunks, job.get("industries"))
    field = _text_join(industries, "industryNameVI", "industryName") if isinstance(industries, list) else ""

    return {
        "title": job.get("jobTitle") or "",
        "company": job.get("companyName") or "",
        "location": _location_text(chunks, job),
        "salary": _salary_text(job),
        "level": job.get("jobLevelVI") or "",
        "description": _text_or_html(chunks, job.get("jobDescription")),
        "requirement": _text_or_html(chunks, job.get("jobRequirement")),
        "benefit": _benefits_text(chunks, job),
        "skills": _skills_list(chunks, job),
        "source_url": job_url,
        "posted_date": job.get("createdOn") or "",
        "size": job.get("companySizeVI") or job.get("companySize") or "",
        "field": field,
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

    session = requests.Session()
    session.headers.update({"User-Agent": random.choice(USER_AGENTS)})

    try:
        job_urls = {}  # url -> job_id
        for page in range(1, NUM_PAGES + 1):
            if total + len(job_urls) >= MAX_JOBS:
                break
            try:
                resp = session.get(LISTING_URL, params={"g": IT_CATEGORY_ID, "page": page}, timeout=20)
                resp.raise_for_status()
            except requests.RequestException as e:
                logger.warning("Failed to load page %d: %s", page, e)
                continue

            for match in JOB_URL_RE.finditer(resp.text):
                url, job_id = match.group(0), int(match.group(1))
                if url not in processed and url not in job_urls:
                    job_urls[url] = job_id
            logger.info("Page %d: %d job URLs collected so far", page, len(job_urls))
            time.sleep(random.uniform(1, 2))

        job_urls = dict(list(job_urls.items())[:MAX_JOBS])

        for job_url, job_id in job_urls.items():
            try:
                resp = session.get(job_url, timeout=20)
                resp.raise_for_status()
                job_data = parse_job_detail(resp.text, job_url, job_id)
                if not job_data or not job_data["title"] or not job_data["company"]:
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
                        level=job_data["level"],
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

                if total % 20 == 0:
                    gc.collect()
                time.sleep(random.uniform(1, 2))

            except requests.RequestException as e:
                logger.warning("Failed job %s: %s", job_url, e)

    finally:
        gc.collect()
        kafka.close()

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump({"source_platform": SOURCE_PLATFORM, "jobs": jobs}, f, ensure_ascii=False, indent=2)

    logger.info("VietnamWorks done: %d jobs", total)


if __name__ == "__main__":
    main()
