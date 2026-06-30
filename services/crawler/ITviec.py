"""
ITviec crawler — itviec.com
Crawl tin tuyển dụng IT từ ITviec, đẩy vào Kafka topic raw_jobs.
"""
import gc
import json
import logging
import os
import re
import time
from datetime import datetime

import undetected_chromedriver as uc
from selenium.webdriver.common.by import By
from selenium.common.exceptions import NoSuchElementException, TimeoutException, WebDriverException

from kafka_producer import CrawlerKafkaProducer

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

SOURCE_PLATFORM = "ITviec"
MAX_JOBS = 150
BASE_URL = "https://itviec.com/it-jobs"
NUM_PAGES = 10

DATA_DIR = os.path.join(os.path.dirname(__file__), "data", "raw", "itviec")
URL_CACHE = os.path.join(DATA_DIR, "processed_urls.txt")


def _load_urls(path: str) -> set:
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return {l.strip() for l in f if l.strip()}
    return set()


def _save_url(path: str, url: str):
    with open(path, "a", encoding="utf-8") as f:
        f.write(url + "\n")


def _safe(root, css: str) -> str:
    try:
        return root.find_element(By.CSS_SELECTOR, css).text.strip()
    except NoSuchElementException:
        return ""


def _parse_skills(driver) -> list:
    try:
        tags = driver.find_elements(By.CSS_SELECTOR, "div.tag-list a, span.skill-tag, a.skill")
        return [t.text.strip() for t in tags if t.text.strip()]
    except Exception:
        return []


def _extract_sections(driver) -> dict:
    result = {"description": "", "requirement": "", "benefit": ""}
    try:
        sections = driver.find_elements(By.CSS_SELECTOR, "div.job-description__item, section.jd-section")
        for sec in sections:
            header = ""
            try:
                header = sec.find_element(By.CSS_SELECTOR, "h3, h4, strong").text.lower()
            except NoSuchElementException:
                pass
            content = sec.text.strip()
            if any(k in header for k in ["mô tả", "description", "về công việc"]):
                result["description"] = content
            elif any(k in header for k in ["yêu cầu", "requirement", "kỹ năng"]):
                result["requirement"] = content
            elif any(k in header for k in ["phúc lợi", "benefit", "quyền lợi"]):
                result["benefit"] = content
        if not any(result.values()):
            # fallback: take whole content block
            try:
                result["description"] = driver.find_element(
                    By.CSS_SELECTOR, "div.job-description, div#job-body"
                ).text.strip()
            except NoSuchElementException:
                pass
    except Exception:
        pass
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

    def _make_options(factory):
        opts = factory()
        opts.add_argument("--no-sandbox")
        opts.add_argument("--disable-dev-shm-usage")
        opts.add_argument("--disable-gpu")
        opts.add_argument("--disable-background-networking")
        opts.add_argument("--disable-sync")
        opts.add_argument("--metrics-recording-only")
        opts.add_argument("--mute-audio")
        opts.page_load_strategy = "eager"
        return opts

    try:
        driver = uc.Chrome(headless=True, options=_make_options(uc.ChromeOptions))
        logger.info("Undetected ChromeDriver OK")
    except Exception as e:
        logger.warning("Undetected ChromeDriver failed: %s, fallback to regular Chrome", e)
        from selenium import webdriver as _wd
        from selenium.webdriver.chrome.options import Options as _Opts
        from selenium.webdriver.chrome.service import Service as _Svc
        from webdriver_manager.chrome import ChromeDriverManager as _CDM
        driver = _wd.Chrome(
            service=_Svc(_CDM().install()),
            options=_make_options(_Opts),
        )

    try:
        job_urls = []
        for page in range(1, NUM_PAGES + 1):
            if total + len(job_urls) >= MAX_JOBS:
                break
            list_url = f"{BASE_URL}?page={page}"
            try:
                driver.get(list_url)
                time.sleep(3)
                cards = driver.find_elements(By.CSS_SELECTOR, "div.job-content a.job-title, h3.title a")
                for a in cards:
                    href = a.get_attribute("href") or ""
                    if href and "itviec.com" in href and href not in processed:
                        job_urls.append(href)
                logger.info("Page %d: %d job URLs collected so far", page, len(job_urls))
            except (TimeoutException, WebDriverException) as e:
                logger.warning("Failed to load page %d: %s", page, e)
                break

        job_urls = list(dict.fromkeys(job_urls))[:MAX_JOBS]

        for job_url in job_urls:
            try:
                driver.get(job_url)
                time.sleep(2)

                title = _safe(driver, "h1.job-title, h1[data-automation='job-title'], h1")
                company = _safe(driver, "div.employer-name a, a.company-name, span.company-name")
                location = _safe(driver, "div.location svg + span, span.location, div.address")
                salary = _safe(driver, "div.salary-range, span.salary, div.box-salary")
                level = _safe(driver, "div.job-level, span.level, li:contains('Level')")

                sections = _extract_sections(driver)
                skills = _parse_skills(driver)

                posted_date = ""
                try:
                    date_el = driver.find_element(By.CSS_SELECTOR, "time, span.posted-date")
                    posted_date = date_el.get_attribute("datetime") or date_el.text.strip()
                except NoSuchElementException:
                    pass

                if not title or not company:
                    continue

                job_data = {
                    "title": title, "company": company, "location": location,
                    "salary": salary, "level": level,
                    "description": sections["description"],
                    "requirement": sections["requirement"],
                    "benefit": sections["benefit"],
                    "skills": skills, "source_url": job_url,
                    "posted_date": posted_date,
                }
                jobs.append(job_data)
                _save_url(URL_CACHE, job_url)
                processed.add(job_url)

                if kafka_enabled:
                    kafka.send_job(
                        job_title=title, company_name=company, location=location,
                        salary=salary, level=level,
                        description=sections["description"],
                        requirement=sections["requirement"],
                        benefit=sections["benefit"],
                        skills=skills, source_url=job_url,
                        posted_date=posted_date, source_platform=SOURCE_PLATFORM,
                    )

                total += 1
                logger.info("[%d] %s @ %s", total, title[:50], company[:30])

            except (TimeoutException, WebDriverException) as e:
                logger.warning("Failed job %s: %s", job_url, e)

    finally:
        driver.quit()
        gc.collect()
        kafka.close()

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump({"source_platform": SOURCE_PLATFORM, "jobs": jobs}, f,
                  ensure_ascii=False, indent=2)

    logger.info("ITviec done: %d jobs", total)


if __name__ == "__main__":
    main()
