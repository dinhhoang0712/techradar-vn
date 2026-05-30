# 🧪 TechPulse VN Test Suite
**Hệ thống Kiểm thử Toàn diện cho Nền tảng Phân tích Xu hướng Công nghệ**

> Giải pháp kiểm thử tự động, bao phủ toàn bộ luồng DataOps: từ khâu **Crawl dữ liệu (Scraping)**, xử lý ngôn ngữ tự nhiên **(NER)**, nạp vào **Graph Database**, cho đến việc kiểm định chất lượng trả lời của **Graph RAG Chatbot**.

---

## 📋 Mục lục

- [Tổng quan](#-tổng-quan)
- [Kiến trúc & Phân lớp Kiểm thử](#-kiến-trúc--phân-lớp-kiểm-thử)
- [Cấu trúc Thư mục](#-cấu-trúc-thư-mục)
- [Phân công Nhân sự](#-phân-công-nhân-sự)
- [Hướng dẫn Cài đặt & Chạy Test](#-hướng-dẫn-cài-đặt--chạy-test)
- [Tiêu chuẩn Chấp nhận (Acceptance Criteria)](#-tiêu-chuẩn-chấp-nhận-acceptance-criteria)

---

## 🎯 Tổng quan

**Vấn đề:** 
Hệ thống TechPulse VN phức tạp với kiến trúc Microservices (FastAPI, Golang, Next.js) kết hợp AI (Gemini 1.5, PhoBERT). Nếu không có cơ chế kiểm tra tự động, một thay đổi nhỏ ở luồng cào dữ liệu cũng có thể làm sập hệ thống RAG chatbot.

**Giải pháp:** 
Xây dựng một khung kiểm thử linh hoạt bằng `pytest`. Thay vì chỉ viết Unit Test, hệ thống tập trung mạnh vào **Integration Tests** (thử nghiệm luồng thực tế) và **Data Quality Tests** (đảm bảo AI không bịa đặt - Hallucination).

---

## 🏗️ Kiến trúc & Phân lớp Kiểm thử

Bộ Test Suite được chia thành 4 lớp bảo vệ cốt lõi:

1. **Unit Tests (Mức Code):** Kiểm thử logic đơn lẻ (VD: Hàm tính Sentiment, logic chia chunk văn bản, Prompt Builder). Không cần kết nối Database.
2. **Data Pipeline Tests (Mức Dữ liệu):** Đảm bảo Scraper lấy đúng HTML, Model NER PhoBERT trích xuất chính xác các thực thể (TECH, ORG, SALARY) từ câu tiếng Việt có nhiễu.
3. **Integration Tests (Mức Hệ thống):** Kiểm tra giao tiếp HTTP giữa API Gateway (Golang) và AI Core (Python). Đảm bảo phân quyền JWT, Rate Limiting, và logic thống kê (Radar/Compare) hoạt động ổn định với Neo4j & Postgres.
4. **Performance & Security (Mức Vận hành):** Thử nghiệm Pentest (Token Hijacking), Concurrent Streaming (SSE), đo lường RAG Latency.

---

## 📁 Cấu trúc Thư mục

```text
tests/
├── 📂 test_backend/                 # Kiểm thử Tích hợp Golang API & Python AI Core
│   ├── conftest.py                  # Cấu hình fixture và URL cho Backend
│   ├── test_admin_api.py            # Kiểm thử phân quyền & API quản trị viên
│   ├── test_analytics_api.py        # Logic tính toán biểu đồ Radar, Compare, Graph
│   ├── test_auth_api.py             # Đăng ký, Đăng nhập, JWT Authorization
│   ├── test_business_logic.py       # Kiểm thử logic nghiệp vụ độc lập
│   ├── test_chat_api.py             # Quản lý phiên hội thoại (Chat Sessions)
│   ├── test_deep_logic.py           # Kiểm thử độ sâu của các luồng xử lý
│   ├── test_edge_cases.py           # Xử lý ngoại lệ, payload dị dạng
│   ├── test_logic_units.py          # Kiểm thử các khối logic nhỏ lẻ
│   ├── test_maintenance_logic.py    # Kiểm thử chế độ bảo trì (Maintenance Mode)
│   ├── test_performance.py          # Giả lập truy cập đồng thời (Stress Test)
│   ├── test_rag_quality.py          # Đánh giá độ tin cậy và chính xác của AI
│   ├── test_security.py             # Kiểm thử an ninh cơ bản
│   ├── test_security_pentest.py     # Thử nghiệm xâm nhập (Rate limit, Token hijacking)
│   └── test_system_integrity.py     # Health checks, Luồng Streaming SSE, Chuẩn hóa địa điểm
│
├── 📂 test_rag/                     # Lõi AI (Graph RAG Engine)
│   ├── conftest.py                  # Cấu hình fixture cho RAG
│   ├── test_chat_service.py         # Dịch vụ quản lý lịch sử và phiên chat
│   ├── test_embedder.py             # Logic tạo vector embedding
│   ├── test_generator.py            # Logic LLM sinh câu trả lời
│   ├── test_pipeline.py             # Toàn bộ luồng: User -> Vector -> Graph -> Rerank -> LLM
│   ├── test_pipeline_stream.py      # Kiểm thử luồng trả về streaming
│   ├── test_prompt_builder.py       # Logic chèn context, xử lý khi Low Confidence
│   ├── test_reranker.py             # Mô hình tái xếp hạng độ ưu tiên kết quả
│   ├── test_retriever_graph.py      # Truy vấn Cypher để lấy Knowledge từ Neo4j
│   ├── test_retriever_user.py       # Truy xuất thông tin ngữ cảnh người dùng
│   ├── test_retriever_vector.py     # Tìm kiếm tương đồng từ Vector Database
│   └── test_routes.py               # Các điểm cuối API của RAG Core
│
├── 📂 test_clustering/              # Hệ thống Phân cụm Học máy (ML Clustering)
│   ├── conftest.py                  # Cấu hình fixture cho mô hình học máy
│   ├── test_clustering_api.py       # API truy xuất danh mục nhóm công nghệ
│   ├── test_feature_pipeline.py     # Tiền xử lý dữ liệu cho Model AI
│   ├── test_integration_tracking.py # Kiểm thử theo dõi quá trình huấn luyện
│   └── test_model_lifecycle.py      # Quy trình lưu trữ & tải mô hình (Model Registry)
│
├── 📂 test_data_pipeline/           # Thu thập & Trích xuất (Crawlers & NLP)
│   ├── conftest.py                  # Cấu hình môi trường cào dữ liệu
│   ├── test_dt_scrape.py            # Bóc tách HTML Dân Trí
│   ├── test_extract_data.py         # Test độ chính xác của NER Pipeline (PhoBERT)
│   ├── test_filter_data.py          # Lọc dữ liệu rác, không hợp lệ
│   ├── test_genk_scrape.py          # Bóc tách HTML GenK
│   ├── test_integration.py          # Kiểm thử tích hợp chuỗi thu thập
│   ├── test_topcv_scrape.py         # Bóc tách HTML TopCV
│   └── test_vn_express_scrape.py    # Bóc tách HTML VNExpress
│
├── 📂 test_database/                # Neo4j & Postgres (Data Integrity)
│   ├── __init__.py                  # Khởi tạo module
│   ├── conftest.py                  # Cấu hình kết nối DB ảo/thật
│   ├── test_connection.py           # Kiểm tra trạng thái ping Database
│   ├── test_create_relationships_script.py # Kịch bản tạo liên kết tự động
│   ├── test_data_transform.py       # Logic biến đổi Dữ liệu Thô -> Đồ thị (ETL)
│   ├── test_multi_source_import.py  # Nạp dữ liệu từ nhiều nguồn
│   ├── test_neo4j_live.py           # Thao tác trực tiếp trên DB live
│   ├── test_node_import.py          # Tạo node (Thực thể) mới
│   ├── test_relationships.py        # Xác thực kết nối giữa Job, Company, Skill
│   ├── test_run_complete_pipeline.py# Chạy toàn bộ luồng pipeline Database
│   ├── test_schema.py               # Kiểm tra cấu trúc bảng & Ràng buộc toàn vẹn
│   └── test_statistics.py           # Truy vấn số lượng thống kê Graph
│
├── 📂 scrape_labeling/              # Công cụ Đánh giá & Gán nhãn cho NER
│   ├── evaluate_phobert_title.py    # Đánh giá Model PhoBERT trên tiêu đề
│   ├── find_best_threshold_phobert_title.py # Tối ưu ngưỡng Confidence
│   ├── scrape_from_DT_labeling.py   # Lấy mẫu từ Dân Trí để gán nhãn
│   ├── scrape_from_GenK_labeling.py # Lấy mẫu từ GenK để gán nhãn
│   └── scrape_from_VN-EP_labeling.py# Lấy mẫu từ VNExpress để gán nhãn
│
├── __init__.py                      # Khởi tạo Python Module cho tests
├── conftest.py                      # Global Test Configuration (Fixtures, Mocks)
└── README.md                        # ← Bạn đang đọc file này
```

---

## 👥 Phân công Nhân sự

Việc bảo trì các file test được phân công cụ thể dựa trên Module:

| Vai trò | Chịu trách nhiệm chính cho thư mục | Nhiệm vụ |
|---------|------------------------------------|-----------|
| **QC / Automation Tester** | Toàn bộ `tests/` | Chạy CI/CD Pipeline, báo cáo Bugs, duy trì Test Fixtures. |
| **Data Engineer** | `test_data_pipeline/` | Cập nhật Scraper tests khi cấu trúc Web thay đổi, test luồng ETL. |
| **AI/ML Engineer** | `test_rag/`, `test_clustering/` | Đo lường độ trễ (Latency), chống Hallucination (Faithfulness check). |
| **Backend / DB DevOps** | `test_backend/`, `test_database/` | Test bảo mật (Pentest), Graph schema, Concurrent Requests. |

---

## 🚀 Hướng dẫn Cài đặt & Chạy Test

### 1. Yêu cầu Tiền đề
- Môi trường Python 3.12+ với đầy đủ thư viện (`pip install -r requirements.txt`).
- File `.env` chứa các biến môi trường (Postgres, Neo4j, Gemini API Key).
- **Lưu ý:** Nếu chạy Integration Tests (`test_backend`), các server **Golang API (Port 8080)** và **Python AI (Port 8000)** phải đang hoạt động. Luôn ưu tiên dùng IP `127.0.0.1` thay vì `localhost`.

### 2. Các Lệnh Chạy Thực Tế

*   **Chạy Toàn bộ Dự án (Full Test Suite):**
    ```bash
    python -m pytest tests/ -v
    ```

*   **Chạy Riêng lẻ Từng Nhóm Tính năng:**
    ```bash
    # Chỉ test Backend & API Tích hợp:
    python -m pytest tests/test_backend/
    
    # Chỉ test luồng Thu thập dữ liệu (Scraper/NER):
    python -m pytest tests/test_data_pipeline/

    # Chỉ test luồng lõi AI & Graph RAG:
    python -m pytest tests/test_rag/

    # Chỉ test cấu trúc Database & Graph Schema (Neo4j/Postgres):
    python -m pytest tests/test_database/

    # Chỉ test thuật toán phân cụm Machine Learning:
    python -m pytest tests/test_clustering/
    ```

*   **Xuất Báo Cáo JSON (Dành cho CI/CD):**
    ```bash
    # Xuất báo cáo cho toàn bộ hệ thống
    python -m pytest tests/ --json-report --json-report-file=artifacts/test_report_full.json
    
    # Xuất báo cáo riêng cho Backend
    python -m pytest tests/test_backend --json-report --json-report-file=artifacts/test_report_backend.json

    # Xuất báo cáo riêng cho RAG
    python -m pytest tests/test_rag --json-report --json-report-file=artifacts/test_report_rag.json
    
    # Xuất báo cáo riêng cho ML Clustering
    python -m pytest tests/test_clustering --json-report --json-report-file=artifacts/test_report_clustering.json
    ```

---

## 🛡️ Tiêu chuẩn Chấp nhận (Acceptance Criteria)

Hệ thống RAG và Web Crawling có những đặc thù riêng biệt, vì vậy bộ test áp dụng các quy chuẩn dung sai như sau:

1.  **Dung sai Mạng & AI (Tolerance):** 
    - Chấp nhận mã lỗi `500/502` trong các bài test RAG nếu lỗi đến từ nhà cung cấp Cloud (ví dụ: Gemini bị Rate Limit hoặc đứt kết nối tạm thời).
2.  **Độ trễ Dòng thời gian thực (Streaming Latency):**
    - Thời gian sinh ra Token đầu tiên (First Token) của AI phản hồi phải **nhỏ hơn 60s**. Vượt ngưỡng này, test sẽ bị đánh `FAIL`.
3.  **Không Chấp nhận Hallucination:**
    - Cấu hình `test_rag_quality.py` cấm tuyệt đối việc AI bịa đặt các công nghệ/khái niệm không có trong Graph Database. (Ví dụ: AI không được phép đưa ra lộ trình "Sửa ống nước bằng ReactJS").
4.  **Bảo vệ Dữ liệu:**
    - Không log JWT token thật, API Key ra màn hình Console dưới mọi hình thức trong suốt quá trình chạy Test.
