# API Documentation 

> Note: Do chưa có dữ liệu cụ thể nên đây chỉ nêu ra các endpoint lớn, các sub-endpoint sẽ được cập nhật sau khi có dữ liệu cụ thể hơn. 

## 1. Root /api/v1/

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| GET | `/radar` | Vào trang radar xu hướng công nghệ | No |
| GET | `/compare` | So sánh chi tiết xu hướng công nghệ | No |
| GET | `/graph` | Xem đồ thị xu hướng công nghệ | No |
| GET | `/chat` | Tương tác với chatbot tư vấn xu hướng công nghệ | Yes |

## 2. Radar /api/v1/radar

Phần API cho nội dung trang radar chia làm 3 layer:
- Layer 1: List 4 công nghệ có tăng trưởng nhanh nhất trong 3 tháng gần nhất
- Layer 2: Thanh tìm kiếm và biểu đồ xu hướng
- Layer 3: Top 10 công nghệ



#### Layer 1 

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| GET | `/radar/top4` | Lấy danh sách 4 công nghệ có tăng trưởng nhanh nhất trong 3 tháng gần nhất + các chỉ số | No |

`Response mẫu`:

```json
{
  "data": [
    {
      "technology": "React",
      "Sentiment": 1,
      "job_count": 1500,
      "YoY": 66,
      "growth_rate": 171
    },
    {
      "technology": "Node.js",
      "Sentiment": 1,
      "job_count": 1200,
      "YoY": 50,
      "growth_rate": 120
    },
    {
      "technology": "Python",
      "Sentiment": 1,
      "job_count": 2000,
      "YoY": 40,
      "growth_rate": 100
    },
    {
      "technology": "Docker",
      "Sentiment": 1,
      "job_count": 800,
      "YoY": 30,
      "growth_rate": 80
    }
  ]
}
```

#### Layer 2

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| GET | `/radar/search` | Truy vấn dữ liệu cho biểu đồ xu hướng theo form search đã nhập | No |


`Request mẫu`:

```json
{
  "technology": ["React", "Node.js", "Python"],
  "time_range": 6,  // 6 tháng gần nhất
  "plot_type": "line"  // loại biểu đồ: line, bar, growth%
}
```

`Response mẫu`:

```json
{
  "data": {
    "React": {
      "months": ["2023-01", "2023-02", "2023-03", "2023-04", "2023-05", "2023-06"],
      "job_counts": [1000, 1100, 1200, 1300, 1400, 1500]
    },
    "Node.js": {
      "months": ["2023-01", "2023-02", "2023-03", "2023-04", "2023-05", "2023-06"],
      "job_counts": [800, 900, 1000, 1100, 1150, 1200]
    },
    "Python": {
      "months": ["2023-01", "2023-02", "2023-03", "2023-04", "2023-05", "2023-06"],
      "job_counts": [1500, 1600, 1700, 1800, 1900, 2000]
    }
  }
}
```

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| GET | `/radar/export-png` | Xuất biểu đồ xu hướng thành file PNG | No |
| GET | `/radar/export-csv` | Xuất dữ liệu biểu đồ xu hướng thành file CSV | No |

#### Layer 3

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| GET | `/radar/top10` | Lấy danh sách top 10 công nghệ kèm số lượng jobs | No |

`Response mẫu`:

```json
{
  "data": [
    {"technology": "React", "job_count": 1500},
    {"technology": "Node.js", "job_count": 1200},
    {"technology": "Python", "job_count": 2000},
    {"technology": "Docker", "job_count": 800},
    {"technology": "AWS", "job_count": 900},
    {"technology": "Kubernetes", "job_count": 700},
    {"technology": "Java", "job_count": 1800},
    {"technology": "C#", "job_count": 1600},
    {"technology": "Go", "job_count": 600},
    {"technology": "Ruby", "job_count": 500}
  ]
}
```

## 3. Compare /api/v1/compare

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| GET | `/compare/search` | Truy vấn dữ liệu so sánh chi tiết xu hướng công nghệ theo form search đã nhập | No |

`Request mẫu`:

```json
{
  "technology": ["React", "Node.js"],
  "time_range": 6,  // 6 tháng gần nhất
  "show_peak": true  // có hiển thị điểm tăng trưởng cao nhất không
}
```


`Response mẫu`:

```json
{
  "data": {
    "React": {
      "growth_rate": 171,
      "YoY": 66,
      "MoM": 7.14,
      "jobs": 1500,
      "peak_month": "2023-06",
      "baohoa": "2023-07"
    },
    "Node.js": {
      "growth_rate": 120,
      "YoY": 50,
      "MoM": 5.26,
      "jobs": 1200,
      "peak_month": "2023-06",
      "baohoa": "2023-07"
    }
  }
}

```


| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| GET | `/compare/llm-summary` | Tóm tắt so sánh chi tiết xu hướng công nghệ bằng LLM | No |

`Response mẫu`:

```json
{
  "data": {
    "summary": "React có tốc độ tăng trưởng nhanh hơn Node.js trong 6 tháng gần nhất với YoY 66% so với 50%. React cũng có số lượng jobs cao hơn (1500 vs 1200). Điểm tăng trưởng cao nhất của React là vào tháng 06/2023, trong khi Node.js cũng đạt đỉnh vào cùng tháng. Cả hai công nghệ đều có xu hướng tăng trưởng tích cực, nhưng React đang dẫn đầu về mức độ phổ biến và tốc độ tăng trưởng."
  }
}
```

## 4. Graph /api/v1/graph

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| GET | `/graph/explore` | Truy vấn dữ liệu để khám phá đồ thị xu hướng công nghệ theo form search đã nhập | No |

`Request mẫu`:

```json

{
    "nodes": ["React", "Node.js"], // danh sách công nghệ muốn hiển thị trên đồ thị
    "depth": 1, // độ sâu của đồ thị: 1 hop, 2 hops
    "filter": true, // có hiển thị filter không
    "focus": false, // quay về trung tâm đồ thị
    "reset": false // reset đồ thị về trạng thái ban đầu
}
```

Nếu request có `filter: true` -> hiện thị tab để lọc sâu.

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| GET | `/graph/filter` | Truy vấn dữ liệu để lọc sâu trên đồ thị xu hướng công nghệ theo form search đã nhập | No |

Request mẫu:

```json
{
    "min_salary": 500, // mức lương tối thiểu
    "min_sentiment": 0.5, // mức độ tích cực tối thiểu
    "location": "Hà Nội", // vị trí địa lý
}
```

## 5. Authentication /api/v1/auth

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/register` | Đăng ký tài khoản | No |
| POST | `/login` | Đăng nhập | No |
| POST | `/refresh` | Refresh token | No |
| POST | `/logout` | Đăng xuất | Optional |
| GET | `/me` | Thông tin user hiện tại | Yes |

## 6. Chatbot /api/v1/chat

| Method | Endpoint | Description | Auth |
| --- | --- | --- | --- |
| POST | `/chat/session` | Tạo phiên chat trả về ID phiên | Yes |
| GET | `/chat/session/{session_id}/messages` | Lấy lịch sử trò chuyện của phiên hiện tại  (khi có reload/quay lại) | Yes |
| POST | `/chat/session/{session_id}/messages` | Gửi tin nhắn mới đến chatbot và nhận phản hồi (trả về SSE) | Yes |