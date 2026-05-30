#!/usr/bin/env python3
"""
Script sửa các file JSON bị lỗi:
- Xóa dấu phẩy thừa
- Sửa cấu trúc lồng nhau sai
- Chuẩn hóa format

Sử dụng: python fix_json_files.py
"""

import os
import json
import re
import shutil
from datetime import datetime

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def fix_json_content(content: str) -> dict:
    """
    Sửa nội dung JSON bị lỗi và trả về dict hợp lệ.
    """
    # Thử parse trực tiếp
    try:
        return json.loads(content)
    except json.JSONDecodeError:
        pass
    
    # Xóa dấu phẩy thừa trước } hoặc ]
    content = re.sub(r',(\s*[}\]])', r'\1', content)
    
    # Thử parse lại
    try:
        return json.loads(content)
    except json.JSONDecodeError:
        pass
    
    # Thử sửa lỗi lồng nhau: {"post_detail": {...}}
    try:
        data = json.loads(content)
        if "post_detail" in data:
            pd = data["post_detail"]
            # Nếu post_detail là dict thay vì list
            if isinstance(pd, dict):
                if "post_detail" in pd:
                    data["post_detail"] = pd["post_detail"]
                else:
                    # Wrap single article into list
                    data["post_detail"] = [pd]
        return data
    except json.JSONDecodeError:
        pass
    
    # Approach: find all valid article objects manually
    articles = []
    
    # Pattern tìm các article object
    article_pattern = re.compile(
        r'\{\s*"title"\s*:\s*"[^"]*"[^}]*"content"\s*:\s*"[^"]*"[^}]*\}',
        re.DOTALL
    )
    
    for match in article_pattern.finditer(content):
        try:
            article = json.loads(match.group())
            articles.append(article)
        except:
            continue
    
    if articles:
        return {"post_detail": articles}
    
    return None


def process_json_file(filepath: str) -> bool:
    """
    Xử lý một file JSON. Trả về True nếu sửa thành công.
    """
    print(f"\n📁 {os.path.basename(filepath)}")
    
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"  ❌ Lỗi đọc: {e}")
        return False
    
    # Thử parse trước
    try:
        data = json.loads(content)
        print(f"  ✓ File hợp lệ, không cần sửa")
        return True
    except json.JSONDecodeError as e:
        print(f"  ⚠ Lỗi JSON: {e}")
    
    # Backup file gốc
    backup_path = filepath + '.bak'
    try:
        shutil.copy(filepath, backup_path)
        print(f"  → Đã backup: {os.path.basename(backup_path)}")
    except Exception as e:
        print(f"  ⚠ Không thể backup: {e}")
    
    # Sửa file
    fixed_data = fix_json_content(content)
    
    if fixed_data is None:
        print(f"  ❌ Không thể sửa file")
        return False
    
    # Lưu file đã sửa
    try:
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(fixed_data, f, ensure_ascii=False, indent=2)
        
        article_count = len(fixed_data.get("post_detail", []))
        print(f"  ✓ Đã sửa! {article_count} bài viết")
        return True
    except Exception as e:
        print(f"  ❌ Lỗi ghi: {e}")
        # Restore backup
        if os.path.exists(backup_path):
            shutil.move(backup_path, filepath)
        return False


def main():
    print("=" * 55)
    print("SCRIPT SỬA FILE JSON BỊ LỖI")
    print("=" * 55)
    
    # Các thư mục cần kiểm tra
    raw_dirs = [
        os.path.join(BASE_DIR, "data", "raw"),
        os.path.join(BASE_DIR, "crawl", "data", "raw"),
    ]
    
    total_fixed = 0
    total_failed = 0
    
    for raw_dir in raw_dirs:
        if not os.path.exists(raw_dir):
            continue
            
        print(f"\n🔍 Thư mục: {raw_dir}")
        
        for root, dirs, files in os.walk(raw_dir):
            for f in files:
                if f.endswith('.json') and not f.endswith('.bak'):
                    filepath = os.path.join(root, f)
                    if process_json_file(filepath):
                        total_fixed += 1
                    else:
                        total_failed += 1
    
    print(f"\n{'=' * 55}")
    print(f"Hoàn thành! Đã sửa: {total_fixed}, Thất bại: {total_failed}")
    print(f"{'=' * 55}")


if __name__ == "__main__":
    main()