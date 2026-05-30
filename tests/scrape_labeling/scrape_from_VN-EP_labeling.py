import argparse
import json
import os
import time
from datetime import datetime

from selenium import webdriver
from selenium.common.exceptions import NoSuchElementException
from selenium.webdriver.common.by import By


def parse_args():
    parser = argparse.ArgumentParser(description="Cào dữ liệu VnExpress để gán nhãn")
    parser.add_argument("--num-pages", type=int, default=20, help="Số trang cần cào")
    parser.add_argument("--max-posts", type=int, default=100, help="Số bài tối đa")
    parser.add_argument("--output-file", default="raw_data_VNEP_label.json", help="Tên file output")
    return parser.parse_args()


def resolve_output_path(output_file):
    base_output_dir = os.path.join(os.path.dirname(__file__), "output_data")
    if os.path.isabs(output_file) or os.path.dirname(output_file):
        out_dir = os.path.dirname(output_file)
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
        return output_file
    os.makedirs(base_output_dir, exist_ok=True)
    return os.path.join(base_output_dir, output_file)


args = parse_args()
driver = webdriver.Chrome()

source_url = "https://vnexpress.net/khoa-hoc-cong-nghe/ai"
driver.get(source_url)

source_platform = driver.find_element(By.CSS_SELECTOR, "a.logo").get_attribute("title")
scraped_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

data = {
    "source_platform": source_platform,
    "source_url": source_url,
    "scraped_at": scraped_at,
    "post_detail": [],
}

posts_info = []
seen_links = set()

for page in range(1, args.num_pages + 1):
    print(f"{'=' * 40}\n")
    print(f"Đang cào trang {page}")

    base_url = source_url.rstrip("/")
    if "-p" in base_url:
        base_url = base_url.rsplit("-p", 1)[0]
    page_url = base_url if page == 1 else f"{base_url}-p{page}"
    driver.get(page_url)
    time.sleep(2)

    articles = driver.find_elements(By.CSS_SELECTOR, "article.item-news.item-news-common.thumb-left:not(.hidden)")
    added_this_page = 0
    not_found_link = 0

    for article in articles:
        title_els = article.find_elements(By.CSS_SELECTOR, "h2.title-news")
        link_els = article.find_elements(By.CSS_SELECTOR, "h2.title-news a")

        title = title_els[0].text if title_els else ""
        link = link_els[0].get_attribute("href") if link_els else ""

        if not link:
            not_found_link += 1
            continue
        if "eclick" in link:
            continue
        if link in seen_links:
            continue

        seen_links.add(link)
        posts_info.append({"title": title, "link": link})
        added_this_page += 1

        if len(posts_info) >= args.max_posts:
            break

    print(f"  Tìm thấy {len(articles)} phần tử, thêm {added_this_page} bài (bỏ qua {not_found_link} bài không tìm thấy link)")

    if len(posts_info) >= args.max_posts:
        break

print(f"\nTổng cộng {len(posts_info)} bài viết để xử lý")

for idx, post in enumerate(posts_info):
    print(f"\nĐang xử lý bài {idx + 1}/{len(posts_info)}: {post['title'][:50]}...")
    driver.get(post["link"])

    try:
        article = driver.find_element(By.ID, "fck_detail_gallery")
        paragraphs = article.find_elements(By.CSS_SELECTOR, "p.Normal")
        content = "\n\n".join([p.text.strip() for p in paragraphs if p.text.strip()])
    except NoSuchElementException:
        print("  ⚠ Không tìm thấy nội dung bài viết, bỏ qua.")
        continue

    data["post_detail"].append({"title": post["title"], "content": content})

driver.quit()

output_file = resolve_output_path(args.output_file)
with open(output_file, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print(f"\nĐã lưu dữ liệu vào file: {output_file}")
