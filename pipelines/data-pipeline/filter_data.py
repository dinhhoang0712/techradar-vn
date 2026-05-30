"""
Lọc bài viết IT/Non-IT bằng model PhoBERT đã fine-tune.
Đầu vào : các file raw_data/raw_data_*.json
Đầu ra  : các file filtered_data/filtered_data_*.json
          (mỗi bài được gán thêm trường "is_relevant": true/false)

Pipeline tiền xử lý giống lúc training (test.py):
  NER (PER/ORG → "name", LOC → "loc") + normalize số/ngày/% + word_tokenize (underthesea)
"""

import re
import json
import os
import sys
import glob

# Fix encoding cho Windows terminal
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

import torch
from transformers import AutoTokenizer, AutoModelForSequenceClassification
from underthesea import word_tokenize, ner

# ==========================================
# CẤU HÌNH
# ==========================================

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Đường dẫn model đã fine-tune
MODEL_DIR = os.path.join(BASE_DIR, "phobert_title_classifier_best")

# Thư mục chứa file raw_data_*.json (đầu vào)
RAW_DATA_DIR = os.path.join(BASE_DIR, "raw_data")

# Thư mục chứa file filtered_data_*.json (đầu ra)
FILTERED_DATA_DIR = os.path.join(BASE_DIR, "filtered_data")

MAX_LEN = 256

# ==========================================
# LOAD MODEL & TOKENIZER
# ==========================================

print("Đang load model PhoBERT classifier...")
tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR)
model = AutoModelForSequenceClassification.from_pretrained(MODEL_DIR)
model.eval()

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
model.to(device)
print(f"Model loaded trên {device}")


# ==========================================
# TIỀN XỬ LÝ (giống pipeline training)
# ==========================================

def preprocess_title(text):
    """Tiền xử lý tiêu đề bằng Underthesea (giống lúc training)."""
    if not text or not isinstance(text, str):
        return ''

    # NER: thay tên người/tổ chức → "name", địa danh → "loc"
    try:
        entities = ner(text)
        for word, pos, chunk, ner_tag in reversed(entities):
            if ner_tag in ('B-PER', 'I-PER', 'B-ORG', 'I-ORG'):
                text = text.replace(word, 'name', 1)
            elif ner_tag in ('B-LOC', 'I-LOC'):
                text = text.replace(word, 'loc', 1)
    except Exception:
        pass

    text = re.sub(r'\d+[.,]?\d*\s*%', 'percent', text)
    text = re.sub(r'\d{1,2}[/-]\d{1,2}[/-]\d{2,4}', 'date', text)
    text = re.sub(r'\d{1,2}[/-]\d{2,4}', 'date', text)
    text = re.sub(r'[Nn]ăm\s+\d{4}', 'date', text)
    text = re.sub(r'[Tt]háng\s+\d{1,2}', 'date', text)
    text = re.sub(r'[Qq]uý\s+\d', 'date', text)
    text = re.sub(r'\d+[.,]?\d*', 'number', text)
    text = re.sub(r'[.,;:!?\"\'\'()\\[\\]{}\\-–—…""\'\'`]', ' ', text)
    text = word_tokenize(text, format='text')
    text = text.lower().strip()
    text = re.sub(r'\s+', ' ', text)
    return text


# ==========================================
# HÀM PHÂN LOẠI
# ==========================================

def predict_one(title):
    """Dự đoán label cho 1 tiêu đề, trả về (bool, confidence)."""
    processed = preprocess_title(title)

    inputs = tokenizer(
        processed,
        padding='max_length',
        truncation=True,
        max_length=MAX_LEN,
        return_tensors='pt'
    ).to(device)

    with torch.no_grad():
        outputs = model(**inputs)
        probs = torch.softmax(outputs.logits, dim=-1)
        pred_label = torch.argmax(probs, dim=-1).item()
        confidence = probs[0][pred_label].item()

    is_it = (pred_label == 1)
    return is_it, confidence


def classify_titles(titles):
    """
    Nhận danh sách tiêu đề, trả về list[bool].
    True = IT (relevant), False = Non-IT.
    Thực hiện tiền xử lý giống pipeline training trước khi tokenize.
    """
    results = []
    for title in titles:
        is_it, _ = predict_one(title)
        results.append(is_it)
    return results


# ==========================================
# XỬ LÝ FILE JSON
# ==========================================

def _resolve_output_path(input_path):
    """raw_data_XYZ.json → filtered_data/filtered_data_XYZ.json"""
    base = os.path.basename(input_path)
    for prefix in ("raw_data_",):
        if base.startswith(prefix):
            suffix = base[len(prefix):]
            break
    else:
        suffix = base
    os.makedirs(FILTERED_DATA_DIR, exist_ok=True)
    return os.path.join(FILTERED_DATA_DIR, "filtered_data_" + suffix)


def filter_json_file(input_path):
    """
    Đọc file raw JSON, chạy model phân loại tiêu đề,
    gán nhãn is_relevant (true/false) cho mỗi bài,
    ghi ra file filtered_data_*.json.

    Hỗ trợ 2 định dạng:
      - Format cũ (VN-EP, DanTri...): JSON object có key "post_detail" chứa list bài.
      - Format mới (job_descriptions...): JSON array trực tiếp các object bài.
    """
    print(f"\n{'=' * 55}")
    print(f"Đang xử lý: {os.path.basename(input_path)}")
    print(f"{'=' * 55}")

    with open(input_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    # --- Nhận diện định dạng ---
    if isinstance(data, list):
        # Format mới: array trực tiếp
        fmt = "array"
        posts = data
    elif isinstance(data, dict):
        # Format cũ: object với key "post_detail"
        fmt = "object"
        posts = data.get("post_detail", [])
    else:
        print("  Định dạng JSON không hợp lệ.")
        return ""

    print(f"Định dạng phát hiện: {'array trực tiếp' if fmt == 'array' else 'object (post_detail)'}")
    print(f"Tổng số bài: {len(posts)}")

    if not posts:
        print("  Không có bài nào để xử lý.")
        return ""

    titles = [post.get("title") or post.get("job_title") or "" for post in posts]

    print(f"Đang phân loại {len(titles)} tiêu đề...")
    print(f"  (Tiền xử lý: NER + word_tokenize + normalize)")

    # Classify và in kết quả từng bài
    # Nếu title là null/rỗng → mặc định gán False, không chạy model
    predictions = []
    for i, title in enumerate(titles):
        if not title or not title.strip():
            predictions.append(False)
            print(f"  [{i + 1:3d}] ✗ Non-IT  (—  ) | [KHÔNG CÓ TIÊU ĐỀ]")
            continue
        is_it, conf = predict_one(title)
        predictions.append(is_it)
        mark = "✓" if is_it else "✗"
        label = "IT     " if is_it else "Non-IT "
        print(f"  [{i + 1:3d}] {mark} {label} ({conf:.0%}) | {title[:60]}")

    # Gán nhãn
    for post, is_it in zip(posts, predictions):
        post["is_relevant"] = is_it

    # Thống kê
    relevant_count = sum(predictions)

    # Ghi file output (giữ nguyên cấu trúc gốc)
    output_path = _resolve_output_path(input_path)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print(f"\n  ✓ Liên quan (IT): {relevant_count}/{len(posts)} bài")
    print(f"  → {output_path}")
    return output_path


# ==========================================
# MAIN
# ==========================================

def main():
    pattern = os.path.join(RAW_DATA_DIR, "raw_data_*.json")
    json_files = sorted(glob.glob(pattern))

    if not json_files:
        print(f"Không tìm thấy file raw_data_*.json trong: {RAW_DATA_DIR}")
        return

    print(f"Tìm thấy {len(json_files)} file cần xử lý:")
    for f in json_files:
        print(f"  - {os.path.basename(f)}")

    for file_path in json_files:
        filter_json_file(file_path)

    print(f"\n{'=' * 55}")
    print("Hoàn thành! Kiểm tra filtered_data/filtered_data_*.json")
    print(f"{'=' * 55}")


if __name__ == "__main__":
    main()
