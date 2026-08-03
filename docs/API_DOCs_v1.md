# API Documentation — v1

Tài liệu này phản ánh **API thực tế** do Spring Boot gateway (`apps/backend`) cung cấp.

---

## Mục lục

1. [Quy ước chung](#quy-ước-chung)
2. [Auth — `/api/v1/auth`](#1-auth---apiv1auth)
3. [User — `/api/v1/user`](#2-user---apiv1user)
4. [Radar — `/api/v1/radar`](#3-radar---apiv1radar)
5. [Compare — `/api/v1/compare`](#4-compare---apiv1compare)
6. [Graph — `/api/v1/graph`](#5-graph---apiv1graph)
7. [Chat — `/api/v1/chat`](#6-chat---apiv1chat)
8. [Clustering — `/api/v1/clustering`](#7-clustering---apiv1clustering)
9. [Notifications — `/api/v1/notifications`](#8-notifications---apiv1notifications)
10. [Admin — `/api/v1/admin`](#9-admin---apiv1admin)
11. [Health & Status](#10-health--status)
12. [Salary — `/api/v1/salary`](#11-salary---apiv1salary)
13. [Company — `/api/v1/companies`](#12-company---apiv1companies)
14. [Job Matching — `/api/v1/jobs`](#13-job-matching---apiv1jobs)
15. [Messaging — `/api/v1/conversations`](#14-messaging---apiv1conversations)
16. [Social / Feed — `/api/v1/feed`, `/api/v1/posts`, `/api/v1/users`](#15-social--feed---apiv1feed-apiv1posts-apiv1users)
17. [AI Interview — `/api/v1/interview`](#16-ai-interview---apiv1interview)
18. [AiProxy — career/recommend/forecast/report/agent/summarize/company-insight](#17-aiproxy---forward-nguyên-văn-sang-ai-rag-core-module-aiproxy)
19. [Career Roadmap — `/api/v1/career/roadmap`, `/api/v1/career/simulate`](#18-career-roadmap---apiv1careerroadmap-apiv1careersimulate-feature-roadmap-native---không-proxy)
20. [Phân quyền](#phân-quyền)
21. [Proxy sang Python](#proxy-sang-python)
22. [Error Codes](#error-codes)

---

## Quy ước chung

### Base URL

```
Development: http://localhost:8080/api/v1
Production: https://api.techradar.vn/api/v1
```

### Serialization

- **Format**: JSON
- **Case convention**: `snake_case` cho tất cả field names (vd: `refresh_token`, `full_name`, `user_id`)
- **Null handling**: Trường `null` được lược bỏ khỏi response (Jackson `JsonInclude.Include.NON_NULL`)

### Response Envelope

Phần lớn response được bọc trong `ApiResponse`:

```json
{
  "success": true,
  "data": {},
  "message": "string",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Ngoại lệ (trả object thuần / bare):**
- `/auth/login`
- `/auth/register`
- `/auth/refresh`
- `/auth/me`
- `/status`

Client nên đọc theo dạng `res?.data ?? res` để xử lý đồng nhất.

### Authentication

Gửi header `Authorization: Bearer <access_token>` cho các endpoint yêu cầu authentication.

**Example:**
```http
GET /api/v1/user/profile
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Error Handling

Response lỗi luôn có format:

```json
{
  "success": false,
  "data": null,
  "message": "Error description",
  "error_code": "ERROR_CODE",
  "timestamp": 1719792000000
}
```

**HTTP Status Codes:**
- `200 OK` - Request thành công
- `400 Bad Request` - Request không hợp lệ (validation error, missing params)
- `401 Unauthorized` - Token không hợp lệ hoặc hết hạn
- `403 Forbidden` - Không có quyền truy cập
- `404 Not Found` - Resource không tồn tại
- `409 Conflict` - Resource đã tồn tại (vd: email đã đăng ký)
- `503 Service Unavailable` - Service phụ thuộc không khả dụng (AI services, Neo4j)

### Pagination

Các endpoint hỗ trợ pagination sử dụng query parameters:
- `page`: Số trang (mặc định: 0)
- `size`: Số item mỗi trang (mặc định: 20, tối đa: 100)

**Example:**
```http
GET /api/v1/admin/users?page=0&size=50
```

---

## 1. Auth — `/api/v1/auth` *(bare cho login/register/refresh/me)*

### POST `/auth/register`

Đăng ký tài khoản mới.

**Authentication:** Public

**Request Body:**
```json
{
  "full_name": "Nguyễn Văn A",
  "email": "user@example.com",
  "password": "SecurePass123!",
  "subscription_tier": "FREE"
}
```

**Fields:**
- `full_name` (string, required): Họ tên đầy đủ (2-100 ký tự)
- `email` (string, required): Email hợp lệ, unique
- `password` (string, required): Mật khẩu (tối thiểu 8 ký tự, phải chứa chữ hoa, chữ thường, số và ký tự đặc biệt)
- `subscription_tier` (string, optional): Gói đăng ký (`FREE`, `PRO`, `ENTERPRISE`), mặc định `FREE`

**Response (bare):**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "role": "USER",
  "expires_in": 900
}
```

**Error Responses:**
- `409 Conflict`: Email đã tồn tại
- `400 Bad Request`: Validation error (password không hợp lệ, email format sai)

**Example:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "full_name": "Nguyễn Văn A",
    "email": "user@example.com",
    "password": "SecurePass123!"
  }'
```

---

### POST `/auth/login`

Đăng nhập với email và password.

**Authentication:** Public

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

**Response (bare):**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "role": "USER",
  "expires_in": 900
}
```

**Error Responses:**
- `401 Unauthorized`: Email hoặc password sai
- `400 Bad Request`: Request body không hợp lệ

**Example:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "SecurePass123!"
  }'
```

---

### POST `/auth/refresh`

Làm mới access token sử dụng refresh token.

**Authentication:** Public

**Request Body:**
```json
{
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (bare):**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "role": "USER",
  "expires_in": 900
}
```

**Error Responses:**
- `401 Unauthorized`: Refresh token không hợp lệ hoặc hết hạn

**Note:** Refresh token mới sẽ được trả về mỗi lần refresh (token rotation).

---

### POST `/auth/logout`

Đăng xuất (stateless, client cần xóa token).

**Authentication:** JWT required

**Request Body:** None

**Response:**
```json
{
  "success": true,
  "data": null,
  "message": "Logged out successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Note:** Server không lưu trạng thái session. Client nên xóa cả access_token và refresh_token từ localStorage.

---

### GET `/auth/me`

Lấy thông tin user hiện tại.

**Authentication:** JWT required

**Response (bare):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "role": "USER",
  "status": "ACTIVE",
  "subscription_tier": "PRO"
}
```

**Fields:**
- `id`: UUID của user
- `email`: Email của user
- `role`: Role (`USER`, `ADMIN`)
- `status`: Status (`ACTIVE`, `INACTIVE`, `SUSPENDED`)
- `subscription_tier`: Gói đăng ký (`FREE`, `PRO`, `ENTERPRISE`)

**Example:**
```bash
curl -X GET http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### POST `/auth/forgot-password`

Yêu cầu reset password (gửi email reset link).

**Authentication:** Public

**Request Body:**
```json
{
  "email": "user@example.com"
}
```

**Response:**
```json
{
  "success": true,
  "data": null,
  "message": "If the email exists, a reset link has been sent",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Note:** Luôn trả về 200 ngay cả khi email không tồn tại (chống dò email). Email được gửi fire-and-forget.

---

### POST `/auth/reset-password`

Reset password với token từ email.

**Authentication:** Public

**Request Body:**
```json
{
  "token": "reset-token-from-email",
  "new_password": "NewSecurePass123!"
}
```

**Response:**
```json
{
  "success": true,
  "data": null,
  "message": "Password reset successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Error Responses:**
- `400 Bad Request`: Token không hợp lệ hoặc hết hạn
- `400 Bad Request`: Password không đáp ứng yêu cầu

## 2. User — `/api/v1/user`

### GET `/user/profile`

Lấy thông tin profile đầy đủ của user hiện tại.

**Authentication:** JWT required

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "full_name": "Nguyễn Văn A",
    "email": "user@example.com",
    "role": "USER",
    "status": "ACTIVE",
    "subscription_tier": "PRO",
    "avatar_url": "http://localhost:8080/api/v1/user/avatar/550e8400-e29b-41d4-a716-446655440000",
    "bio": "Full-stack developer interested in AI and ML",
    "job_role": "Senior Developer",
    "location": "Ho Chi Minh City",
    "technologies": ["React", "Node.js", "Python", "Docker"],
    "notify_inapp": true,
    "notify_email": false
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `id`: UUID của user
- `full_name`: Họ tên đầy đủ
- `email`: Email
- `role`: Role (`USER`, `ADMIN`)
- `status`: Status (`ACTIVE`, `INACTIVE`, `SUSPENDED`)
- `subscription_tier`: Gói đăng ký (`FREE`, `PRO`, `ENTERPRISE`)
- `avatar_url`: URL avatar (null nếu chưa có)
- `bio`: Bio ngắn (tùy chọn)
- `job_role`: Vị trí công việc hiện tại
- `location`: Địa điểm
- `technologies`: Danh sách công nghệ quan tâm
- `notify_inapp`: Bật thông báo in-app
- `notify_email`: Bật thông báo email

**Example:**
```bash
curl -X GET http://localhost:8080/api/v1/user/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### PUT `/user/profile`

Cập nhật thông tin profile.

**Authentication:** JWT required

**Request Body:** (tất cả fields optional)
```json
{
  "full_name": "Nguyễn Văn B",
  "bio": "Updated bio",
  "job_role": "Tech Lead",
  "location": "Hanoi",
  "technologies": ["React", "TypeScript", "Go", "Kubernetes"],
  "notify_inapp": true,
  "notify_email": true
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "full_name": "Nguyễn Văn B",
    "email": "user@example.com",
    "role": "USER",
    "status": "ACTIVE",
    "subscription_tier": "PRO",
    "avatar_url": "http://localhost:8080/api/v1/user/avatar/550e8400-e29b-41d4-a716-446655440000",
    "bio": "Updated bio",
    "job_role": "Tech Lead",
    "location": "Hanoi",
    "technologies": ["React", "TypeScript", "Go", "Kubernetes"],
    "notify_inapp": true,
    "notify_email": true
  },
  "message": "Profile updated successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Note:** Chỉ các field được gửi sẽ được cập nhật. Email không thể thay đổi.

---

### POST `/user/avatar`

Upload avatar cho user.

**Authentication:** JWT required

**Request Body:**
```json
{
  "content_type": "image/png",
  "data_base64": "iVBORw0KGgoAAAANSUhEUgAA..."
}
```

**Fields:**
- `content_type`: MIME type (`image/png`, `image/jpeg`, `image/jpg`, `image/webp`, `image/gif`)
- `data_base64`: Base64-encoded image data

**Constraints:**
- Kích thước tối đa: 3 MB
- Định dạng hỗ trợ: PNG, JPEG, JPG, WebP, GIF

**Response:**
```json
{
  "success": true,
  "data": {
    "avatar_url": "http://localhost:8080/api/v1/user/avatar/550e8400-e29b-41d4-a716-446655440000"
  },
  "message": "Avatar uploaded successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Error Responses:**
- `400 Bad Request`: File quá lớn hoặc định dạng không hỗ trợ
- `413 Payload Too Large`: File vượt giới hạn kích thước

---

### GET `/user/avatar/{userId}`

Lấy avatar của user (public endpoint).

**Authentication:** Public

**Response:** Binary image data với appropriate Content-Type header

**Headers:**
- `Content-Type`: image/png, image/jpeg, etc.
- `X-Content-Type-Options: nosniff`

**Error Responses:**
- `404 Not Found`: User không có avatar

**Example:**
```bash
curl -X GET http://localhost:8080/api/v1/user/avatar/550e8400-e29b-41d4-a716-446655440000 \
  --output avatar.png
```

---

### GET `/user/data-export`

Xuất toàn bộ dữ liệu cá nhân của user hiện tại (GDPR-style data export).

**Authentication:** Required (Bearer JWT)

### DELETE `/user/account`

Xoá tài khoản hiện tại (GDPR-style erasure).

**Authentication:** Required (Bearer JWT)

## 3. Radar — `/api/v1/radar` *(đọc từ `tech_analytics` trong Postgres)*

### GET `/radar/top4`

Lấy top 4 công nghệ có tăng trưởng cao nhất theo ngành.

**Authentication:** JWT required

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "industry": "Frontend",
      "growth_rate": 42.1,
      "job_count": 1240,
      "mom_rate": 18.2,
      "jobs_this_month": 890
    },
    {
      "industry": "Backend",
      "growth_rate": 35.8,
      "job_count": 980,
      "mom_rate": 12.5,
      "jobs_this_month": 720
    },
    {
      "industry": "DevOps",
      "growth_rate": 28.4,
      "job_count": 650,
      "mom_rate": 8.3,
      "jobs_this_month": 450
    },
    {
      "industry": "AI/ML",
      "growth_rate": 25.6,
      "job_count": 520,
      "mom_rate": 15.7,
      "jobs_this_month": 380
    }
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `industry`: Tên ngành/công nghệ
- `growth_rate`: Tỷ lệ tăng trưởng YoY (%)
- `job_count`: Tổng số việc làm hiện tại
- `mom_rate`: Tỷ lệ tăng trưởng Month-over-Month (%)
- `jobs_this_month`: Số việc làm trong tháng hiện tại

---

### GET `/radar/top10`

Lấy top 10 công nghệ theo số lượng việc làm.

**Authentication:** JWT required

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "keyword": "React",
      "job_count": 1240
    },
    {
      "keyword": "Node.js",
      "job_count": 980
    },
    {
      "keyword": "Python",
      "job_count": 890
    },
    {
      "keyword": "Java",
      "job_count": 850
    },
    {
      "keyword": "TypeScript",
      "job_count": 720
    },
    {
      "keyword": "Docker",
      "job_count": 650
    },
    {
      "keyword": "Kubernetes",
      "job_count": 520
    },
    {
      "keyword": "AWS",
      "job_count": 480
    },
    {
      "keyword": "Go",
      "job_count": 420
    },
    {
      "keyword": "PostgreSQL",
      "job_count": 380
    }
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `keyword`: Tên công nghệ
- `job_count`: Số việc làm hiện tại

---

### GET `/radar/search`

Tìm kiếm xu hướng công nghệ theo keywords.

**Authentication:** JWT required

**Query Parameters:**
- `keywords[]`: Danh sách keywords để tìm (bắt buộc)
- `months`: Số tháng để phân tích (mặc định: 6)

**Example:**
```http
GET /api/v1/radar/search?keywords=React&keywords=Vue&keywords=Angular&months=12
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "month": 1,
      "year": 2024,
      "keywords": {
        "React": 120,
        "Vue": 85,
        "Angular": 45
      }
    },
    {
      "month": 2,
      "year": 2024,
      "keywords": {
        "React": 135,
        "Vue": 92,
        "Angular": 48
      }
    }
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `month`: Tháng (1-12)
- `year`: Năm
- `keywords`: Object với key là keyword và value là số lượng

---

### GET `/radar/export-png`

Xuất radar chart dưới dạng PNG.

**Authentication:** JWT required

**Query Parameters:**
- `limit`: Số công nghệ hiển thị (mặc định: 20, tối đa: 50)

**Response:** Binary PNG data

**Headers:**
- `Content-Type`: image/png
- `Content-Disposition`: attachment; filename="radar.png"

**Example:**
```bash
curl -X GET "http://localhost:8080/api/v1/radar/export-png?limit=20" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  --output radar.png
```

---

### GET `/radar/export-csv`

Xuất radar data dưới dạng CSV.

**Authentication:** JWT required

**Query Parameters:**
- `limit`: Số công nghệ xuất (mặc định: 50, tối đa: 100)

**Response:** Binary CSV data

**Headers:**
- `Content-Type`: text/csv
- `Content-Disposition`: attachment; filename="radar.csv"

**CSV Format:**
```csv
keyword,job_count,growth_rate,mom_rate,article_count
React,1240,42.1,18.2,87
Node.js,980,35.8,12.5,65
...
```

**Example:**
```bash
curl -X GET "http://localhost:8080/api/v1/radar/export-csv?limit=50" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  --output radar.csv
```

---

### GET `/radar/stream`

SSE stream — đẩy snapshot top4/top10 mới ngay khi admin rebuild analytics xong (`live:radar`
Redis Pub/Sub, xem [`docs/DATABASE.md`](./DATABASE.md) §5), để `TrendDashboard` cập nhật real-time
thay vì phải tự F5.

**Authentication:** Public

## 4. Compare — `/api/v1/compare`

### GET `/compare/search`

So sánh xu hướng của nhiều công nghệ.

**Authentication:** JWT required

**Query Parameters:**
- `keywords[]`: Danh sách công nghệ để so sánh (bắt buộc)
- `months`: Số tháng để phân tích (mặc định: 12)

**Example:**
```http
GET /api/v1/compare/search?keywords=React&keywords=Vue&months=12
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "keyword": "React",
      "yoy_rate": 42.1,
      "mom_rate": 18.2,
      "growth_rate": 35.8,
      "monthly": [
        {
          "month": 1,
          "year": 2024,
          "count": 120
        },
        {
          "month": 2,
          "year": 2024,
          "count": 135
        }
      ]
    },
    {
      "keyword": "Vue",
      "yoy_rate": 28.4,
      "mom_rate": 12.5,
      "growth_rate": 22.3,
      "monthly": [
        {
          "month": 1,
          "year": 2024,
          "count": 85
        },
        {
          "month": 2,
          "year": 2024,
          "count": 92
        }
      ]
    }
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `keyword`: Tên công nghệ
- `yoy_rate`: Tỷ lệ tăng trưởng Year-over-Year (%)
- `mom_rate`: Tỷ lệ tăng trưởng Month-over-Month (%)
- `growth_rate`: Tỷ lệ tăng trưởng tổng thể (%)
- `monthly`: Mảng dữ liệu theo tháng

---

### POST `/compare/llm-summary`

Tạo tóm tắt so sánh bằng LLM (proxy đến ai-rag-core).

**Authentication:** JWT required

**Request Body:**
```json
{
  "technology1": "React",
  "technology2": "Vue",
  "growth_rate1": 42.1,
  "growth_rate2": 28.4,
  "job_count1": 1240,
  "job_count2": 650,
  "article_count1": 87,
  "article_count2": 45,
  "comparison_score": 0.78
}
```

**Fields:**
- `technology1`, `technology2`: Tên 2 công nghệ so sánh
- `growth_rate1`, `growth_rate2`: Tỷ lệ tăng trưởng
- `job_count1`, `job_count2`: Số việc làm
- `article_count1`, `article_count2`: Số bài viết
- `comparison_score`: Điểm tương đồng (0-1)

**Response:**
```json
{
  "success": true,
  "data": {
    "summary": "React hiện có mức độ phổ biến cao hơn Vue với 1240 việc làm so với 650 việc làm. Tỷ lệ tăng trưởng của React là 42.1% so với 28.4% của Vue. React có nhiều bài viết hơn (87 so với 45), cho thấy cộng đồng sôi động hơn. Tuy nhiên, Vue có điểm tương đồng 0.78 với React, cho thấy cả hai đều là frameworks frontend phổ biến với kiến trúc component-based."
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Error Responses:**
- `503 Service Unavailable`: ai-rag-core service không khả dụng
- `400 Bad Request`: Request body không hợp lệ

**Note:** Endpoint này proxy đến ai-rag-core service với timeout 120s.

## 5. Graph — `/api/v1/graph` *(Neo4j)*

### GET `/graph/explore`

Khám phá knowledge graph từ keywords.

**Authentication:** JWT required

**Query Parameters:**
- `keywords[]`: Danh sách keywords để tìm kiếm (bắt buộc)
- `depth`: Độ sâu traversal (mặc định: 2, tối đa: 4)
- `location`: Lọc theo địa điểm (tùy chọn)
- `min_salary`: Lọc theo mức lương tối thiểu (tùy chọn)

**Example:**
```http
GET /api/v1/graph/explore?keywords=React&keywords=Node.js&depth=2&location=Ho+Chi+Minh
```

**Response:**
```json
{
  "success": true,
  "data": {
    "nodes": [
      {
        "id": "tech-react",
        "label": "React",
        "type": "Technology",
        "properties": {
          "category": "Frontend",
          "growth_rate": 42.1,
          "job_count": 1240
        }
      },
      {
        "id": "tech-nodejs",
        "label": "Node.js",
        "type": "Technology",
        "properties": {
          "category": "Backend",
          "growth_rate": 35.8,
          "job_count": 980
        }
      },
      {
        "id": "comp-fpt",
        "label": "FPT Software",
        "type": "Company",
        "properties": {
          "location": "Ho Chi Minh City",
          "size": "Large"
        }
      }
    ],
    "edges": [
      {
        "source": "tech-react",
        "target": "comp-fpt",
        "label": "USES",
        "properties": {
          "strength": 0.85
        }
      },
      {
        "source": "tech-nodejs",
        "target": "comp-fpt",
        "label": "USES",
        "properties": {
          "strength": 0.72
        }
      }
    ],
    "found": true
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `nodes`: Mảng các node trong graph
  - `id`: ID unique của node
  - `label`: Tên hiển thị
  - `type`: Loại node (`Technology`, `Company`, `Job`, `Skill`, `Article`, `Person`)
  - `properties`: Object chứa các properties của node
- `edges`: Mảng các relationships giữa nodes
  - `source`: ID node nguồn
  - `target`: ID node đích
  - `label`: Loại relationship (`USES`, `REQUIRES`, `MENTIONS`, `RELATED_TO`, etc.)
  - `properties`: Object chứa các properties của relationship
- `found`: Boolean indicating if any nodes were found

---

### GET `/graph/road_analysis`

Phân tích đường đi ngắn nhất giữa 2 công nghệ.

**Authentication:** JWT required

**Query Parameters:**
- `from`: Công nghệ bắt đầu (bắt buộc)
- `to`: Công nghệ đích (bắt buộc)

**Example:**
```http
GET /api/v1/graph/road_analysis?from=React&to=Go
```

**Response:**
```json
{
  "success": true,
  "data": {
    "nodes": [
      {
        "id": "tech-react",
        "label": "React",
        "type": "Technology"
      },
      {
        "id": "tech-javascript",
        "label": "JavaScript",
        "type": "Technology"
      },
      {
        "id": "tech-go",
        "label": "Go",
        "type": "Technology"
      }
    ],
    "edges": [
      {
        "source": "tech-react",
        "target": "tech-javascript",
        "label": "RELATED_TO"
      },
      {
        "source": "tech-javascript",
        "target": "tech-go",
        "label": "RELATED_TO"
      }
    ],
    "found": true
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Note:** Sử dụng thuật toán shortest path của Neo4j để tìm đường đi ngắn nhất.

---

### POST `/graph/filter`

Lọc graph nodes theo nhiều tiêu chí.

**Authentication:** JWT required

**Request Body:**
```json
{
  "locations": ["Ho Chi Minh City", "Hanoi"],
  "node_types": ["Technology", "Company"],
  "min_salary": 15,
  "max_salary": 30,
  "sentiment": "positive"
}
```

**Fields:**
- `locations`: Danh sách địa điểm để lọc (tùy chọn)
- `node_types`: Danh sách loại node để lọc (tùy chọn)
- `min_salary`/`max_salary`: đơn vị **triệu VND** — chỉ áp dụng cho node `Job` (property `salary`
  là free-text tiếng Việt, vd. `"15-25 triệu"`); parse + so khớp khoảng được thực hiện **phía
  Java** sau khi Cypher trả về (không parse được free-text trong Cypher), text không parse được
  (vd. `"Thoả thuận"`) bị coi là KHÔNG khớp nếu có truyền khoảng lọc (tùy chọn)
- `sentiment`: Sentiment filter cho articles (`positive`, `negative`, `neutral`) — áp dụng
  `sentiment_score BETWEEN`, với dead-zone `neutral` là `[-0.2, 0.2]` (tùy chọn)

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "tech-react",
      "label": "React",
      "type": "Technology",
      "properties": {
        "category": "Frontend",
        "growth_rate": 42.1
      }
    },
    {
      "id": "comp-fpt",
      "label": "FPT Software",
      "type": "Company",
      "properties": {
        "location": "Ho Chi Minh City"
      }
    }
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

## 6. Chat — `/api/v1/chat` *(proxy ai-rag-core; session ở Postgres)*

### GET `/chat`

Health check cho RAG service.

**Authentication:** JWT required

**Response:**
```json
{
  "success": true,
  "data": {
    "status": "healthy",
    "neo4j": "connected",
    "version": "1.0.0"
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `status`: Status của RAG service (`healthy`, `degraded`, `unhealthy`)
- `neo4j`: Status kết nối Neo4j (`connected`, `disconnected`)
- `version`: Version của ai-rag-core service

---

### POST `/chat/session`

Tạo chat session mới.

**Authentication:** JWT required

**Response:**
```json
{
  "success": true,
  "data": {
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "created_at": "2024-07-01T10:00:00Z"
  },
  "message": "Session created successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `session_id`: UUID của session mới
- `created_at`: Timestamp khi session được tạo

---

### GET `/chat/sessions`

Lấy danh sách chat sessions của user.

**Authentication:** JWT required

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "session_id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "React vs Vue comparison",
      "created_at": "2024-07-01T10:00:00Z"
    },
    {
      "session_id": "660e8400-e29b-41d4-a716-446655440000",
      "title": "Career path advice",
      "created_at": "2024-06-28T15:30:00Z"
    }
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `session_id`: UUID của session
- `title`: Tên session (tự động tạo từ tin nhắn đầu tiên)
- `created_at`: Timestamp khi session được tạo

---

### DELETE `/chat/session/{sessionId}`

Xóa chat session (kiểm tra ownership).

**Authentication:** JWT required

**Response:**
```json
{
  "success": true,
  "data": null,
  "message": "Session deleted successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Error Responses:**
- `403 Forbidden`: Session không thuộc về user
- `404 Not Found`: Session không tồn tại

---

### GET `/chat/session/{sessionId}/messages`

Lấy lịch sử tin nhắn của session.

**Authentication:** JWT required

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "770e8400-e29b-41d4-a716-446655440000",
      "role": "user",
      "content": "What is the difference between React and Vue?",
      "created_at": "2024-07-01T10:00:00Z"
    },
    {
      "id": "880e8400-e29b-41d4-a716-446655440000",
      "role": "assistant",
      "content": "React and Vue are both popular JavaScript frameworks for building user interfaces...",
      "created_at": "2024-07-01T10:00:05Z"
    }
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `id`: UUID của message
- `role`: Role (`user`, `assistant`)
- `content`: Nội dung tin nhắn
- `created_at`: Timestamp khi tin nhắn được tạo

---

### POST `/chat/session/{sessionId}/messages`

Gửi tin nhắn mới và nhận câu trả lời (non-streaming).

**Authentication:** JWT required

**Request Body:**
```json
{
  "query": "What are the job prospects for React developers in Vietnam?"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "answer": "Based on current data, React developers in Vietnam have excellent job prospects...",
    "session_id": "550e8400-e29b-41d4-a716-446655440000",
    "sources": [
      {
        "title": "Vietnam IT Salary Report 2024",
        "url": "https://example.com/article1",
        "relevance": 0.92
      },
      {
        "title": "Top Tech Skills in Demand",
        "url": "https://example.com/article2",
        "relevance": 0.87
      }
    ],
    "entities": ["React", "Vietnam", "Frontend", "JavaScript"],
    "job_titles": ["Frontend Developer", "Full Stack Developer", "React Developer"],
    "query": "What are the job prospects for React developers in Vietnam?"
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `answer`: Câu trả lời từ LLM
- `session_id`: UUID của session
- `sources`: Mảng các nguồn tham khảo
  - `title`: Tiêu đề nguồn
  - `url`: URL nguồn
  - `relevance`: Độ liên quan (0-1)
- `entities`: Mảng các entities được trích xuất
- `job_titles`: Mảng các job titles liên quan
- `query`: Query gốc

**Error Responses:**
- `503 Service Unavailable`: ai-rag-core service không khả dụng

---

### POST `/chat/session/{sessionId}/messages/stream`

Gửi tin nhắn và nhận câu trả lời streaming (SSE).

**Authentication:** JWT required

**Request Body:**
```json
{
  "query": "What are the job prospects for React developers in Vietnam?"
}
```

**Response:** Server-Sent Events (SSE) stream

**Content-Type:** `text/event-stream`

**Event Format:**
```
data: {"token": "Based"}
data: {"token": " on"}
data: {"token": " current"}
data: {"token": " data"}
data: {"done": true, "sources": [...], "entities": [...]}
```

**Client Implementation (JavaScript):**
```javascript
const response = await fetch('/api/v1/chat/session/{sessionId}/messages/stream', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`
  },
  body: JSON.stringify({ query: 'your query' })
});

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  
  const chunk = decoder.decode(value);
  const lines = chunk.split('\n');
  
  for (const line of lines) {
    if (line.startsWith('data: ')) {
      const data = JSON.parse(line.slice(6));
      if (data.token) {
        // Append token to UI
      }
      if (data.done) {
        // Stream complete, show sources
      }
    }
  }
}
```

**Note:** Không thể dùng `EventSource` thuần vì cần gửi header `Authorization`. Phải dùng `fetch` với streaming response.

## 7. Clustering — `/api/v1/clustering` *(proxy ml-clustering, 503 nếu service lỗi)*

### GET `/clustering/clusters`

Lấy danh sách các technology clusters.

**Authentication:** JWT required

**Query Parameters:**
- `is_coherent`: Boolean, nếu true chỉ trả về coherent clusters (mặc định: false)

**Example:**
```http
GET /api/v1/clustering/clusters?is_coherent=true
```

**Response:** (verbatim từ ml-clustering service)
```json
{
  "success": true,
  "data": [
    {
      "cluster_id": 0,
      "label": "Frontend Frameworks",
      "size": 15,
      "technologies": ["React", "Vue", "Angular", "Svelte", "Solid"],
      "description": "Modern JavaScript frameworks for building user interfaces"
    },
    {
      "cluster_id": 1,
      "label": "Backend Frameworks",
      "size": 12,
      "technologies": ["Spring Boot", "Express", "Django", "Flask", "FastAPI"],
      "description": "Server-side frameworks for building APIs"
    }
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Error Responses:**
- `503 Service Unavailable`: ml-clustering service không khả dụng

**Note:** Response được trả **verbatim** từ Python service (gateway không reshape).

---

### GET `/clustering/clusters/{clusterId}`

Lấy thông tin chi tiết của một cluster.

**Authentication:** JWT required

**Path Parameters:**
- `clusterId`: ID của cluster

**Example:**
```http
GET /api/v1/clustering/clusters/0
```

**Response:**
```json
{
  "success": true,
  "data": {
    "cluster_id": 0,
    "label": "Frontend Frameworks",
    "size": 15,
    "technologies": ["React", "Vue", "Angular", "Svelte", "Solid"],
    "description": "Modern JavaScript frameworks for building user interfaces",
    "avg_growth_rate": 38.5,
    "avg_job_count": 920,
    "keywords": ["JavaScript", "UI", "Component", "SPA"]
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

### GET `/clustering/tech/{techName}/cluster`

Tìm cluster của một công nghệ cụ thể.

**Authentication:** JWT required

**Path Parameters:**
- `techName`: Tên công nghệ (URL-encoded)

**Example:**
```http
GET /api/v1/clustering/tech/React/cluster
```

**Response:**
```json
{
  "success": true,
  "data": {
    "cluster_id": 0,
    "label": "Frontend Frameworks",
    "technology": "React",
    "confidence": 0.92,
    "similar_technologies": ["Vue", "Angular", "Svelte"]
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `cluster_id`: ID của cluster
- `label`: Tên cluster
- `technology`: Tên công nghệ được query
- `confidence`: Độ tin cậy của dự đoán (0-1)
- `similar_technologies`: Các công nghệ tương tự trong cùng cluster

---

### POST `/clustering/predict/batch`

Dự đoán cluster cho nhiều công nghệ cùng lúc.

**Authentication:** JWT required

**Request Body:**
```json
{
  "tech_names": ["React", "Vue", "Go", "Rust", "Python"]
}
```

**Fields:**
- `tech_names`: Danh sách tên công nghệ (bắt buộc, không rỗng)

**Response:**
```json
{
  "success": true,
  "data": {
    "predictions": [
      {
        "technology": "React",
        "cluster_id": 0,
        "label": "Frontend Frameworks",
        "confidence": 0.92
      },
      {
        "technology": "Vue",
        "cluster_id": 0,
        "label": "Frontend Frameworks",
        "confidence": 0.89
      },
      {
        "technology": "Go",
        "cluster_id": 2,
        "label": "Backend Languages",
        "confidence": 0.95
      },
      {
        "technology": "Rust",
        "cluster_id": 2,
        "label": "Backend Languages",
        "confidence": 0.91
      },
      {
        "technology": "Python",
        "cluster_id": 3,
        "label": "General Purpose",
        "confidence": 0.88
      }
    ]
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Error Responses:**
- `400 Bad Request`: `tech_names` rỗng hoặc không hợp lệ
- `503 Service Unavailable`: ml-clustering service không khả dụng

## 8. Notifications — `/api/v1/notifications` *(in-app, JWT; scope theo user)*

### GET `/notifications`

Lấy danh sách notifications của user.

**Authentication:** JWT required

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "990e8400-e29b-41d4-a716-446655440000",
      "type": "trend_alert",
      "title": "React growth alert",
      "body": "React has grown by 25% this month, exceeding the alert threshold.",
      "link": "/radar?tech=React",
      "read": false,
      "created_at": "2024-07-01T10:00:00Z"
    },
    {
      "id": "991e8400-e29b-41d4-a716-446655440000",
      "type": "system",
      "title": "Welcome to TechRadar",
      "body": "Thank you for signing up! Start exploring technology trends.",
      "link": "/radar",
      "read": true,
      "created_at": "2024-06-28T09:00:00Z"
    }
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `id`: UUID của notification
- `type`: Loại notification — giá trị thực tế đang dùng (UPPER_SNAKE_CASE, không phải lowercase): `TREND_ALERT`, `JOB_MATCH`, `POST_LIKE`, `POST_COMMENT`, `NEW_FOLLOWER`, `NEW_MESSAGE` (xem [`docs/BACKEND_GUIDE.md`](./BACKEND_GUIDE.md) §4.10 cho nơi mỗi loại được tạo)
- `title`: Tiêu đề ngắn
- `body`: Nội dung chi tiết
- `link`: Link để điều hướng khi click
- `read`: Boolean indicating đã đọc hay chưa
- `created_at`: Timestamp khi notification được tạo

**Note:** Chỉ trả về 50 notifications mới nhất.

---

### GET `/notifications/unread-count`

Lấy số lượng notifications chưa đọc.

**Authentication:** JWT required

**Response:**
```json
{
  "success": true,
  "data": 5,
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

### POST `/notifications/{id}/read`

Đánh dấu một notification là đã đọc.

**Authentication:** JWT required

**Response:**
```json
{
  "success": true,
  "data": null,
  "message": "Notification marked as read",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Error Responses:**
- `403 Forbidden`: Notification không thuộc về user
- `404 Not Found`: Notification không tồn tại

---

### POST `/notifications/read-all`

Đánh dấu tất cả notifications là đã đọc.

**Authentication:** JWT required

**Response:**
```json
{
  "success": true,
  "data": null,
  "message": "All notifications marked as read",
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

### GET `/notifications/stream`

Stream notifications realtime (SSE).

**Authentication:** JWT required

**Response:** Server-Sent Events (SSE) stream

**Content-Type:** `text/event-stream`

**Event Format:**
```
event: notification
data: {"id": "...", "type": "trend_alert", "title": "...", "body": "...", "link": "...", "created_at": "..."}

event: heartbeat
data: {"timestamp": 1719792000000}
```

**Client Implementation (JavaScript):**
```javascript
const response = await fetch('/api/v1/notifications/stream', {
  method: 'GET',
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  
  const chunk = decoder.decode(value);
  const lines = chunk.split('\n');
  
  for (const line of lines) {
    if (line.startsWith('event: ')) {
      const eventType = line.slice(7);
    }
    if (line.startsWith('data: ')) {
      const data = JSON.parse(line.slice(6));
      if (eventType === 'notification') {
        // Show notification to user
      }
    }
  }
}
```

**Note:** 
- Heartbeat được gửi mỗi 25s để giữ connection alive
- Không thể dùng `EventSource` thuần vì cần gửi header `Authorization`. Phải dùng `fetch` với streaming response.

**Notification Sources** (`type` values thực tế, xem [`docs/BACKEND_GUIDE.md`](./BACKEND_GUIDE.md) §4.10):
- **`TREND_ALERT`**: ETL radar phát event `trend.alerts` lên Kafka khi một công nghệ tăng ≥ threshold (mặc định 20%) MoM. `TrendAlertDispatcher` fan-out tới user có công nghệ đó trong `user_profile.technologies` (kênh in-app + email theo `notify_inapp`/`notify_email`).
- **`JOB_MATCH`** *(NEW)*: `KafkaNeo4jWriterService` phát event `job.match.alerts` lên Kafka khi có 1 job posting HOÀN TOÀN MỚI (bỏ qua nếu chỉ là MERGE-update job đã biết). `JobMatchDispatcher` fan-out tới user có kỹ năng trùng công nghệ job yêu cầu (cùng cơ chế `notify_inapp`/`notify_email` như trend alert).
- **`POST_LIKE`**, **`POST_COMMENT`**, **`NEW_FOLLOWER`**, **`NEW_MESSAGE`** *(NEW, không qua Kafka)*: tạo trực tiếp (đồng bộ, best-effort) từ `ToggleLikeUseCase`/`AddCommentUseCase`/`ToggleFollowUseCase`/`SendMessageUseCase` khi có tương tác — **luôn ghi in-app bất kể `notify_inapp`/`notify_email`**, và KHÔNG gửi email (2 flag đó chỉ áp dụng cho `TREND_ALERT`/`JOB_MATCH`).

## 9. Admin — `/api/v1/admin` *(yêu cầu role ADMIN)*

### Users Management

#### GET `/admin/users`

Lấy danh sách tất cả users (có pagination).

**Authentication:** Admin role required

**Query Parameters:**
- `page`: Số trang (mặc định: 0)
- `size`: Số item mỗi trang (mặc định: 20, tối đa: 100)

**Response:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "email": "user@example.com",
        "full_name": "Nguyễn Văn A",
        "role": "USER",
        "status": "ACTIVE",
        "subscription_tier": "PRO",
        "created_at": "2024-06-01T10:00:00Z"
      }
    ],
    "total_elements": 150,
    "total_pages": 8,
    "current_page": 0,
    "size": 20
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### POST `/admin/users`

Tạo user mới.

**Authentication:** Admin role required

**Request Body:**
```json
{
  "email": "newuser@example.com",
  "password": "SecurePass123!",
  "full_name": "Nguyễn Văn B",
  "role": "USER",
  "status": "ACTIVE",
  "subscription_tier": "FREE"
}
```

**Fields:**
- `email` (required): Email
- `password` (required): Password
- `full_name` (optional): Họ tên đầy đủ
- `role` (optional): Role (`USER`, `ADMIN`), mặc định `USER`
- `status` (optional): Status (`ACTIVE`, `INACTIVE`, `SUSPENDED`), mặc định `ACTIVE`
- `subscription_tier` (optional): Gói đăng ký (`FREE`, `PRO`, `ENTERPRISE`), mặc định `FREE`

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "660e8400-e29b-41d4-a716-446655440000",
    "email": "newuser@example.com",
    "full_name": "Nguyễn Văn B",
    "role": "USER",
    "status": "ACTIVE",
    "subscription_tier": "FREE",
    "created_at": "2024-07-01T10:00:00Z"
  },
  "message": "User created successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### PUT `/admin/users/{id}`

Cập nhật thông tin user.

**Authentication:** Admin role required

**Request Body:** (tất cả fields optional)
```json
{
  "full_name": "Nguyễn Văn C",
  "role": "ADMIN",
  "status": "ACTIVE",
  "subscription_tier": "PRO"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "full_name": "Nguyễn Văn C",
    "role": "ADMIN",
    "status": "ACTIVE",
    "subscription_tier": "PRO",
    "created_at": "2024-06-01T10:00:00Z"
  },
  "message": "User updated successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### DELETE `/admin/users/{id}`

Xóa user.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": null,
  "message": "User deleted successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

### Settings Management

#### GET `/admin/settings`

Lấy tất cả settings.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "key": "maintenance_web",
      "value": "false",
      "description": "Enable maintenance mode for web"
    },
    {
      "key": "notifications.trend_threshold",
      "value": "20",
      "description": "Trend alert threshold percentage"
    }
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### GET `/admin/settings/{key}`

Lấy một setting cụ thể.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": {
    "key": "maintenance_web",
    "value": "false",
    "description": "Enable maintenance mode for web"
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### PUT `/admin/settings/{key}`

Cập nhật hoặc tạo setting.

**Authentication:** Admin role required

**Request Body:**
```json
{
  "value": "true",
  "description": "Enable maintenance mode for web"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "key": "maintenance_web",
    "value": "true",
    "description": "Enable maintenance mode for web"
  },
  "message": "Setting updated successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### DELETE `/admin/settings/{key}`

Xóa setting.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": null,
  "message": "Setting deleted successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

### Dashboard Analytics

#### GET `/admin/dashboard/user-count`

Lấy tổng số user.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": 150,
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### GET `/admin/dashboard/visits-today`

Lấy số lượt truy cập hôm nay.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": 1250,
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### GET `/admin/dashboard/searches-today`

Lấy số lượt tìm kiếm hôm nay.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": 450,
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### GET `/admin/dashboard/monthly-visits`

Lấy thống kê truy cập 12 tháng gần nhất.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "month": 1,
      "year": 2024,
      "visit_count": 15000
    },
    {
      "month": 2,
      "year": 2024,
      "visit_count": 16500
    }
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### GET `/admin/dashboard/top-keywords`

Lấy top 10 keywords được tìm kiếm nhiều nhất.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": [
    "React",
    "Node.js",
    "Python",
    "Java",
    "TypeScript",
    "Docker",
    "Kubernetes",
    "AWS",
    "Go",
    "PostgreSQL"
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### GET `/admin/dashboard/social` *(NEW)*

Thống kê tương tác trên social feed.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": {
    "total_posts": 340,
    "posts_today": 12,
    "total_comments": 890,
    "total_likes": 2100,
    "total_follows": 560,
    "top_posters": [
      { "user_id": "u1...", "full_name": "Nguyễn Văn A", "post_count": 24 }
    ],
    "pending_reports": 3
  }
}
```

**Fields:**
- `pending_reports`: số report `content_report` đang ở trạng thái `PENDING` — cùng số hiển thị badge ở AdminSidebar mục "Báo cáo vi phạm".

---

#### GET `/admin/dashboard/jobs` *(NEW)*

Thống kê thị trường việc làm/công nghệ.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": {
    "total_jobs_indexed": 4200,
    "top_technologies": [
      { "name": "React", "job_count": 320 }
    ],
    "job_match_alerts_sent": 87
  }
}
```

**Fields:**
- `job_match_alerts_sent`: tổng số notification loại `JOB_MATCH` đã từng tạo (không phải theo ngày — luỹ kế từ đầu).

---

#### GET `/admin/dashboard/pipeline` *(NEW)*

Sức khoẻ pipeline Kafka → Neo4j (`KafkaNeo4jWriterService`).

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": {
    "articles_processed": 1520,
    "articles_failed": 3,
    "jobs_processed": 980,
    "jobs_failed": 1,
    "last_article_processed_at": "2026-07-15T10:00:00Z",
    "last_job_processed_at": "2026-07-15T09:58:00Z",
    "last_failure_at": "2026-07-14T22:10:00Z",
    "last_failure_message": "Neo4j session timeout"
  }
}
```

**Lưu ý:** các số liệu này là counter **trong bộ nhớ** của instance đang trả lời request (không
lưu Postgres/Redis) — reset về 0 mỗi khi backend restart, và trong triển khai nhiều instance mỗi
instance có số riêng (không cộng gộp).

---

#### GET `/admin/dashboard/messaging` *(NEW)*

Thống kê khối lượng nhắn tin/notification.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": {
    "total_conversations": 210,
    "total_messages": 5400,
    "messages_today": 130,
    "notifications_by_type": [
      { "type": "NEW_MESSAGE", "count": 5400 },
      { "type": "POST_LIKE", "count": 2100 },
      { "type": "TREND_ALERT", "count": 45 }
    ]
  }
}
```

---

### Social Moderation *(NEW)*

Admin xem/xoá bất kỳ post/comment nào (bỏ qua kiểm tra quyền sở hữu mà user thường bị áp), và
duyệt hàng đợi report.

#### GET `/admin/posts`

Danh sách toàn bộ post để kiểm duyệt.

**Authentication:** Admin role required

**Query params:** `page` (default `0`), `size` (default `20`)

**Response (wrapped):** mảng `{ id, author_id, author_name, author_avatar_url, content, created_at, like_count, comment_count }`

---

#### DELETE `/admin/posts/{id}`

Xoá bất kỳ post nào (khác `DELETE /posts/{id}` — endpoint đó chỉ cho chủ bài đăng).

**Authentication:** Admin role required

---

#### GET `/admin/posts/{id}/comments`

Danh sách comment của 1 post để kiểm duyệt.

**Authentication:** Admin role required

**Query params:** `page` (default `0`), `size` (default `20`)

**Response (wrapped):** mảng `{ id, author_id, author_name, author_avatar_url, content, created_at }`

---

#### DELETE `/admin/comments/{id}`

Xoá bất kỳ comment nào.

**Authentication:** Admin role required

---

#### GET `/admin/reports`

Hàng đợi kiểm duyệt — các report đang `PENDING`, cũ nhất trước.

**Authentication:** Admin role required

**Query params:** `page` (default `0`), `size` (default `20`)

**Response (wrapped):**
```json
{
  "success": true,
  "data": [
    {
      "id": "r1...",
      "reporter_id": "u1...",
      "reporter_name": "Nguyễn Văn A",
      "post_id": "p1...",
      "comment_id": null,
      "target_type": "POST",
      "target_content": "Nội dung bài viết bị report...",
      "target_author_name": "Trần Văn B",
      "reason": "Spam / quảng cáo không liên quan",
      "status": "PENDING",
      "created_at": "2026-07-15T08:00:00Z"
    }
  ]
}
```

**Fields:**
- `target_type`: `"POST"` nếu `post_id` khác null, ngược lại `"COMMENT"` — suy ra ở tầng response, không phải cột riêng trong DB.

---

#### POST `/admin/reports/{id}/dismiss`

Đánh dấu report đã xem xét, không vi phạm (không xoá nội dung — muốn xoá thì gọi
`DELETE /admin/posts/{id}` hoặc `DELETE /admin/comments/{id}` riêng).

**Authentication:** Admin role required

**Error Responses:** `404 Not Found` nếu report không tồn tại hoặc không còn ở trạng thái `PENDING` (đã được xử lý trước đó)

#### POST `/admin/reports/{id}/ai-suggestion`

Gợi ý AI (qua `ai-rag-core` `/internal/ai/moderation-suggestion`) về việc report này có nên bị xử lý
hay không — hỗ trợ admin quyết định nhanh hơn, không tự động dismiss/xoá.

**Authentication:** Admin role required (`social:moderate`)

---

### CMS Management

> `AdminCmsController` quản lý bảng `cms_content` (crawled reports / jobs / keywords hiển thị
> trong admin CMS). **Không có phân trang** — `list()` không nhận `page`/`size` và trả về
> nguyên mảng phẳng trong `data`. **Không có field `content`** — cột nội dung đầy đủ tên là
> `body` (migration V35, `TEXT`, nullable), và `CmsContentRequest` (DTO nhận request cho
> create/update) **không có field `body`/`content` nào cả** — gọi qua API này luôn lưu
> `body = null`; `body` chỉ được điền nội bộ bởi `MonthlyReportSchedulerService` qua một overload
> khác của `CmsService.create(...)`, không thể touch được từ route admin này.

#### GET `/admin/cms`

Lấy toàn bộ nội dung CMS (không phân trang), mới tạo trước.

**Authentication:** Admin role required (`cms:manage`)

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": "770e8400-e29b-41d4-a716-446655440000",
      "title": "Technology Trends Report Q2 2024",
      "type": "Report",
      "content_date": "2024-06-30",
      "status": "Published",
      "body": null,
      "created_at": "2024-06-30T10:00:00Z",
      "updated_at": "2024-06-30T10:00:00Z"
    }
  ],
  "message": "CMS content",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields (mỗi item, khớp `CmsContent`):**
- `type`: chuỗi tự do, giá trị thực tế đang dùng là `Report` | `Job` | `Keyword` (không phải enum ràng buộc ở tầng API)
- `status`: chuỗi tự do, giá trị thực tế đang dùng là `Published` | `Analyzed` | `Pending` | `Archived`
- `body`: nội dung đầy đủ — luôn `null` khi tạo/sửa qua API admin này (xem lưu ý ở trên)

---

#### POST `/admin/cms`

Tạo nội dung CMS mới.

**Authentication:** Admin role required (`cms:manage`)

**Request Body:**
```json
{
  "title": "Technology Trends Report Q3 2024",
  "type": "Report",
  "content_date": "2024-09-30",
  "status": "Pending"
}
```

**Fields (khớp `CmsContentRequest` — KHÔNG có field `content`/`body`):**
- `title` (string, required — `@NotBlank`)
- `type` (string, optional) — không ràng buộc enum ở tầng validation; giá trị đang dùng: `Report` | `Job` | `Keyword`
- `content_date` (`LocalDate`, optional, format `yyyy-MM-dd`)
- `status` (string, optional, mặc định `"Pending"` nếu bỏ trống — xem `CmsService.create`); giá trị đang dùng: `Published` | `Analyzed` | `Pending` | `Archived`

**Response:** `201 Created`, body CMS content vừa tạo (shape như `GET /admin/cms`, `body` luôn `null`), `message: "CMS content created"`

---

#### PUT `/admin/cms/{id}`

Cập nhật nội dung CMS. Field nào gửi rỗng/`null` thì giữ nguyên giá trị cũ (partial update ở tầng
service — `title`/`status` chỉ ghi đè nếu có text, `type`/`content_date` chỉ ghi đè nếu không
`null`); `body` không thể sửa qua endpoint này.

**Authentication:** Admin role required (`cms:manage`)

**Request Body:** (cùng shape `CmsContentRequest` như POST, mọi field coi như optional ở tầng service)
```json
{
  "title": "Updated Title",
  "status": "Published"
}
```

**Response:** `200 OK`, body CMS content sau khi cập nhật (shape như `GET /admin/cms`), `message: "CMS content updated"`

**Error Responses:** `404 Not Found` nếu `id` không tồn tại

---

#### DELETE `/admin/cms/{id}`

Xóa nội dung CMS.

**Authentication:** Admin role required (`cms:manage`)

**Response:** `204 No Content`, body `ApiResponse<Void>` với `message: "CMS content deleted"`

**Error Responses:** `404 Not Found` nếu `id` không tồn tại

---

### Analytics ETL

#### POST `/admin/analytics/rebuild`

Dựng lại bảng `tech_analytics` từ Neo4j.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": {
    "rows_upserted": 1250
  },
  "message": "Analytics rebuilt successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Error Responses:**
- `503 Service Unavailable`: Neo4j không khả dụng

**Note:** Thao tác này có thể tốn thời gian tùy thuộc vào lượng dữ liệu trong Neo4j.

---

### Graph Analytics

#### POST `/admin/graph-analytics/rebuild`

Tính lại PageRank, cộng đồng công nghệ (Louvain) và degree centrality bằng Neo4j GDS trên đồ thị
con `Technology`-`RELATED_TO`-`Technology`, ghi kết quả trực tiếp lên `Technology` node
(`pagerank_score`, `community_id`, `degree_centrality` — xem [`docs/DATABASE.md`](./DATABASE.md)
§4.1). Cần plugin GDS (`docker-compose.yml` `NEO4J_PLUGINS`). Dữ liệu này hiển thị ở chế độ
"Phân tích đồ thị" trên Knowledge Graph Explorer, đọc lại qua `GET /graph/explore` như bất kỳ
property nào khác trên node — không có endpoint đọc riêng.

**Authentication:** Admin role required (`graph:manage`)

**Response:**
```json
{
  "success": true,
  "data": {
    "technologies_scored": 842,
    "communities_found": 37
  },
  "message": "Graph analytics rebuilt",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Error Responses:**
- `503 Service Unavailable`: Neo4j/GDS plugin không khả dụng

**Note:** `communities_found` là tổng số cộng đồng Louvain thô tìm được; UI chỉ hiển thị 6 cộng
đồng lớn nhất (`community_id` 0-5), phần còn lại gộp vào `community_id = 99` ("khác").

---

### Cache eviction

Company/job không có bước ETL/rebuild như radar (dữ liệu Neo4j đã cập nhật liên tục qua Kafka +
batch import) — chỉ có cache Redis (30 phút, xem [`docs/DATABASE.md`](./DATABASE.md) §5) là cũ.
3 endpoint này cho phép evict tay thay vì chờ TTL.

#### POST `/admin/cache/companies/evict`

Evict cache `cache:company:all` (dùng bởi `GET /companies` và `GET /companies/{id}/similar`).

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": null,
  "message": "Company cache evicted",
  "error_code": null,
  "timestamp": 1719792000000
}
```

#### POST `/admin/cache/jobs/evict`

Evict toàn bộ cache `cache:job:match:*` (một entry/tập kỹ năng, dùng bởi `GET /jobs/matches`).

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": null,
  "message": "Job match cache evicted",
  "error_code": null,
  "timestamp": 1719792000000
}
```

#### POST `/admin/cache/roadmap/evict`

Evict toàn bộ cache `cache:roadmap:*` (một entry/user, dùng bởi `GetCareerRoadmapUseCase`).

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": null,
  "message": "Roadmap cache evicted",
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

### Clustering Admin

`AdminClusteringController`, permission `clustering:manage`.

| Method | Path | Mô tả |
|---|---|---|
| GET | `/admin/clustering/pipeline/status` | Trạng thái lần chạy retrain gần nhất (idle/running/success/failed) |
| POST | `/admin/clustering/pipeline/trigger` | Kích hoạt retrain pipeline `ml-clustering` (409 nếu đang chạy) |
| GET | `/admin/clustering/pipeline/runs` | Lịch sử các lần chạy pipeline |
| PUT | `/admin/clustering/clusters/{clusterId}/label` | Ghi đè nhãn (label) cho 1 cluster |

**Authentication:** Admin role required (`clustering:manage`)

---

### Notification Admin

`AdminNotificationController`, permission `notification:manage`.

#### POST `/admin/notifications`

Gửi/broadcast thông báo in-app tới người dùng (admin).

**Authentication:** Admin role required (`notification:manage`)

---

### Data Platform Admin

`AdminDataPlatformController`, permission `datapipeline:manage` — chạy tay các job Gold/sync của
`data-platform` thay vì chờ lịch đêm, xem [`docs/DATA_PLATFORM.md`](./DATA_PLATFORM.md).

| Method | Path | Mô tả |
|---|---|---|
| GET | `/admin/data-platform/jobs` | Trạng thái/lần chạy gần nhất của mọi job |
| POST | `/admin/data-platform/jobs/{jobId}/trigger` | Kích hoạt 1 job chạy ngay (qua Redis Pub/Sub tới `data-platform`) |
| GET | `/admin/data-platform/jobs/{jobId}/history` | Lịch sử chạy của 1 job |

**Authentication:** Admin role required (`datapipeline:manage`)

---

### Audit Log

`AuditLogAdminController`, permission `audit:view`.

#### GET `/admin/audit-log`

Danh sách audit log entries (hành động admin đã thực hiện).

**Authentication:** Admin role required (`audit:view`)

---

### Crawler Admin

`CrawlerAdminController`, permission `crawler:manage`.

| Method | Path | Mô tả |
|---|---|---|
| POST | `/admin/crawler/trigger` | Kích hoạt crawler chạy ngay qua Redis (`delivered: false` nếu không có crawler container nào đang lắng nghe) |
| GET | `/admin/crawler/status` | Trạng thái lần crawl gần nhất |

**Authentication:** Admin role required (`crawler:manage`)

## 10. Health & Status — Public

### GET `/health`

Health check endpoint cho toàn bộ hệ thống.

**Authentication:** Public

**Response:**
```json
{
  "success": true,
  "data": {
    "status": "healthy",
    "version": "2.0.0",
    "timestamp": "2024-07-01T10:00:00Z",
    "dependencies": {
      "postgres": "connected",
      "neo4j": "connected",
      "redis": "connected"
    }
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Fields:**
- `status`: Overall status (`healthy`, `degraded`, `unhealthy`)
- `version`: API version
- `timestamp`: Current timestamp
- `dependencies`: Status của các dependencies
  - `postgres`: PostgreSQL connection status
  - `neo4j`: Neo4j connection status
  - `redis`: Redis connection status

---

### GET `/status`

Lấy feature flags và maintenance status (bare response).

**Authentication:** Public

**Response:**
```json
{
  "maintenance_web": false,
  "maintenance_mobile": false,
  "feature_graph": true,
  "feature_chat": true,
  "feature_rag": true,
  "feature_clustering": true,
  "feature_compare": true,
  "feature_career": true
}
```

**Fields:**
- `maintenance_web`: Maintenance mode cho web app
- `maintenance_mobile`: Maintenance mode cho mobile app
- `feature_graph`: Graph explorer feature enabled
- `feature_chat`: Chat/RAG feature enabled
- `feature_rag`: RAG feature enabled
- `feature_clustering`: Clustering feature enabled
- `feature_compare`: Compare feature enabled
- `feature_career`: Career path feature enabled

**Note:** Các giá trị này được đọc từ bảng `settings` trong PostgreSQL. Admin có thể cập nhật qua `/admin/settings`.

---

### GET `/stats/public`

Vài số liệu tổng quan công khai của hệ thống (vd. tổng số công ty, tin tuyển dụng, thành viên —
hiển thị ở trang landing).

**Authentication:** Public

---

## 11. Salary — `/api/v1/salary` *(đọc Neo4j, giá trị đơn vị triệu VND)*

### GET `/salary/top`

Top công nghệ theo mức lương trung vị (chỉ tính tech có ≥ `min_jobs` job posting có dữ liệu lương).

**Authentication:** Required (Bearer JWT)

**Query params:**
- `limit` (int, optional, default `20`)
- `min_jobs` (int, optional, default `3`)

**Response (wrapped):**
```json
{
  "success": true,
  "data": [
    {
      "tech_name": "React",
      "total_jobs": 120,
      "jobs_with_salary": 45,
      "median_salary_mvnd": 22.0,
      "avg_salary_mvnd": 23.5,
      "min_salary_mvnd": 12.0,
      "max_salary_mvnd": 45.0,
      "p25_salary_mvnd": 18.0,
      "p75_salary_mvnd": 28.0,
      "salary_range": "18 - 28 triệu VND",
      "top_co_techs": ["TypeScript", "Redux", "Node.js"]
    }
  ],
  "message": "Salary insights",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Example:**
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/salary/top?limit=10&min_jobs=3"
```

---

### GET `/salary/tech/{techName}`

Chi tiết lương cho một công nghệ cụ thể (cùng shape response với `/salary/top`, chỉ 1 object thay vì mảng).

**Authentication:** Required (Bearer JWT)

**Path params:**
- `techName` (string, required) — tên công nghệ, ví dụ `React`

**Error Responses:**
- `404 Not Found` (`NOT_FOUND`): Không có dữ liệu lương cho tech này

**Example:**
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/salary/tech/React"
```

---

## 12. Company — `/api/v1/companies` *(Neo4j; NEW)*

### GET `/companies`

Danh sách công ty, xếp hạng theo số lượng job đang tuyển. Tech stack của mỗi công ty được suy ra
gián tiếp qua `Company<-[:POSTED_BY|HIRES_FOR]-Job-[:REQUIRES]->Technology` (KHÔNG đọc quan hệ
`USES` — xem [`docs/DATABASE.md`](./DATABASE.md) §4.1 về sự khác biệt này).

**Authentication:** Public (`GET /companies/**` nằm trong `SecurityConfig.PUBLIC_ROUTES` — trang
Company Explorer hiển thị công khai, không cần đăng nhập)

**Query params:**
- `page` (int, optional, default `0`)
- `size` (int, optional, default `20`)

**Lưu ý cache:** kết quả Neo4j được cache nguyên khối trong Redis 30 phút (`cache:company:all`,
xem [`docs/DATABASE.md`](./DATABASE.md) §5) rồi mới phân trang trong bộ nhớ — công ty/job mới
ingest có thể chưa xuất hiện ngay cho tới khi cache hết hạn hoặc admin gọi tay
`POST /admin/cache/companies/evict` (xem mục 9).

**Response (wrapped):**
```json
{
  "success": true,
  "data": [
    {
      "id": "4:abc:123",
      "name": "FPT Software",
      "location": "Hà Nội",
      "job_count": 34,
      "tech_stack": ["Java", "React", "PostgreSQL"]
    }
  ],
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

### GET `/companies/{id}/similar`

Công ty có tech stack tương tự (Jaccard similarity, tính in-memory trên toàn bộ tập công ty).

**Authentication:** Public (`GET /companies/**`, xem ghi chú ở mục `GET /companies` phía trên)

**Path params:**
- `id` (string, required) — Neo4j element id của company

**Query params:**
- `limit` (int, optional, default `10`, 1-100)

**Response (wrapped):**
```json
{
  "success": true,
  "data": [
    {
      "id": "4:abc:456",
      "name": "VNG Corporation",
      "location": "TP.HCM",
      "shared_techs": ["Java", "React"],
      "score": 0.42
    }
  ]
}
```

**Error Responses:**
- `404 Not Found`: `id` không tồn tại

---

### GET `/companies/{id}/health-score`

Điểm "sức khoẻ công nghệ" của công ty (suy ra từ mức độ đa dạng/hiện đại của tech stack qua job
posting).

**Authentication:** Public

### GET `/companies/{id}/mentions`

Danh sách bài viết có nhắc đến công ty này (`Article-[:MENTIONS]->Company`).

**Authentication:** Public

---

## 13. Job Matching — `/api/v1/jobs` *(Neo4j; NEW)*

### GET `/jobs/matches`

Gợi ý job phù hợp với hồ sơ (kỹ năng trong `user_profile.technologies`), xếp theo `score = số kỹ
năng khớp / số kỹ năng job yêu cầu`. Location/min-salary được lọc phía Java (Cypher không parse
được lương dạng free-text tiếng Việt).

**Authentication:** Required (Bearer JWT) — dùng `SecurityUtils.currentUserId()` để lấy kỹ năng hồ sơ

**Query params:**
- `location` (string, optional)
- `min_salary` (number, optional, đơn vị triệu VND)
- `limit` (int, optional, default `20`)

**Response (wrapped):**
```json
{
  "success": true,
  "data": [
    {
      "title": "Senior Backend Developer",
      "company": "Tiki",
      "location": "TP.HCM",
      "salary_raw": "25-35 triệu",
      "salary_min_mvnd": 25.0,
      "salary_max_mvnd": 35.0,
      "source_url": "https://itviec.com/...",
      "due_date": "2026-08-01",
      "matched_skills": ["Java", "Spring Boot"],
      "missing_skills": ["Kafka"],
      "score": 0.67
    }
  ]
}
```

**Fields:**
- `salary_min_mvnd`/`salary_max_mvnd`: lưu ý tên field — Jackson snake_case biến `salaryMinMVnd` (Java) thành `salary_min_mvnd` (KHÔNG phải `salary_min_m_vnd`).
- `matched_skills`/`missing_skills`: so khớp giữa kỹ năng hồ sơ và kỹ năng job yêu cầu.

**Lưu ý cache:** kết quả Neo4j (trước khi lọc `location`/`min_salary`) được cache theo tập kỹ năng
hồ sơ trong Redis 30 phút (`cache:job:match:<skills>`) — job mới ingest có thể chưa xuất hiện
ngay cho tới khi cache hết hạn hoặc admin gọi tay `POST /admin/cache/jobs/evict` (xem mục 9);
xem [`docs/DATABASE.md`](./DATABASE.md) §5.

**Example:**
```bash
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/jobs/matches?location=H%C3%A0%20N%E1%BB%99i&min_salary=20&limit=10"
```

---

## 14. Messaging — `/api/v1/conversations` *(Postgres + SSE; NEW)*

1-1 direct messaging. Realtime là **SSE** (không phải WebSocket), push qua Redis Pub/Sub nên hoạt
động đúng dù chạy nhiều instance backend — xem [`docs/DATABASE.md`](./DATABASE.md) §5. Vì
`EventSource` chuẩn không set được header `Authorization`, client PHẢI dùng `fetch` +
`ReadableStream` để đọc `/conversations/stream` (không dùng `new EventSource(...)` trực tiếp).

### GET `/conversations`

Danh sách hội thoại của user hiện tại, mới nhất trước.

**Authentication:** Required (Bearer JWT)

**Query params:**
- `page` (int, optional, default `0`)
- `size` (int, optional, default `20`, tối đa 100)

**Response (wrapped):**
```json
{
  "success": true,
  "data": [
    {
      "id": "c1a2...",
      "other_user": { "id": "u1...", "full_name": "Nguyễn Văn A", "avatar_url": null },
      "last_message_content": "Chào bạn!",
      "last_message_at": "2026-07-15T10:00:00Z",
      "last_message_sender_id": "u1...",
      "unread_count": 2
    }
  ]
}
```

---

### POST `/conversations/with/{userId}`

Lấy hội thoại 1-1 với `userId`, tạo mới nếu chưa có (upsert theo cặp user, canonical
`user_a_id < user_b_id`).

**Authentication:** Required (Bearer JWT)

**Response (wrapped):** `{ "id": "<conversation_id>" }`

**Error Responses:**
- `400 Bad Request` (`INVALID_CONVERSATION`): tự nhắn tin cho chính mình

---

### GET `/conversations/{id}/messages`

**Authentication:** Required (Bearer JWT) — 404 nếu người gọi không phải thành viên hội thoại

**Query params:** `page` (default `0`), `size` (default `30`)

**Response (wrapped):** mảng message, cũ nhất trước — mỗi item khớp `DirectMessageResponse`:
```json
[
  {
    "id": "m1...",
    "conversation_id": "c1...",
    "sender_id": "u1...",
    "content": "Xin chào!",
    "created_at": "2026-07-15T10:00:00Z",
    "read": true,
    "attachment": null,
    "reactions": [
      { "emoji": "👍", "count": 2, "reacted_by_me": true }
    ]
  }
]
```

**Fields:**
- `attachment` (nullable): `null` nếu message không có file đính kèm, ngược lại
  `{ content_type, filename, size, url }` — xem shape đầy đủ ở `POST /conversations/{id}/messages` bên dưới
- `reactions`: luôn là 1 mảng (rỗng nếu chưa ai react), mỗi phần tử `{ emoji, count, reacted_by_me }`
  — `reacted_by_me` tính theo user gọi API (viewer), KHÔNG phải sender

---

### POST `/conversations/{id}/messages`

Gửi tin nhắn, có thể kèm 1 file/ảnh đính kèm (base64, không có endpoint upload riêng — file đi
thẳng trong cùng request). Sau khi lưu Postgres sẽ push realtime tới người nhận qua SSE, đồng thời
tạo notification `NEW_MESSAGE` (kèm preview 140 ký tự, `link=/messages?conversation={id}`) cho
người nhận.

**Authentication:** Required (Bearer JWT)

**Request Body:**
```json
{
  "content": "Xin chào!",
  "attachment": {
    "content_type": "image/png",
    "filename": "screenshot.png",
    "data_base64": "iVBORw0KGgoAAAANSUhEUgA..."
  }
}
```

**Fields:**
- `content` (string) — required trừ khi có `attachment` (được phép rỗng nếu message chỉ gồm file); nếu có, tối đa 2000 ký tự sau khi trim
- `attachment` (object, optional, có thể là `null`/bỏ qua hoàn toàn):
  - `content_type` (string, required nếu có `attachment`) — phải khớp đúng 1 trong allowlist (xem bên dưới), so sánh không phân biệt hoa/thường
  - `filename` (string, optional) — ký tự `\r \n " \ /` bị thay bằng `_`; cắt về tối đa 255 ký tự; bỏ trống → mặc định `"file"`
  - `data_base64` (string, required nếu có `attachment`) — payload base64 của file; cho phép có tiền tố data-URL (`data:image/png;base64,...`, phần trước dấu phẩy bị bỏ)

**Giới hạn file đính kèm (`FileUploadValidator`):**
- Kích thước sau khi decode base64: tối đa **10 MB**
- Content-type allowlist: `image/png`, `image/jpeg`, `image/jpg`, `image/webp`, `image/gif`,
  `application/pdf`, `application/msword`,
  `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (`.docx`),
  `application/vnd.ms-excel`,
  `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (`.xlsx`),
  `text/plain`, `application/zip`
  — **KHÔNG** có `image/svg+xml` (chặn có chủ đích để tránh stored-XSS qua endpoint serve file công khai cho 2 thành viên hội thoại)

**Response (wrapped):** message vừa tạo, cùng shape với item trong `GET /conversations/{id}/messages`:
```json
{
  "id": "m1...",
  "conversation_id": "c1...",
  "sender_id": "u1...",
  "content": "Xin chào!",
  "created_at": "2026-07-15T10:00:00Z",
  "read": false,
  "attachment": {
    "content_type": "image/png",
    "filename": "screenshot.png",
    "size": 48213,
    "url": "/conversations/c1.../messages/m1.../attachment"
  },
  "reactions": []
}
```
- `attachment.url`: đường dẫn tương đối (chưa kèm `/api/v1`) để gọi `GET .../attachment` bên dưới; `size` là số byte sau khi decode

**Error Responses:**
- `400 Bad Request` (`INVALID_CONTENT`): `content` rỗng và không có `attachment`, hoặc `content` > 2000 ký tự
- `400 Bad Request` (`INVALID_ATTACHMENT`): `data_base64` không decode được / decode ra rỗng hoặc > 10MB, hoặc `content_type` không nằm trong allowlist
- `404 Not Found`: người gọi không phải thành viên hội thoại

---

### GET `/conversations/{conversationId}/messages/{messageId}/attachment`

Lấy file/ảnh đính kèm của 1 message (binary) — chỉ 2 thành viên của hội thoại mới gọi được.

**Authentication:** Required (Bearer JWT)

**Response:** binary, headers:
- `Content-Type`: content-type gốc đã validate lúc upload (vd. `image/png`)
- `Content-Disposition`: `inline; filename="<filename đã sanitize>"`
- `X-Content-Type-Options`: `nosniff`

**Error Responses:**
- `404 Not Found`: người gọi không phải thành viên hội thoại, `messageId` không tồn tại/không thuộc `conversationId`, hoặc message đó không có attachment

---

### POST `/conversations/{conversationId}/messages/{messageId}/reactions`

Set (hoặc thay) reaction của user hiện tại trên 1 message — mỗi user chỉ có tối đa 1 reaction/message
(gọi lại với emoji khác = thay reaction cũ, upsert theo `(message_id, user_id)`).

**Authentication:** Required (Bearer JWT)

**Request Body:**
```json
{ "emoji": "👍" }
```

**Fields:**
- `emoji` (string, required) — chỉ nhận đúng 1 trong 6 giá trị (`SetMessageReactionUseCase.ALLOWED_EMOJI`,
  không phải emoji picker tự do): 👍 ❤️ 😂 😮 😢 😡

**Response (wrapped):** mảng reaction summary hiện tại của message (theo góc nhìn user gọi API):
```json
{
  "success": true,
  "data": [
    { "emoji": "👍", "count": 2, "reacted_by_me": true }
  ],
  "message": "Reaction set",
  "error_code": null,
  "timestamp": 1719792000000
}
```

**Side effect:** push `REACTIONS_CHANGED` cho người còn lại trong hội thoại qua SSE (`/conversations/stream`)
— xem shape ở mục SSE bên dưới. Người vừa set reaction không nhận lại event này qua stream, chỉ có response HTTP này.

**Error Responses:**
- `400 Bad Request` (`INVALID_CONTENT`): "Unsupported reaction emoji" — `emoji` không nằm trong danh sách 6 giá trị trên
- `404 Not Found`: người gọi không phải thành viên hội thoại, hoặc `messageId` không thuộc `conversationId`

---

### DELETE `/conversations/{conversationId}/messages/{messageId}/reactions`

Xoá reaction của user hiện tại trên 1 message (không có request body).

**Authentication:** Required (Bearer JWT)

**Response (wrapped):** cùng shape với `POST .../reactions`, `message: "Reaction removed"`

**Side effect:** cùng broadcast `REACTIONS_CHANGED` như `POST .../reactions`.

**Error Responses:**
- `404 Not Found`: người gọi không phải thành viên hội thoại, hoặc `messageId` không thuộc `conversationId`

---

### POST `/conversations/{id}/read`

Đánh dấu toàn bộ tin nhắn (không phải do mình gửi) trong hội thoại là đã đọc.

**Authentication:** Required (Bearer JWT)

**Response:** `ApiResponse<Void>`

---

### GET `/conversations/stream`

SSE stream mọi event messaging của user hiện tại (mọi hội thoại) — tin nhắn mới **và** thay đổi
reaction, dùng để cập nhật badge/tin nhắn realtime toàn app.

**Authentication:** Required (Bearer JWT, gửi qua header — KHÔNG dùng query param)

**Response:** `text/event-stream`. Mỗi `data:` line là 1 JSON `MessageLiveEventResponse` — discriminated
union theo field `type`, chỉ 1 trong 2 dạng sau (field không liên quan tới `type` hiện tại sẽ `null`):

- `type: "NEW_MESSAGE"` — có tin nhắn mới (gửi bởi người kia trong 1 hội thoại của mình):
  ```json
  {
    "type": "NEW_MESSAGE",
    "message": {
      "id": "m1...",
      "conversation_id": "c1...",
      "sender_id": "u1...",
      "content": "Xin chào!",
      "created_at": "2026-07-15T10:00:00Z",
      "read": false,
      "attachment": null,
      "reactions": []
    }
  }
  ```
- `type: "REACTIONS_CHANGED"` — reaction trên 1 message vừa được set/xoá bởi người kia:
  ```json
  {
    "type": "REACTIONS_CHANGED",
    "conversation_id": "c1...",
    "message_id": "m1...",
    "reactions": [
      { "emoji": "👍", "count": 1, "reacted_by_me": false }
    ]
  }
  ```

**Example:**
```bash
curl -N -H "Authorization: Bearer $TOKEN" -H "Accept: text/event-stream" \
  "http://localhost:8080/api/v1/conversations/stream"
```

---

## 15. Social / Feed — `/api/v1/feed`, `/api/v1/posts`, `/api/v1/users` *(Postgres; NEW)*

> Lưu ý: 2 controller khác convention nhau — `PostController` KHÔNG có prefix riêng (route thẳng
> `/feed`, `/posts/**`), còn `UserSocialController` dùng prefix `/users`.

### GET `/feed`

Feed bài đăng của bản thân + người đang follow.

**Authentication:** Required (Bearer JWT)

**Query params:** `page` (default `0`), `size` (default `20`, tối đa 50)

**Response (wrapped):**
```json
{
  "success": true,
  "data": [
    {
      "id": "p1...",
      "author": { "id": "u1...", "full_name": "Nguyễn Văn A", "avatar_url": null },
      "content": "Vừa học xong Kubernetes!",
      "created_at": "2026-07-15T09:00:00Z",
      "like_count": 3,
      "comment_count": 1,
      "liked_by_me": false
    }
  ]
}
```

---

### POST `/posts`

**Authentication:** Required (Bearer JWT)

**Request Body:** `{ "content": "..." }` (required, ≤ 2000 ký tự)

**Response (wrapped):** post vừa tạo (shape như trong `/feed`)

---

### DELETE `/posts/{id}`

Chỉ chủ bài đăng được xoá.

**Authentication:** Required (Bearer JWT)

**Error Responses:** `404 Not Found` nếu không phải chủ bài đăng (hoặc id không tồn tại)

---

### POST `/posts/{id}/like` · DELETE `/posts/{id}/like`

Like/unlike một bài đăng (idempotent).

**Authentication:** Required (Bearer JWT)

**Side effect:** lần like đầu tiên (không tính unlike hay like lặp lại) tạo notification
`POST_LIKE` cho tác giả bài viết (bỏ qua nếu tự like bài của mình); lỗi tạo notification không
làm fail request like — xem [`docs/BACKEND_GUIDE.md`](./BACKEND_GUIDE.md) §4.10.

---

### GET `/posts/{id}/comments` · POST `/posts/{id}/comments`

**Authentication:** GET là Public (`GET /posts/*/comments` nằm trong `SecurityConfig.PUBLIC_ROUTES`
— khớp với việc handler không đọc identity người gọi). POST yêu cầu Bearer JWT.

**Query params (GET):** `page` (default `0`), `size` (default `20`)

**Request Body (POST):** `{ "content": "..." }` (required, ≤ 1000 ký tự)

**Response (wrapped, GET):** mảng `{ id, author, content, created_at }`, cũ nhất trước.

**Side effect (POST):** tạo notification `POST_COMMENT` (kèm preview 140 ký tự) cho tác giả bài
viết, bỏ qua nếu tự comment vào bài của mình.

---

### POST `/posts/{id}/report` · POST `/comments/{id}/report` *(NEW)*

Báo cáo (flag) một bài viết hoặc bình luận vi phạm để admin xem xét trong hàng đợi kiểm duyệt.

**Authentication:** Required (Bearer JWT)

**Request Body:**
```json
{ "reason": "Spam / quảng cáo không liên quan" }
```

**Fields:**
- `reason` (string, required, 1-500 ký tự)

**Response:** `ApiResponse<Void>`

**Hành vi idempotent:** nếu user này đã có 1 report **PENDING** trên đúng target đó, gọi lại
không tạo dòng mới (`ON CONFLICT DO NOTHING`, im lặng — không lỗi, không phản hồi khác biệt).
Nếu report trước đó đã bị admin dismiss, user CÓ THỂ report lại (V12 chỉ tính `status='PENDING'`
là "đã report" — xem [`docs/DATABASE.md`](./DATABASE.md) §3.2).

**Error Responses:**
- `400 Bad Request` (`INVALID_REASON`): `reason` rỗng hoặc quá 500 ký tự

---

### GET `/users/{id}/profile-summary`

**Authentication:** Required (Bearer JWT)

**Response (wrapped):**
```json
{
  "id": "u1...",
  "full_name": "Nguyễn Văn A",
  "avatar_url": null,
  "bio": "Backend developer",
  "job_role": "Backend Developer",
  "location": "Hà Nội",
  "follower_count": 12,
  "following_count": 8,
  "post_count": 5,
  "is_following": false
}
```

**Fields:**
- `is_following`: field này được ép `@JsonProperty("is_following")` tường minh trong code — KHÔNG
  suy ra tự động từ naming strategy (field Java tên `following`, không có "hump" để tách).

---

### GET `/users/{id}/posts`

Bài đăng của 1 user, `liked_by_me` được tính theo người xem hiện tại.

**Authentication:** Required (Bearer JWT)

**Query params:** `page` (default `0`), `size` (default `20`)

---

### POST `/users/{id}/follow` · DELETE `/users/{id}/follow`

**Authentication:** Required (Bearer JWT)

**Error Responses:** `400 Bad Request` (`INVALID_FOLLOW`): tự follow chính mình

**Side effect:** follow mới (không tính unfollow rồi follow lại — chỉ tính lần đầu) tạo
notification `NEW_FOLLOWER` cho người được follow, `link` trỏ về `/users/{followerId}`.

---

### GET `/users/suggested`

Gợi ý người để follow, xếp theo số follower.

**Authentication:** Required (Bearer JWT)

**Query params:** `limit` (default `10`, tối đa 50)

**Response (wrapped):** mảng `{ id, full_name, avatar_url }`

---

### GET `/users/search`

Tìm user theo tên.

**Authentication:** Required (Bearer JWT)

---

### GET `/feed/stream`

SSE stream — đẩy bài đăng mới trong feed real-time (`live:feed` Redis Pub/Sub, xem
[`docs/DATABASE.md`](./DATABASE.md) §5), không cần tự refresh.

**Authentication:** Required (Bearer JWT)

---

### GET `/posts/{postId}/images/{imageId}`

Lấy 1 ảnh đính kèm bài đăng (binary).

**Authentication:** Public

---

### GET `/hashtags/trending`

Danh sách hashtag đang trend trong feed.

**Authentication:** Public

---

## 16. AI Interview — `/api/v1/interview` *(proxy ai-rag-core qua module `aiproxy`; NEW)*

### POST `/interview`

Phỏng vấn thử với AI — **stateless**: client tự giữ toàn bộ lịch sử (`history`) và gửi lại đầy đủ
mỗi lượt; trạng thái (mở đầu / giữa buổi / kết thúc) được server suy ra hoàn toàn từ độ dài
`history` (không có session lưu phía server). Request được gateway forward nguyên văn tới
`ai-rag-core` `POST /interview` qua `PythonAiProxyClient` (xem [`docs/AI_PLATFORM.md`](./AI_PLATFORM.md)).

**Authentication:** Required (Bearer JWT) — `user_id` được gateway đính kèm từ token nếu có

**Request Body:**
```json
{
  "target_role": "Senior Backend Developer",
  "target_company": "Tiki",
  "history": [
    { "question": "Bạn hãy giới thiệu về bản thân?", "answer": "Tôi có 5 năm kinh nghiệm..." }
  ]
}
```

**Fields:**
- `target_role` (string, required, 1-120 ký tự)
- `target_company` (string, optional)
- `history` (array, optional, default `[]`) — gửi rỗng để bắt đầu buổi phỏng vấn mới; mỗi phần tử `{ question, answer }`

**Response (wrapped — double-wrapped: response Python nằm nguyên trong `data`):**
```json
{
  "success": true,
  "data": {
    "next_question": "Bạn đã từng làm việc với hệ thống distributed chưa?",
    "feedback_on_last_answer": "Câu trả lời khá tốt, nên nêu ví dụ cụ thể hơn.",
    "is_final": false,
    "turn": 2,
    "final_summary": null
  }
}
```

Khi `history.length >= 5` (MAX_TURNS phía `ai-rag-core`), response chuyển sang lượt cuối:
```json
{
  "next_question": null,
  "feedback_on_last_answer": null,
  "is_final": true,
  "turn": 5,
  "final_summary": { "score": 7, "summary": "**Điểm mạnh:**\n- ...\n**Cần cải thiện:**\n- ..." }
}
```

**Error Responses:**
- `503 Service Unavailable`: mọi lỗi từ `ai-rag-core` bị gộp chung thành lỗi generic này (gateway không passthrough error detail của Python) — xem [`docs/BACKEND_GUIDE.md`](./BACKEND_GUIDE.md) §4.16.

**Example:**
```bash
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  http://localhost:8080/api/v1/interview \
  -d '{"target_role":"Senior Backend Developer","target_company":"Tiki","history":[]}'
```

---

## 17. AiProxy — forward nguyên văn sang `ai-rag-core` *(module `aiproxy`)*

Mỗi endpoint dưới đây là 1 controller riêng nhưng đều chỉ forward `Map<String,Object>` nguyên văn
qua `AiProxyRequestHandler`/`PythonAiProxyClient` (không có typed DTO phía Java) — response từ
Python bọc verbatim vào `ApiResponse.data`. Bất kỳ lỗi nào từ `ai-rag-core` đều bị gộp thành
`503 SERVICE_UNAVAILABLE` chung (không passthrough chi tiết lỗi). Xem
[`docs/BACKEND_GUIDE.md`](./BACKEND_GUIDE.md) §4.16 và [`docs/AI_PLATFORM.md`](./AI_PLATFORM.md).

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| POST | `/career` | JWT (`forwardAsCurrentUser`) | Tư vấn career path bằng AI (khác `GET /career/roadmap` ở §18 — đây là proxy LLM, không phải tính native) |
| POST | `/recommend` | JWT (`forwardAsCurrentUser`) | Gợi ý công nghệ nên học tiếp |
| POST | `/interview` | JWT (`forwardAsCurrentUser`) | Xem §16 |
| POST | `/agent` | JWT (`forwardAsCurrentUser`) | AI Agent multi-tool (LangChain) |
| GET | `/forecast` | Public (`forward`) | Dự báo xu hướng công nghệ |
| GET | `/report` | Public (`forward`) | Báo cáo xu hướng tổng hợp theo kỳ |
| POST | `/chat/summarize` | Public (`forward`) | Tóm tắt nhanh 1 đoạn hội thoại/nội dung |
| POST | `/company-insight` | Public (`forward`) | Tóm tắt AI về hồ sơ tuyển dụng/tech stack 1 công ty — hiển thị trên trang `/companies` công khai |

Ranh giới public/JWT là controller gọi `forward()` (nội dung chung, không cá nhân hoá) hay
`forwardAsCurrentUser()` (đính `user_id` từ JWT, cá nhân hoá theo người dùng) trên
`AiProxyRequestHandler` — không phải quyết định bảo mật đặc biệt cho từng route.

**Rate limit:** 20 req/60s, theo user id (route JWT) hoặc IP (route public) — xem
[Rate Limiting](#rate-limiting).

---

## 18. Career Roadmap — `/api/v1/career/roadmap`, `/api/v1/career/simulate` *(feature `roadmap`, native — không proxy)*

Cùng base path `/career` với `POST /career` ở §17 nhưng là **controller khác**
(`CareerRoadmapController`, feature `roadmap`) — tính native trên backend, không forward sang
`ai-rag-core`.

#### GET `/career/roadmap`

Lộ trình sự nghiệp cá nhân hoá: gộp gợi ý kỹ năng tiếp theo (`/recommend`), lộ trình vai trò
(`/career`) và job phù hợp (`/jobs/matches`) vào 1 lần gọi có cache
(`cache:roadmap:<userId>`, TTL 30 phút — evict qua `POST /admin/cache/roadmap/evict`, §9). Nếu hồ
sơ chưa có `technologies` nào, trả `has_technologies: false` với các mục rỗng thay vì lỗi.

**Authentication:** Required (Bearer JWT)

#### GET `/career/simulate`

"What-if": mô phỏng tác động của việc học thêm 1 công nghệ giả định (không lưu vào hồ sơ) — số
job phù hợp trước/sau, thống kê lương thị trường thật, và dự báo xu hướng (thống kê + LLM). Kết
hợp `/jobs/matches` scoring, `/salary/tech` và `/forecast`. Cache riêng
`cache:simulate:<userId>:<tech>`.

**Authentication:** Required (Bearer JWT)

**Query params:** `technology` (string, required)

---

## Phân quyền

### Public Endpoints

Không yêu cầu JWT authentication:

- `/auth/login`
- `/auth/register`
- `/auth/refresh`
- `/auth/logout`
- `/auth/forgot-password`
- `/auth/reset-password`
- `/health`
- `/status`
- `GET /user/avatar/{userId}`
- `GET /companies/**` *(Company Explorer hiển thị công khai — bao gồm cả `GET /companies/{id}/similar`)*
- `GET /posts/*/comments`, `GET /posts/*/images/**` *(đọc bình luận/ảnh bài viết không cần định danh người gọi; POST vẫn yêu cầu JWT)*
- `/actuator/**`
- Swagger UI (`/swagger-ui/**`, `/v3/api-docs/**`)
- `GET /forecast` *(proxy `aiproxy` — dùng `AiProxyRequestHandler.forward()`, nội dung chung không cá nhân hoá)*
- `GET /report` *(proxy `aiproxy`, tương tự)*
- `POST /chat/summarize` *(proxy `aiproxy`, tương tự)*
- `POST /company-insight` *(proxy `aiproxy`, tương tự — hiển thị trên trang `/companies` công khai nên phải public)*

### Admin Endpoints

Tất cả endpoints dưới `/admin/**` yêu cầu JWT hợp lệ + đúng permission code (không phải bare
`hasRole('ADMIN')` — permission-based RBAC, migration `V24`/`V27`, xem
[`docs/BACKEND_GUIDE.md`](./BACKEND_GUIDE.md) §6.3). Role `admin` có đủ cả 13 permission; role
`moderator` chỉ có `social:moderate` + `audit:view`.

| Permission code | Controller |
|---|---|
| `user:manage` | `UserAdminController` |
| `notification:manage` | `AdminNotificationController` |
| `analytics:manage` | `AnalyticsAdminController` |
| `cms:manage` | `AdminCmsController` |
| `crawler:manage` | `CrawlerAdminController` |
| `cache:manage` | `CacheAdminController` |
| `system:settings` | `AdminController` |
| `datapipeline:manage` | `AdminDataPlatformController` |
| `social:moderate` | `AdminSocialController` |
| `audit:view` | `AuditLogAdminController` |
| `dashboard:view` | `AdminDashboardController` |
| `clustering:manage` | `AdminClusteringController` |
| `graph:manage` | `GraphAnalyticsAdminController` |

### Authenticated Endpoints

Yêu cầu JWT hợp lệ:

- Tất cả endpoints còn lại không thuộc Public hoặc Admin — bao gồm TOÀN BỘ endpoint mới:
  `/jobs/matches`, `/conversations/**`, `/feed`, `/users/**`, `/salary/**`, và (trong `aiproxy`)
  `/career`, `/recommend`, `/interview`, `/agent`. Ngoại lệ theo method: `/companies/**` và
  `/posts/**` chỉ auth-required cho các route KHÔNG nằm trong danh sách Public phía trên (vd.
  `POST /posts`, `DELETE /posts/{id}`, `POST /posts/{id}/comments` vẫn cần JWT; `GET /companies/**`
  thì không).

**Note:** `spring.webflux.base-path` bị strip **trước** security filter, nên matcher trong `SecurityConfig.PUBLIC_ROUTES` được khai báo **không** kèm `/api/v1`.

**Nguyên tắc public/auth trong `aiproxy`:** ranh giới là controller gọi `forward()` hay
`forwardAsCurrentUser()` trên `AiProxyRequestHandler`. `forward()` (không đính kèm user) → nội
dung chung, public: `/forecast`, `/report`, `/chat/summarize`, `/company-insight`.
`forwardAsCurrentUser()` (đính `user_id` từ JWT, kết quả cá nhân hoá theo người dùng đang đăng
nhập) → yêu cầu auth: `/career`, `/recommend`, `/interview`, `/agent`. `/company-insight` trước
đây bị bỏ sót khỏi `PUBLIC_ROUTES` dù dùng `forward()` — khách ẩn danh vào trang `/companies`
(công khai) bị 401 ở `/company-insight` và bị web client tự động đăng xuất; đã thêm vào
`PUBLIC_ROUTES` cho khớp nguyên tắc trên. Xem [`docs/BACKEND_GUIDE.md`](./BACKEND_GUIDE.md) §4.16.

---

## Proxy sang Python

Spring Boot gateway proxy các request đến Python services với header bảo mật.

### ai-rag-core Proxy

**Endpoints được proxy:**
- `/chat/**` — qua `RagProxyService` riêng, request/response có typed DTO (§6)
- `/compare/llm-summary` — gọi `ai-rag-core` `POST /internal/ai/llm-summary`
- `/career`, `/forecast`, `/recommend`, `/report`, `/chat/summarize`, `/agent`, `/interview`,
  `/company-insight` (§11-16 + AI Interview) — qua module **`aiproxy`** (`PythonAiProxyClient`/`AiProxyPort`):
  forward **nguyên văn** `Map<String,Object>` (KHÔNG có typed DTO phía Java, khác với `/chat/**`),
  response từ Python được bọc verbatim vào `ApiResponse.data` (double-wrapped). Bất kỳ lỗi nào
  từ phía Python đều bị gộp thành `503 SERVICE_UNAVAILABLE` chung, không passthrough chi tiết lỗi.
  Timeout riêng cho `/agent` là 120s; các endpoint còn lại trong nhóm này là 60s
  (`AiProxyPort.DEFAULT_TIMEOUT`).

**Configuration:**
- Environment variable: `PYTHON_RAG_BASE_URL` (mặc định: `http://ai-rag-core:8000`) — dùng cho `/chat/**`
- `app.python.ai.base-url` (mặc định: `http://localhost:8000`) — dùng cho nhóm `aiproxy`
- Security header: `X-Internal-Auth: <INTERNAL_API_TOKEN>` (`app.python.internal-token`)
- Timeout: 120 giây cho `/chat/**` và `/agent`; 60 giây cho phần còn lại của `aiproxy`

**Example:**
```http
POST /api/v1/chat/session/{sessionId}/messages
X-Internal-Auth: techradar-internal-secret
```

---

### ml-clustering Proxy

**Endpoints được proxy:**
- `/clustering/**`

**Configuration:**
- Environment variable: `PYTHON_ML_CLUSTERING_BASE_URL` (mặc định: `http://ml-clustering:8001`)
- Security header: Không yêu cầu (service không có auth)
- Timeout: 60 seconds

---

## Error Codes

### Common Error Codes

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `AUTH_ERROR` | 401 | Authentication failed |
| `TOKEN_EXPIRED` | 401 | JWT token expired |
| `TOKEN_INVALID` | 401 | Invalid JWT token |
| `FORBIDDEN` | 403 | Access denied (insufficient permissions) |
| `NOT_FOUND` | 404 | Resource not found |
| `CONFLICT` | 409 | Resource already exists |
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `BAD_REQUEST` | 400 | Invalid request |
| `SERVICE_UNAVAILABLE` | 503 | External service unavailable |
| `INTERNAL_ERROR` | 500 | Internal server error |

### Auth Error Codes

| Error Code | Description |
|------------|-------------|
| `EMAIL_EXISTS` | Email already registered |
| `INVALID_CREDENTIALS` | Invalid email or password |
| `INVALID_TOKEN` | Invalid refresh token |
| `TOKEN_REVOKED` | Token has been revoked |

### User Error Codes

| Error Code | Description |
|------------|-------------|
| `USER_NOT_FOUND` | User not found |
| `AVATAR_TOO_LARGE` | Avatar file exceeds size limit |
| `INVALID_IMAGE_FORMAT` | Unsupported image format |

### Graph Error Codes

| Error Code | Description |
|------------|-------------|
| `GRAPH_QUERY_FAILED` | Neo4j query failed |
| `GRAPH_NOT_CONNECTED` | Neo4j connection failed |

### Chat Error Codes

| Error Code | Description |
|------------|-------------|
| `RAG_SERVICE_UNAVAILABLE` | ai-rag-core service unavailable |
| `LLM_ERROR` | LLM generation failed |
| `SESSION_NOT_FOUND` | Chat session not found |
| `SESSION_OWNERSHIP_ERROR` | Session does not belong to user |

### Clustering Error Codes

| Error Code | Description |
|------------|-------------|
| `CLUSTERING_SERVICE_UNAVAILABLE` | ml-clustering service unavailable |
| `CLUSTER_NOT_FOUND` | Cluster not found |
| `EMPTY_TECH_NAMES` | Technology names list is empty |

### Notification Error Codes

| Error Code | Description |
|------------|-------------|
| `NOTIFICATION_NOT_FOUND` | Notification not found |
| `NOTIFICATION_OWNERSHIP_ERROR` | Notification does not belong to user |

### Messaging Error Codes (NEW)

| Error Code | Description |
|------------|-------------|
| `INVALID_CONVERSATION` | Cố gắng nhắn tin cho chính mình |
| `INVALID_CONTENT` | `content` rỗng (và không có `attachment`), `content` > 2000 ký tự, hoặc `emoji` reaction không nằm trong 6 giá trị cho phép |
| `INVALID_ATTACHMENT` | File đính kèm (`POST /conversations/{id}/messages`) rỗng/base64 không hợp lệ, > 10MB sau khi decode, hoặc `content_type` không nằm trong allowlist |

### Social Error Codes (NEW)

| Error Code | Description |
|------------|-------------|
| `INVALID_FOLLOW` | Cố gắng follow chính mình |
| `INVALID_REASON` | Report thiếu `reason` hoặc `reason` > 500 ký tự |

---

## Rate Limiting

Chỉ 3 nhóm endpoint có rate limit thật (Redis INCR+EXPIRE, `shared/redis/*RateLimiterService`) —
`/graph/**` và `/clustering/**` KHÔNG có rate limit riêng nào:

- **Auth** (`/auth/login`, `/auth/register`, `/auth/forgot-password`): theo IP, mỗi action một
  bộ đếm riêng — login 10 req/60s, register 5 req/60s, forgot-password 5 req/300s (`AuthRateLimiterService`)
- **Chat** (`/chat/**`): 20 req/60s theo user (`ChatRateLimiterService`, `CHAT_RATE_LIMIT_MAX`/`_WINDOW`)
- **AiProxy** (`/career`, `/recommend`, `/interview`, `/agent`, `/forecast`, `/report`,
  `/chat/summarize`, `/company-insight`): 20 req/60s — theo user id nếu route yêu cầu đăng nhập,
  theo IP nếu route public (`AiProxyRateLimiterService`, `AIPROXY_RATE_LIMIT_MAX`/`_WINDOW`)

Khi rate limit exceeded:
```json
{
  "success": false,
  "data": null,
  "message": "Rate limit exceeded. Please try again later.",
  "error_code": "RATE_LIMIT_EXCEEDED",
  "timestamp": 1719792000000
}
```

HTTP Status: `429 Too Many Requests`

---

## Webhooks (Future)

Tương lai sẽ hỗ trợ webhooks cho:
- Trend alerts
- User events (registration, subscription changes)
- System events

---

## Changelog

### v1.1 (Current)
- **Mới:** Salary insights (`/salary/top`, `/salary/tech/{techName}`)
- **Mới:** Company Explorer (`/companies`, `/companies/{id}/similar`)
- **Mới:** Job Matching (`/jobs/matches`)
- **Mới:** Direct Messaging (`/conversations/**`, SSE)
- **Mới:** Social Feed (`/feed`, `/posts/**`, `/users/**` follow/suggested/profile-summary)
- **Mới:** AI Interview (`/interview`)
- **Refactor:** 6 module proxy Python riêng biệt (career/forecast/recommend/report/summarize/agent)
  được gộp thành module gateway `aiproxy` dùng chung một client — không đổi hợp đồng API phía
  client, chỉ đổi cách gateway implement (xem `docs/BACKEND_GUIDE.md` §4.16)
- Đồ thị (`/graph/filter`): thêm lọc theo sentiment band và khoảng lương (salary overlap)
- Bổ sung [`docs/DATABASE.md`](./DATABASE.md) làm tài liệu CSDL riêng (Postgres/Neo4j/Redis)
- **Mới:** Content moderation — user report (`POST /posts/{id}/report`, `POST /comments/{id}/report`) + admin moderation queue (`/admin/posts/**`, `/admin/comments/{id}`, `/admin/reports/**`)
- **Mới:** Admin Dashboard mở rộng — `/admin/dashboard/social|jobs|pipeline|messaging`
- **Mới:** Cache Admin — `/admin/cache/companies/evict`, `/admin/cache/jobs/evict` (company/job dùng Redis look-aside cache từ bản này)
- **Mới:** Notification-on-action — thích/bình luận/follow/nhắn tin giờ tạo notification (`POST_LIKE`/`POST_COMMENT`/`NEW_FOLLOWER`/`NEW_MESSAGE`), cộng với `JOB_MATCH` (job mới khớp kỹ năng hồ sơ, qua Kafka `job.match.alerts`)
- **Sửa:** Messaging/Notification SSE giờ fan-out qua Redis Pub/Sub (`live:messages`/`live:notifications`) thay vì in-memory single-instance — hoạt động đúng khi backend chạy nhiều instance

### v1.0
- Initial API release
- Auth, User, Radar, Compare, Graph, Chat, Clustering, Notifications, Admin endpoints
- JWT authentication
- SSE streaming cho chat và notifications
- Proxy đến Python AI services

---

## Support

Nếu bạn gặp vấn đề với API:

1. Kiểm tra HTTP status code và error message
2. Xem Error Codes section để hiểu lỗi
3. Kiểm tra `/health` endpoint để xác nhận service status
4. Xem Swagger UI tại `/swagger-ui.html` để test API trực tiếp
5. Mở issue trên GitHub repository

---

## Examples

### Complete Flow: Login → Get Profile → Create Chat Session

```bash
# 1. Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "SecurePass123!"}'

# Response: { "access_token": "...", "refresh_token": "...", ... }

# 2. Get profile
curl -X GET http://localhost:8080/api/v1/user/profile \
  -H "Authorization: Bearer <access_token>"

# 3. Create chat session
curl -X POST http://localhost:8080/api/v1/chat/session \
  -H "Authorization: Bearer <access_token>"

# Response: { "session_id": "...", ... }

# 4. Send message
curl -X POST http://localhost:8080/api/v1/chat/session/<session_id>/messages \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"query": "What are the top technologies in Vietnam?"}'
```
