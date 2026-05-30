import re
import torch
from underthesea import word_tokenize, ner
from transformers import AutoTokenizer, AutoModelForSequenceClassification

# ============================================================
# 1. CẤU HÌNH
# ============================================================
MODEL_PATH = 'C:\\Users\\Admin\\MyProject\\Final_Data_Mining\\src\\data-pipeline\\phobert_title_classifier_best'  # Đường dẫn thư mục model đã tải từ Drive
MAX_LEN = 256

# ============================================================
# 2. LOAD MODEL & TOKENIZER
# ============================================================
print("Đang load model...")
tokenizer = AutoTokenizer.from_pretrained(MODEL_PATH)
model = AutoModelForSequenceClassification.from_pretrained(MODEL_PATH)
model.eval()

device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
model.to(device)
print(f"Model loaded trên {device}")

# ============================================================
# 3. HÀM TIỀN XỬ LÝ (giống notebook training)
# ============================================================
def preprocess_title(text):
    """Tiền xử lý tiêu đề bằng Underthesea (giống lúc training)."""
    if not text or not isinstance(text, str):
        return ''
    
    # NER
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
    text = re.sub(r'[.,;:!?\"\'\'()\[\]{}\-–—…""\'\'`]', ' ', text)
    text = word_tokenize(text, format='text')
    text = text.lower().strip()
    text = re.sub(r'\s+', ' ', text)
    return text

# ============================================================
# 4. HÀM DỰ ĐOÁN
# ============================================================
def predict_title(title):
    """Dự đoán label cho 1 tiêu đề."""
    # Preprocessing
    processed = preprocess_title(title)
    
    # Tokenize
    inputs = tokenizer(
        processed,
        padding='max_length',
        truncation=True,
        max_length=MAX_LEN,
        return_tensors='pt'
    ).to(device)
    
    # Predict
    with torch.no_grad():
        outputs = model(**inputs)
        probs = torch.softmax(outputs.logits, dim=-1)
        pred_label = torch.argmax(probs, dim=-1).item()
        confidence = probs[0][pred_label].item()
    
    label_name = 'IT' if pred_label == 1 else 'Non-IT'
    
    print(f"Tiêu đề:    {title}")
    print(f"Kết quả:    {label_name} (confidence: {confidence:.2%})")
    print(f"  P(Non-IT): {probs[0][0].item():.2%} | P(IT): {probs[0][1].item():.2%}")
    print()
    
    return {'label': pred_label, 'label_name': label_name, 'confidence': confidence}

# ============================================================
# 5. TEST
# ============================================================
if __name__ == '__main__':
    titles = [
        "Google ra mắt mô hình AI Gemini 2.0 với khả năng lập trình tự động",
        "Hướng dẫn deploy ứng dụng React lên AWS Lambda",
        "Samsung ra mắt tủ lạnh thông minh mới tại Việt Nam",
        "Tai nghe Sony WH-1000XM5 giảm giá sốc dịp Black Friday",
    ]
    
    print("=" * 60)
    print("🔮 DỰ ĐOÁN TIÊU ĐỀ")
    print("=" * 60)
    for t in titles:
        predict_title(t)