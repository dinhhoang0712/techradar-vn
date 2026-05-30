# Tổng quan các thư mục dữ liệu (Data Folders Overview)

Hệ thống Data Pipeline luân chuyển dữ liệu qua 3 giai đoạn chính, tương ứng với 3 thư mục lưu trữ dưới đây. Mỗi thư mục đại diện cho một bước xử lý tinh luyện dữ liệu từ dạng thô sơ sang dạng có cấu trúc.

---

## 1. 📂 `raw_data` (Dữ liệu Thô)
Đây là nơi chứa các tập tin đầu vào của pipeline, được thu thập trực tiếp từ các nguồn ngoài. Dữ liệu chưa qua bất kỳ quá trình xử lý học máy nào.

- **Nguồn gốc:** 
  - Scrape từ các trang báo (Dân Trí, GenK, VnExpress).
  - Scrape từ trang tuyển dụng TopCV.
  - Tải về từ HuggingFace (dataset `tinixai/vietnamese-job-descriptions`).
- **Nội dung:** Chứa các thông tin cơ bản của bài viết hoặc tin tuyển dụng như `title` (tiêu đề), `content` (nội dung bài viết/mô tả công việc), `source_platform`, `source_url`, và thời gian thu thập.
- **Vai trò:** Làm dữ liệu gốc (Single Source of Truth) để các bước phía sau đọc và phân tích.
- **Các tệp (Files) tiêu biểu:**
  - `raw_data_DT_part1.json`, `raw_data_DT_part2.json`: Dữ liệu bài viết công nghệ từ báo Dân Trí.
  - `raw_data_GenK_part1.json` đến `part4.json`: Dữ liệu bài viết ICT, đồ chơi số từ GenK.
  - `raw_data_VN-EP.json`: Dữ liệu khoa học - công nghệ từ VnExpress.
  - `raw_data_topCV.json`: Tin tuyển dụng lấy từ trang TopCV.
  - `raw_data_job_descriptions.json`: Dữ liệu tin tuyển dụng IT (đã được làm sạch các trường thừa) tải từ HuggingFace.

---

## 2. 📂 `filtered_data` (Dữ liệu Đã Lọc)
Thư mục này chứa kết quả đầu ra của Bước Phân Loại (`filter_data.py`). Mục đích là nhằm loại bỏ "nhiễu" — những bài báo hoặc tin tức không liên quan đến lĩnh vực Công nghệ Thông tin (IT).

- **Cách thức tạo ra:** Dữ liệu từ `raw_data` được đưa qua mô hình học máy **PhoBERT** (đã fine-tune) để phân tích tiêu đề.
- **Sự thay đổi:** Cấu trúc file được giữ nguyên như `raw_data`, nhưng mỗi bài viết/tin tuyển dụng được gán thêm một trường mới:
  - `"is_relevant": true` (Nếu bài viết liên quan đến IT)
  - `"is_relevant": false` (Nếu bài viết ngoài ngành / Non-IT)
- **Vai trò:** Giảm tải khối lượng tính toán cho bước trích xuất thực thể phía sau bằng cách chỉ giữ lại dữ liệu thật sự có giá trị.
- **Các tệp (Files) tiêu biểu:** Tương ứng 1-1 với thư mục `raw_data` nhưng có tiền tố `filtered_data_`:
  - `filtered_data_DT_part1.json`
  - `filtered_data_topCV.json`
  - `filtered_data_job_descriptions.json`
  - ...

---

## 3. 📂 `extracted_data` (Dữ liệu Đã Trích Xuất Thực Thể)
Đây là thư mục chứa thành phẩm cuối cùng của pipeline dữ liệu, được tạo ra bởi script `extract_data.py`. Đây cũng là nguồn dữ liệu chuẩn hóa cuối cùng được sử dụng để tải lên hệ thống lưu trữ Cloud (AWS S3) và phục vụ mục đích trực quan hóa/phân tích.

- **Cách thức tạo ra:** Chỉ những bài viết có `"is_relevant": true` từ `filtered_data` mới được đem đi xử lý qua mô hình nhận diện thực thể (NER - **ELECTRA**) và các tập luật Regex / Dictionary.
- **Sự thay đổi:** Thêm một đối tượng `"entities"` vào mỗi bài đăng, bao gồm các thông tin quan trọng được bóc tách và phân loại thành 7 nhóm:
  - `PER` (Tên người)
  - `ORG` (Tên tổ chức, công ty)
  - `LOC` (Địa điểm)
  - `DATE` (Thời gian)
  - `TECH` (Công nghệ, framework, ngôn ngữ lập trình)
  - `JOB_ROLE` (Vị trí, chức danh nghề nghiệp)
  - `SALARY` (Mức lương)
- **Vai trò:** Biến đổi văn bản tự do (unstructured text) thành dạng dữ liệu có cấu trúc cao (highly structured data), sẵn sàng cho Data Warehouse hoặc các bài toán phân tích xu hướng thị trường lao động.
- **Các tệp (Files) tiêu biểu:** Kế thừa từ `filtered_data` nhưng có tiền tố `extracted_data_phobert_`. Trong các tệp này, những bài viết bị đánh dấu là Non-IT (`"is_relevant": false`) đã bị lược bỏ hoàn toàn.
  - `extracted_data_phobert_topCV.json`
  - `extracted_data_phobert_job_descriptions.json`
  - `extracted_data_phobert_VN-EP.json`
  - ...

---

### 🔄 Luồng Di Chuyển (Data Flow)
```text
(Internet / HuggingFace)
        │
        ▼
   [raw_data] ──(PhoBERT Classifier)──► [filtered_data] ──(ELECTRA NER + Rule-based)──► [extracted_data] ──(Boto3)──► [AWS S3]
```
