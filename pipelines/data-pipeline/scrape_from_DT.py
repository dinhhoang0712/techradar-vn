from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.common.exceptions import NoSuchElementException
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import Select
import time
import json
import os
from datetime import datetime

driver = webdriver.Chrome()

#part = 1
#source_url = "https://dantri.com.vn/cong-nghe/ai-internet.htm"
part = 2
source_url = "https://dantri.com.vn/cong-nghe/an-ninh-mang.htm" 

num_pages = 10 

driver.get(source_url)

source_platform = driver.find_element(By.CSS_SELECTOR, "a[aria-label]").get_attribute("aria-label")

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
for page in range(1, num_pages + 1):
    print(f"{'='*40}\n")
    print(f"Đang cào trang {page}")
    
    page_url = source_url if page == 1 \
        else source_url.replace(".htm", f"/trang-{page}.htm")
    
    driver.get(page_url)
    
    time.sleep(2)
    
    articles = driver.find_elements(By.CSS_SELECTOR, "div.article-content")

    added_this_page = 0 
    not_found_link = 0 
    for article in articles:
        title_els = article.find_elements(By.CSS_SELECTOR, 'h3.article-title')

        link = article.find_element(By.CSS_SELECTOR, 'a[data-prop="sapo"]').get_attribute("href")
        title = title_els[0].text if title_els else ""
        
        if not link: # Nếu không có link thì bỏ qua
            not_found_link += 1 
            continue 
        if "eclick" in link: # Nếu link là quảng cáo thì bỏ qua
            continue 
        if link in seen_links: # Nếu link đã được xử lý thì bỏ qua
            continue
        seen_links.add(link) # Thêm link vào set để tránh trùng bài
        posts_info.append({
            "title": title,
            "link": link
        })
        added_this_page += 1 
    
    print(f"  Tìm thấy {len(articles)} phần tử, thêm {added_this_page} bài (bỏ qua {not_found_link} bài không tìm thấy link)") 

print(f"\nTổng cộng {len(posts_info)} bài viết từ {num_pages} trang")

for idx, post in enumerate(posts_info):
    print(f"\nĐang xử lý bài {idx + 1}/{len(posts_info)}: {post['title'][:50]}...")
    
    driver.get(post['link'])
    
    try:
        article = driver.find_element(By.ID, "desktop-in-article")
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
    output_file = os.path.join("raw_data", "raw_data_DT_part1.json")
else:
    output_file = os.path.join("raw_data", "raw_data_DT_part2.json")
with open(output_file, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print(f"\nĐã lưu dữ liệu vào file: {output_file}")
