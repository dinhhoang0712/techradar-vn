
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.common.exceptions import NoSuchElementException
from selenium.webdriver.support.ui import WebDriverWait
from selenium.common.exceptions import StaleElementReferenceException
import time
import json
import os
from datetime import datetime

driver = webdriver.Chrome()

# part = 1
# source_url = "https://genk.vn/ai.chn" 
# part = 2
# source_url = "https://genk.vn/internet.chn" 
# part = 3
# source_url = "https://genk.vn/do-choi-so.chn" 
part = 4
source_url = "https://genk.vn/tin-ict.chn" 


driver.get(source_url)
time.sleep(3)  # Chờ trang load xong

# === Cuộn trang xuống cho đến khi thấy nút "Xem thêm" (a.btnviewmore) ===
def _view_more_visible(driver):
    """Trả về (True, element) nếu nút hiển thị; dùng scrollIntoView để đưa vào khung nhìn."""
    for btn in driver.find_elements(By.CSS_SELECTOR, "a.btnviewmore"):
        try:
            text = (btn.text or "").strip()
            if "Xem thêm" not in text:
                continue
            driver.execute_script(
                "arguments[0].scrollIntoView({block: 'center', inline: 'nearest'});",
                btn,
            )
            time.sleep(0.4)
            if btn.is_displayed():
                return True, btn
        except StaleElementReferenceException:
            continue
    return False, None


max_scrolls = 100
scroll_count = 0
last_height = 0
stuck = 0
found_btn = False

while scroll_count < max_scrolls:
    visible, _ = _view_more_visible(driver)
    if visible:
        found_btn = True
        #print(f"Đã tìm thấy nút 'Xem thêm' sau {scroll_count} lần cuộn.")
        break

    driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
    time.sleep(2)

    height = driver.execute_script(
        "return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);"
    )
    if height <= last_height:
        stuck += 1
        if stuck >= 3:
            visible, _ = _view_more_visible(driver)
            if visible:
                found_btn = True
                print(f"Đã tìm thấy nút 'Xem thêm' sau {scroll_count} lần cuộn (chiều cao trang không đổi).")
            break
    else:
        stuck = 0
    last_height = height
    scroll_count += 1
# if not found_btn:
#     print(f"Không thấy 'Xem thêm' sau {max_scrolls} lần cuộn — vẫn cào phần đã load.")

# Cuộn lại đầu trang trước khi scrape
driver.execute_script("window.scrollTo(0, 0);")
time.sleep(1)

source_platform = "GenK-Trang thông tin điện tử từ tổng hợp"

scraped_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

data = {
    "source_platform": source_platform,
    "source_url": source_url,
    "scraped_at": scraped_at,
    "post_detail": []
}

wait = WebDriverWait(driver, 10)

posts_info = []
seen_links = set()

articles = driver.find_elements(By.CSS_SELECTOR, "div.elp-list")

added_this_page = 0  # Số bài đã thêm
not_found_link = 0  # Số bài không tìm thấy link
for article in articles:
    link_els = article.find_elements(By.CSS_SELECTOR, 'h4.knswli-title a')

    title_els = article.find_elements(By.CSS_SELECTOR, 'h4.knswli-title')

    link = link_els[0].get_attribute("href") if link_els else ""
    title = title_els[0].text if title_els else ""

    if not link:  # Không có link → bỏ qua, không lấy tiêu đề
        not_found_link += 1
        continue
    if "eclick" in link:  # Link quảng cáo → bỏ qua
        continue
    if link in seen_links:  # Đã xử lý → bỏ qua
        continue
    seen_links.add(link)
    posts_info.append({
        "title": title,
        "link": link
        })
    added_this_page += 1

print(f"Tìm thấy {len(articles)} phần tử, thêm {added_this_page} bài (bỏ qua {not_found_link} bài không tìm thấy link)")

for idx, post in enumerate(posts_info):
    print(f"\nĐang xử lý bài {idx + 1}/{len(posts_info)}: {post['title'][:50]}...")
    
    driver.get(post['link'])
    
    try:
        article = driver.find_element(By.ID, "ContentDetail")
        paragraphs = article.find_elements(By.CSS_SELECTOR, "p")
        content = "\n\n".join([p.text.strip() for p in paragraphs if p.text.strip()])
    except NoSuchElementException:
        print(f"  ⚠ Không tìm thấy nội dung bài viết, bỏ qua.")
        continue

    post_detail = {
        "title": post['title'],
        "content": content,    
    }
    
    data["post_detail"].append(post_detail)

driver.quit()

os.makedirs("raw_data", exist_ok=True)
if part == 1:
    output_file = os.path.join("raw_data", "raw_data_GenK_part1.json")
elif part == 2:
    output_file = os.path.join("raw_data", "raw_data_GenK_part2.json")
elif part == 3:
    output_file = os.path.join("raw_data", "raw_data_GenK_part3.json")
else:
    output_file = os.path.join("raw_data", "raw_data_GenK_part4.json")
with open(output_file, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print(f"\nĐã lưu dữ liệu vào file: {output_file}")
