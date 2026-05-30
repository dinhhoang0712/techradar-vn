"""
Script xoa cac truong khong can thiet trong file vietnamese_job_descriptions.json.
Cac truong bi xoa: id, job_type, year, experience_level, education_level, job_position
Ket qua luu vao: vietnamese_job_descriptions_cleaned.json
"""

import json
import os
import sys

# Fix Windows console encoding
sys.stdout.reconfigure(encoding="utf-8")

# Đường dẫn file
INPUT_FILE = os.path.join(os.path.dirname(__file__), "vietnamese_job_descriptions.json")
OUTPUT_FILE = os.path.join(os.path.dirname(__file__), "raw_data_job_descriptions.json")

# Các trường cần xóa
FIELDS_TO_REMOVE = {"id", "job_type", "year", "experience_level", "education_level", "job_position"}


def clean_records(records: list[dict]) -> list[dict]:
    """Xóa các trường không cần thiết khỏi mỗi bản ghi."""
    cleaned = []
    for record in records:
        cleaned_record = {k: v for k, v in record.items() if k not in FIELDS_TO_REMOVE}
        cleaned.append(cleaned_record)
    return cleaned


def main():
    print(f"Đang đọc file: {INPUT_FILE}")
    with open(INPUT_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)

    total = len(data)
    print(f"Tổng số bản ghi: {total}")
    print(f"Các trường sẽ bị xóa: {', '.join(sorted(FIELDS_TO_REMOVE))}")

    cleaned_data = clean_records(data)

    print(f"Đang ghi file kết quả: {OUTPUT_FILE}")
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(cleaned_data, f, ensure_ascii=False, indent=2)

    print(f"✅ Hoàn thành! Đã lưu {len(cleaned_data)} bản ghi vào '{os.path.basename(OUTPUT_FILE)}'")

    # In ví dụ bản ghi đầu tiên sau khi làm sạch
    if cleaned_data:
        print("\n--- Ví dụ bản ghi đầu tiên sau khi xóa trường ---")
        print(json.dumps(cleaned_data[0], ensure_ascii=False, indent=2)[:800])


if __name__ == "__main__":
    main()
