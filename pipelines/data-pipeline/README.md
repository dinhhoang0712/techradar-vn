# 📊 Data Pipeline — Khai phá dữ liệu IT

Pipeline thu thập, lọc và trích xuất thực thể từ các bài viết / tin tuyển dụng IT tiếng Việt, gồm **4 giai đoạn chính**:

```
[Scraping & Download] ──► [Filtering] ──► [Entity Extraction] ──► [Cloud Upload]
```

---

## 📁 Cấu trúc thư mục

```
data-pipeline/
├── dowload_data.py                 # Tải dataset từ HuggingFace (tinixai/vietnamese-job-descriptions)
├── clean_json_fields.py            # Làm sạch dữ liệu JSON tải về (xoá các trường không cần thiết)
├── scrape_from_DT.py               # Scraper: Dân Trí (công nghệ)
├── scrape_from_GenK.py             # Scraper: GenK (AI, internet, ICT...)
├── scrape_from_topCV.py            # Scraper: TopCV (tin tuyển dụng IT)
├── scrape_from_VN-EP.py            # Scraper: VnExpress (khoa học-công nghệ)
├── filter_data.py                  # Lọc IT/Non-IT bằng PhoBERT
├── extract_data.py                 # Trích xuất thực thể (NER + rule-based)
├── upload_to_s3.py                 # Tải dữ liệu đã trích xuất (extracted_data) lên AWS S3
├── phobert_title_classifier_best/  # Model PhoBERT fine-tune (dùng trong filter_data.py)
├── raw_data/                       # Output của bước Scraping
├── filtered_data/                  # Output của bước Filtering
└── extracted_data/                 # Output của bước Extraction
```

---

## 🔄 Pipeline tổng quan

```
┌─────────────────────────────────────────────────────────────────────┐
│  BƯỚC 1 — THU THẬP DỮ LIỆU (Scraping & Download)                   │
│                                                                     │
│   dowload_data.py      ──►  vietnamese_job_descriptions_1000.json │
│   clean_json_fields.py ──►  raw_data_job_descriptions.json        │
│   scrape_from_DT.py    ──►  raw_data/raw_data_DT_part{1,2}.json   │
│   scrape_from_GenK.py  ──►  raw_data/raw_data_GenK_part{1..4}.json│
│   scrape_from_topCV.py ──►  raw_data/raw_data_topCV.json          │
│   scrape_from_VN-EP.py ──►  raw_data/raw_data_VN-EP.json          │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  BƯỚC 2 — FILTERING (Phân loại IT / Non-IT bằng PhoBERT)           │
│                                                                     │
│   filter_data.py                                                    │
│     Input  : raw_data/raw_data_*.json                               │
│     Output : filtered_data/filtered_data_*.json                     │
│              (mỗi bài được gán thêm "is_relevant": true/false)      │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  BƯỚC 3 — ENTITY EXTRACTION (Trích xuất thực thể NER)              │
│                                                                     │
│   extract_data.py                                                   │
│     Input  : filtered_data/filtered_data_*.json                     │
│     Output : extracted_data/extracted_data_phobert_*.json           │
│              (chỉ bài is_relevant=true, có thêm field "entities")   │
└─────────────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────────┐
│  BƯỚC 4 — CLOUD STORAGE (Tải dữ liệu lên AWS S3)                   │
│                                                                     │
│   upload_to_s3.py                                                   │
│     Input  : extracted_data/*.json                                  │
│     Output : AWS S3 Bucket (database-data-mining/extracted_data)    │
└─────────────────────────────────────────────────────────────────────┘
```

---

## ⚙️ Yêu cầu

### Thư viện Python

```bash
pip install selenium undetected-chromedriver
pip install torch transformers
pip install underthesea
pip install datasets boto3 python-dotenv
```

### Trình duyệt & Driver

| Script | Yêu cầu |
|--------|---------|
| `scrape_from_DT.py` | Google Chrome + ChromeDriver (phải cùng phiên bản) |
| `scrape_from_GenK.py` | Google Chrome + ChromeDriver |
| `scrape_from_topCV.py` | Google Chrome phiên bản **147** + `undetected-chromedriver` |
| `scrape_from_VN-EP.py` | Google Chrome + ChromeDriver |

> **Lưu ý:** `scrape_from_topCV.py` dùng `undetected_chromedriver` để vượt bot-detection của TopCV. Cần truyền đúng `version_main` khớp với phiên bản Chrome đang cài.

---

## 📝 Chi tiết từng bước

### Bước 1 — Scraping & Download Data

#### `dowload_data.py` & `clean_json_fields.py` — HuggingFace

- **Nguồn:** Dataset `tinixai/vietnamese-job-descriptions` trên HuggingFace
- **Cơ chế:** Dùng thư viện `datasets` để tải 10,000 bản ghi đầu tiên, sau đó làm sạch các trường không cần thiết (như `id`, `job_type`, `year`, `experience_level`...).
- **Output:** `raw_data_job_descriptions.json` (dạng mảng JSON trực tiếp).

```bash
# Tải dữ liệu từ HuggingFace
python dowload_data.py
# Làm sạch dữ liệu JSON
python clean_json_fields.py
```

---

#### `scrape_from_DT.py` — Dân Trí

- **Nguồn:** `https://dantri.com.vn/cong-nghe/`
- **Danh mục:** `ai-internet` (part 1) và `an-ninh-mang` (part 2)
- **Cơ chế:** Dùng `selenium.webdriver.Chrome`, phân trang theo pattern `.htm` → `-trang-{N}.htm`
- **Lọc:** Bỏ link quảng cáo (`eclick`), bỏ bài trùng
- **Nội dung:** Lấy thẻ `<p>` bên trong `#desktop-in-article`
- **Output:** `raw_data/raw_data_DT_part{1|2}.json`

```python
# Thay đổi chủ đề / số trang tại đầu file
part = 1   # hoặc 2
source_url = "https://dantri.com.vn/cong-nghe/ai-internet.htm"
num_pages = 10
```

---

#### `scrape_from_GenK.py` — GenK

- **Nguồn:** `https://genk.vn/`
- **Danh mục:** `ai.chn` (part 1), `internet.chn` (part 2), `do-choi-so.chn` (part 3), `tin-ict.chn` (part 4)
- **Cơ chế:** Cuộn trang xuống cho đến khi hiện nút **"Xem thêm"** (`a.btnviewmore`), sau đó scrape toàn bộ danh sách bài đã load
- **Nội dung:** Lấy `<p>` bên trong `#ContentDetail`
- **Output:** `raw_data/raw_data_GenK_part{1..4}.json`

```python
# Chọn part cần cào
part = 4
source_url = "https://genk.vn/tin-ict.chn"
```

---

#### `scrape_from_topCV.py` — TopCV

- **Nguồn:** `https://www.topcv.vn/tim-viec-lam-cong-nghe-thong-tin-cr257`
- **Cơ chế:** Dùng `undetected_chromedriver` (chống bot-detect), thêm `time.sleep(random)` giữa các request
- **Nội dung bài đăng:** Lấy các section trong `.job-description__item--content` (hỗ trợ cả `<ul><li>` và `<p>`)
- **Phân trang:** 6 trang, URL dạng `?page={N}`
- **Output:** `raw_data/raw_data_topCV.json`

```python
num_pages = 6          # Số trang muốn cào
driver = uc.Chrome(version_main=147)  # Chỉnh version khớp Chrome đang cài
```

---

#### `scrape_from_VN-EP.py` — VnExpress

- **Nguồn:** `https://vnexpress.net/khoa-hoc-cong-nghe/ai`
- **Cơ chế:** Phân trang theo pattern `{url}-p{N}`
- **Nội dung:** Lấy `<p class="Normal">` bên trong `#fck_detail_gallery`
- **Output:** `raw_data/raw_data_VN-EP.json`

```python
source_url = "https://vnexpress.net/khoa-hoc-cong-nghe/ai"
num_pages = 10
```

---

### Cấu trúc file JSON đầu ra (sau Scraping)

```json
{
  "source_platform": "Tên nguồn",
  "source_url": "https://...",
  "scraped_at": "YYYY-MM-DD HH:MM:SS",
  "post_detail": [
    {
      "title": "Tiêu đề bài viết",
      "content": "Nội dung đầy đủ..."
    }
  ]
}
```

---

### Bước 2 — Filtering (`filter_data.py`)

Dùng model **PhoBERT** đã fine-tune (lưu tại `phobert_title_classifier_best/`) để phân loại mỗi bài là **IT** hay **Non-IT** dựa trên tiêu đề.
*Hỗ trợ hai định dạng JSON: Dạng đối tượng có chứa mảng (legacy) và dạng mảng trực tiếp (từ HuggingFace).*

#### Pipeline tiền xử lý tiêu đề (giống lúc training)

```
Tiêu đề thô
  │
  ├─► NER (underthesea): PER/ORG → "name", LOC → "loc"
  ├─► Chuẩn hoá số: phần trăm → "percent", ngày → "date", số → "number"
  ├─► Xoá dấu câu
  ├─► word_tokenize (underthesea)
  └─► Lowercase + thu gọn khoảng trắng
```

#### Chạy

```bash
python filter_data.py
```

- **Input:** tự động scan `raw_data/raw_data_*.json`
- **Output:** `filtered_data/filtered_data_*.json` (mỗi bài thêm `"is_relevant": true/false`)
- **Hỗ trợ GPU:** Tự detect `cuda` nếu có

#### Cấu trúc file JSON đầu ra (sau Filtering)

```json
{
  "source_platform": "...",
  "source_url": "...",
  "scraped_at": "...",
  "post_detail": [
    {
      "title": "...",
      "content": "...",
      "is_relevant": true
    }
  ]
}
```

---

### Bước 3 — Entity Extraction (`extract_data.py`)

Trích xuất thực thể có tên từ các bài **is_relevant = true**, kết hợp:

| Thực thể | Phương pháp | Mô tả |
|----------|-------------|-------|
| `PER` | NER model (`NlpHust/ner-vietnamese-electra-base`) | Tên người |
| `ORG` | NER model | Tổ chức, công ty |
| `LOC` | NER model | Địa danh, khu vực |
| `DATE` | Regex rule-based | Ngày, tháng, năm, quý (tiếng Việt + Anh) |
| `TECH` | Dictionary + Regex | Công nghệ, ngôn ngữ lập trình, framework, tool |
| `JOB_ROLE` | Dictionary + Regex | Chức danh, vị trí nghề nghiệp |
| `SALARY` | Regex rule-based | Mức lương (VNĐ, USD, định tính) |

#### Xử lý văn bản dài

Văn bản vượt **512 tokens** được chia thành các chunk (480 token/chunk, overlap 50 token) để NER model xử lý đầy đủ mà không bỏ sót entity ở ranh giới chunk.

#### Chạy (mặc định — scan toàn bộ thư mục)

```bash
python extract_data.py
```

#### Chạy với file cụ thể

```bash
python extract_data.py filtered_data/filtered_data_topCV.json
```

#### Chạy với thư mục tuỳ chỉnh

```bash
python extract_data.py --dir path/to/filtered_data/
```

- **Input:** `filtered_data/filtered_data_*.json` (chỉ lấy bài `is_relevant=true`)
- **Output:** `extracted_data/extracted_data_phobert_*.json`
- **Hỗ trợ GPU:** Tự detect `cuda` nếu có

#### Cấu trúc file JSON đầu ra (sau Extraction)

```json
{
  "source_platform": "...",
  "source_url": "...",
  "scraped_at": "...",
  "post_detail": [
    {
      "title": "...",
      "content": "...",
      "is_relevant": true,
      "entities": {
        "PER":      ["Nguyễn Văn A", "..."],
        "ORG":      ["Google", "FPT Software", "..."],
        "LOC":      ["Hà Nội", "TP.HCM", "..."],
        "DATE":     ["tháng 4 năm 2025", "Q1/2025", "..."],
        "TECH":     ["Python", "Docker", "ChatGPT", "..."],
        "JOB_ROLE": ["Software Engineer", "Data Analyst", "..."],
        "SALARY":   ["15 - 25 triệu", "lương cạnh tranh", "..."]
      }
    }
  ]
}
```

---

### Bước 4 — Upload AWS S3 (`upload_to_s3.py`)

- **Mục đích:** Đồng bộ dữ liệu đã được trích xuất (`extracted_data/*.json`) lên bucket S3 `database-data-mining`.
- **Cơ chế:** Script sẽ tự động xóa các file cũ trong thư mục S3 (đảm bảo tính idempotency) trước khi upload toàn bộ file mới.
- **Yêu cầu:** Cần có file `.env` chứa `AWS_ACCESS_KEY_ID` và `AWS_SECRET_ACCESS_KEY`.

```bash
python upload_to_s3.py
```

---

## 🚀 Chạy toàn bộ pipeline

```bash
# Bước 1: Thu thập dữ liệu (chạy từng script theo nhu cầu)
python dowload_data.py
python clean_json_fields.py
python scrape_from_DT.py
python scrape_from_GenK.py
python scrape_from_topCV.py
python scrape_from_VN-EP.py

# Bước 2: Lọc IT/Non-IT
python filter_data.py

# Bước 3: Trích xuất thực thể
python extract_data.py

# Bước 4: Tải dữ liệu lên S3
python upload_to_s3.py
```

> **Tip:** Các bước 2 và 3 xử lý **tất cả** file trong thư mục tương ứng, nên có thể chạy từng script Scraping riêng rồi chạy filter/extract một lần sau cùng.

---

## 🗂️ Nguồn dữ liệu

| Script | Nguồn | Loại nội dung | File đầu ra |
|--------|-------|---------------|-------------|
| `dowload_data.py` | [HuggingFace](https://huggingface.co/datasets/tinixai/vietnamese-job-descriptions) | Dataset tin tuyển dụng IT tiếng Việt | `vietnamese_job_descriptions_1000.json` |
| `scrape_from_DT.py` | [Dân Trí](https://dantri.com.vn/cong-nghe/) | Bài viết công nghệ (AI, an ninh mạng) | `raw_data_DT_part1.json`, `raw_data_DT_part2.json` |
| `scrape_from_GenK.py` | [GenK](https://genk.vn/) | Bài viết IT, đồ chơi số, ICT | `raw_data_GenK_part1-4.json` |
| `scrape_from_topCV.py` | [TopCV](https://www.topcv.vn/) | Tin tuyển dụng IT | `raw_data_topCV.json` |
| `scrape_from_VN-EP.py` | [VnExpress](https://vnexpress.net/) | Bài viết AI, khoa học-công nghệ | `raw_data_VN-EP.json` |

---

## 🏷️ Nhãn thực thể (Entity Labels)

| Label | Mô tả | Ví dụ |
|-------|-------|-------|
| `PER` | Tên người | `Nguyễn Văn A`, `Elon Musk` |
| `ORG` | Tổ chức, công ty | `Google`, `FPT Software`, `Bộ TT&TT` |
| `LOC` | Địa danh | `Hà Nội`, `Silicon Valley` |
| `DATE` | Ngày, tháng, năm, quý | `tháng 4 năm 2025`, `Q1/2025`, `30/4` |
| `TECH` | Công nghệ, công cụ, framework | `Python`, `Docker`, `ChatGPT`, `AWS` |
| `JOB_ROLE` | Chức danh nghề nghiệp | `Software Engineer`, `CTO`, `Lập trình viên` |
| `SALARY` | Mức lương | `15 - 25 triệu`, `$2,000 USD`, `lương cạnh tranh` |

---

## 🔧 Troubleshooting

### Lỗi ChromeDriver / undetected-chromedriver

| Lỗi | Nguyên nhân | Cách xử lý |
|-----|-------------|------------|
| `SessionNotCreatedException` | Phiên bản ChromeDriver không khớp Chrome | Cập nhật Chrome lên đúng version hoặc chỉnh `version_main` trong `scrape_from_topCV.py` |
| `WebDriverException: Chrome not found` | Chrome chưa cài hoặc không tìm thấy path | Kiểm tra `google-chrome --version` / `chrome.exe` |
| `NoSuchElementException` khi scrape nội dung | Cấu trúc HTML nguồn thay đổi | Mở DevTools kiểm tra lại CSS selector |
| Bài bị bỏ qua nhiều (⚠) | Trang trả về nội dung rỗng / CAPTCHA | Tăng `time.sleep`, thử lại hoặc dùng VPN |

### Lỗi model PhoBERT / ELECTRA

| Lỗi | Nguyên nhân | Cách xử lý |
|-----|-------------|------------|
| `OSError: Model not found` | Thiếu thư mục `phobert_title_classifier_best/` | Đặt model đã fine-tune vào đúng thư mục |
| `CUDA out of memory` | VRAM không đủ | Dùng CPU (`CUDA_VISIBLE_DEVICES=""`) hoặc giảm batch |
| `UnicodeEncodeError` trên Windows terminal | Encoding mặc định không phải UTF-8 | Đã xử lý tự động trong cả 2 script; nếu vẫn lỗi, chạy `set PYTHONIOENCODING=utf-8` |

### Các trường hợp thường gặp khác

```bash
# Nếu underthesea chưa có dữ liệu NER, tải về trước
python -c "from underthesea import ner; ner('test')"

# Nếu model HuggingFace cần download (lần đầu chạy extract_data.py)
# Script tự download ~500 MB từ HuggingFace Hub, cần kết nối internet
```

---

## 📌 Ghi chú kỹ thuật

### Tại sao cần tiền xử lý tiêu đề trước khi lọc?

`filter_data.py` áp dụng đúng pipeline tiền xử lý đã dùng khi **training** model PhoBERT:
- Thay thế tên người/tổ chức bằng `name`, địa danh bằng `loc` → giảm OOV (out-of-vocabulary)
- Chuẩn hóa số, ngày tháng, phần trăm → tăng tính nhất quán
- `word_tokenize` (underthesea) → tách từ đúng cú pháp tiếng Việt

Nếu bỏ qua bước này, độ chính xác phân loại sẽ giảm đáng kể.

### Tại sao dùng sliding-window chunking trong `extract_data.py`?

Model ELECTRA có giới hạn **512 tokens**. Các bài viết dài (đặc biệt từ TopCV, Dân Trí) thường vượt ngưỡng này. Pipeline chia văn bản thành các **chunk 480 token** với **overlap 50 token** để:
- Không bỏ sót entity nằm ở ranh giới giữa hai chunk
- Dedup entity theo `(word.lower(), label)` để tránh trùng lặp

### Thứ tự ưu tiên rule-based (TECH & JOB_ROLE)

Các cụm từ dài được match trước cụm ngắn (`sorted by len, reverse=True`) để tránh match sai. Ví dụ: `"Software Engineer"` được nhận diện trước `"Engineer"`.

### Dedup thực thể

- **NER model** (PER/ORG/LOC): dedup theo `(word.lower(), label)` qua tất cả chunk
- **DATE / SALARY**: dedup theo **vị trí ký tự** (overlap check) để tránh trùng khi nhiều regex cùng bắt một span
- **TECH / JOB_ROLE**: dedup theo `keyword.lower()` (mỗi kỹ thuật/chức danh chỉ xuất hiện 1 lần/bài)

---

## 📊 Luồng dữ liệu đầy đủ

```
Dantri / GenK / VnExpress / TopCV / HuggingFace
          │
          │  selenium / undetected_chromedriver / datasets
          ▼
    raw_data/*.json
    { source_platform, source_url, scraped_at,
      post_detail: [ { title, content } ] }
          │
          │  PhoBERT fine-tune (phobert_title_classifier_best)
          │  + underthesea NER + regex normalize
          ▼
    filtered_data/*.json
    { ..., post_detail: [ { title, content, is_relevant } ] }
          │
          │  NlpHust/ner-vietnamese-electra-base  (PER/ORG/LOC)
          │  + Regex rule-based                   (DATE, SALARY)
          │  + Dictionary rule-based              (TECH, JOB_ROLE)
          ▼
    extracted_data/extracted_data_phobert_*.json
    { ..., post_detail: [ { title, content, is_relevant,
                             entities: { PER, ORG, LOC,
                                         DATE, TECH,
                                         JOB_ROLE, SALARY } } ] }
```
