from selenium import webdriver
import undetected_chromedriver as uc
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import Select
from selenium.common.exceptions import NoSuchElementException
import time
import random
import json
import os
from datetime import datetime, timedelta
import traceback
def safe_find(driver, css):
    try:
        return driver.find_element(By.CSS_SELECTOR, css).text.strip()
    except NoSuchElementException:
        return ""

def scrape_job_description(driver):
    """
    Cào job description từ trang chi tiết công việc.

    Logic:
    - Lấy tất cả div.job-description__item--content (mỗi div là 1 section).
    - Nếu section chứa <ul>: lấy <li>, nối bằng ". ".
    - Nếu section không có <ul>: lấy <p>, nối bằng ". ".
    - Các section cách nhau bằng "\n\n".

    Trả về: string content
    """
    try:
        # Lấy tất cả section nội dung (Mô tả công việc, Yêu cầu ứng viên, ...)
        sections = driver.find_elements(By.CLASS_NAME, "job-description__item--content")

        paragraphs = []
        for section in sections:
            ul_elements = section.find_elements(By.TAG_NAME, "ul")

            if ul_elements:
                # Trường hợp dùng <ul><li>
                for ul in ul_elements:
                    li_elements = ul.find_elements(By.TAG_NAME, "li")
                    sentences = [li.text.strip() for li in li_elements if li.text.strip()]
                    if sentences:
                        paragraphs.append(". ".join(sentences))
            else:
                # Trường hợp dùng <p> trực tiếp
                p_elements = section.find_elements(By.TAG_NAME, "p")
                sentences = [p.text.strip() for p in p_elements if p.text.strip()]
                if sentences:
                    paragraphs.append(". ".join(sentences))

        # Các section cách nhau bằng dòng trống
        content = "\n\n".join(paragraphs)
        return content

    except NoSuchElementException:
        print("  [CẢNH BÁO] Không tìm thấy job-description__item--content")
        return ""

driver = uc.Chrome(version_main=147)

base_url_page1 = "https://www.topcv.vn/tim-viec-lam-cong-nghe-thong-tin-cr257?type_keyword=1&category_family=r257&saturday_status=0"
base_url_paged = "https://www.topcv.vn/tim-viec-lam-cong-nghe-thong-tin-cr257?type_keyword=1&page={page}&category_family=r257&saturday_status=0"
num_pages = 6  # Số trang muốn cào

driver.get(base_url_page1)

source_platform = driver.find_element(By.CSS_SELECTOR, "a.header-menu-mobile__logo img").get_attribute("title")

scraped_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

data = {
    "source_platform": source_platform,
    "source_url": base_url_page1,
    "scraped_at": scraped_at,
    "post_detail": []
}

wait = WebDriverWait(driver, 10)

seen_links = set()  # Tránh trùng bài khi cùng link xuất hiện nhiều trang

for page in range(1, num_pages + 1):
    print(f"\n{'='*40}")
    print(f"Đang cào trang {page}/{num_pages}")

    # Build URL theo số trang
    page_url = base_url_page1 if page == 1 else base_url_paged.format(page=page)
    driver.get(page_url)
    time.sleep(random.uniform(2, 4))

    # Lấy tất cả link bài đăng trên trang hiện tại
    elements_links = driver.find_elements(By.CSS_SELECTOR, "h3.title a")
    links = [
        e.get_attribute("href")
        for e in elements_links
        if e.get_attribute("href") and e.get_attribute("href") not in seen_links
    ]
    # Loại trùng trong trang, đồng thời thêm vào seen_links
    unique_links = []
    for lk in links:
        if lk not in seen_links:
            seen_links.add(lk)
            unique_links.append(lk)

    print(f"  Tìm thấy {len(unique_links)} bài trên trang này")

    for idx, link in enumerate(unique_links):
        print(f"\n  Đang xử lý bài {idx + 1}/{len(unique_links)}")

        try:
            driver.get(link)
            time.sleep(random.uniform(5, 7))  # chống detect là bot

            title = safe_find(driver, "h1.job-detail__info--title")
            if not title:
                print(f"  [BỎ QUA] Không cào được title tại: {link}")
                continue

            content = scrape_job_description(driver)
            post_detail = {
                "title": title,
                "content": content
            }
            data["post_detail"].append(post_detail)

        except Exception as e:
            print(f"  [LỖI] Xử lý bài {link} thất bại. Chi tiết: {e}")
            continue

driver.quit()

os.makedirs("raw_data", exist_ok=True)
output_file = os.path.join("raw_data", "raw_data_topCV.json")
with open(output_file, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print(f"\nĐã lưu dữ liệu vào file: {output_file}")
print(f"\nTổng số bài viết: {len(data['post_detail'])}")