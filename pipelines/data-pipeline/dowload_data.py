from datasets import load_dataset
import json
import os

# Số bản ghi lấy ra (override bằng env NUM_RECORDS nếu cần)
NUM_RECORDS = int(os.environ.get("NUM_RECORDS", "10000"))
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
# Tên file phải khớp INPUT của clean_json_fields.py
OUTPUT_PATH = os.path.join(BASE_DIR, "vietnamese_job_descriptions.json")

# Load dataset từ HuggingFace
dataset = load_dataset(
    "tinixai/vietnamese-job-descriptions",
    split="train",
)

print(dataset)
print(dataset[0])

# Lấy NUM_RECORDS bản ghi đầu tiên và lưu thành JSON
n = min(NUM_RECORDS, len(dataset))
subset = dataset.select(range(n))
records = [subset[i] for i in range(len(subset))]

with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
    json.dump(records, f, ensure_ascii=False, indent=2)

print(f"\n✅ Đã lưu {len(records)} bản ghi vào file: {OUTPUT_PATH}")
