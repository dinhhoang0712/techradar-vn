from datasets import load_dataset
import json

# Load dataset từ HuggingFace
dataset = load_dataset(
    "tinixai/vietnamese-job-descriptions",
    split="train",
)

print(dataset)
print(dataset[0])

# Lấy 10000 bản ghi đầu tiên và lưu thành JSON
subset = dataset.select(range(10000))
records = [subset[i] for i in range(len(subset))]

output_path = "vietnamese_job_descriptions_1000.json"
with open(output_path, "w", encoding="utf-8") as f:
    json.dump(records, f, ensure_ascii=False, indent=2)

print(f"\n✅ Đã lưu {len(records)} bản ghi vào file: {output_path}")
