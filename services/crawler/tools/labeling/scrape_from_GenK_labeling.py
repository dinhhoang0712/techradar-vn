import argparse
import json
import os
import time
from datetime import datetime

from selenium import webdriver
from selenium.common.exceptions import NoSuchElementException
from selenium.common.exceptions import StaleElementReferenceException
from selenium.webdriver.common.by import By


GENK_SOURCE_URL = "https://genk.vn/ai.chn"


def parse_args():
    parser = argparse.ArgumentParser(description="Cào dữ liệu GenK để gán nhãn")
    parser.add_argument("--source-url", default=GENK_SOURCE_URL, help="Nguồn GenK cần cào")
    parser.add_argument("--max-posts", type=int, default=100, help="Số bài tối đa")
    parser.add_argument("--max-load-rounds", type=int, default=180, help="Số vòng load thêm tối đa cho source")
    parser.add_argument("--output-file", default="raw_data_GenK_label.json", help="Tên file output")
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


def click_view_more_button(driver):
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
                btn.click()
                return True
        except StaleElementReferenceException:
            continue
    return False


def collect_links_from_source(driver, source_url, posts_info, seen_links, max_posts, max_load_rounds):
    print(f"\n{'=' * 60}")
    print(f"Đang cào source: {source_url}")
    print(f"{'=' * 60}")

    driver.get(source_url)
    time.sleep(2)

    no_new_rounds = 0

    for round_idx in range(1, max_load_rounds + 1):
        before_count = len(posts_info)
        articles = driver.find_elements(By.CSS_SELECTOR, "div.elp-list")

        for article in articles:
            link_els = article.find_elements(By.CSS_SELECTOR, "h4.knswli-title a")
            title_els = article.find_elements(By.CSS_SELECTOR, "h4.knswli-title")

            link = link_els[0].get_attribute("href") if link_els else ""
            title = title_els[0].text if title_els else ""

            if not link or "eclick" in link or link in seen_links:
                continue

            seen_links.add(link)
            posts_info.append({"title": title, "link": link})

            if len(posts_info) >= max_posts:
                break

        added = len(posts_info) - before_count
        print(f"  Vòng {round_idx:03d}: +{added} bài mới | Tổng hiện tại: {len(posts_info)}")

        if len(posts_info) >= max_posts:
            return

        clicked = click_view_more_button(driver)
        if clicked:
            time.sleep(1.5)
        else:
            driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")
            time.sleep(1.5)

        if added == 0 and not clicked:
            no_new_rounds += 1
            if no_new_rounds >= 8:
                print("  Không còn bài mới sau nhiều lần thử, dừng cào source.")
                return
        else:
            no_new_rounds = 0


args = parse_args()
driver = webdriver.Chrome()

data = {
    "source_platform": "GenK-Trang thông tin điện tử từ tổng hợp",
    "source_url": args.source_url,
    "scraped_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
    "post_detail": [],
}

posts_info = []
seen_links = set()

collect_links_from_source(
    driver=driver,
    source_url=args.source_url,
    posts_info=posts_info,
    seen_links=seen_links,
    max_posts=args.max_posts,
    max_load_rounds=args.max_load_rounds,
)

print(f"Tổng số bài viết cần xử lý: {len(posts_info)}")

for idx, post in enumerate(posts_info):
    print(f"\nĐang xử lý bài {idx + 1}/{len(posts_info)}: {post['title'][:50]}...")
    driver.get(post["link"])

    try:
        article = driver.find_element(By.ID, "ContentDetail")
        paragraphs = article.find_elements(By.CSS_SELECTOR, "p")
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
