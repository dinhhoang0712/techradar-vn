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
12. [Phân quyền](#phân-quyền)
13. [Proxy sang Python](#proxy-sang-python)
14. [Error Codes](#error-codes)

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
  "min_salary": 1000,
  "max_salary": 5000,
  "sentiment": "positive"
}
```

**Fields:**
- `locations`: Danh sách địa điểm để lọc (tùy chọn)
- `node_types`: Danh sách loại node để lọc (tùy chọn)
- `min_salary`: Mức lương tối thiểu (tùy chọn)
- `max_salary`: Mức lương tối đa (tùy chọn)
- `sentiment`: Sentiment filter cho articles (`positive`, `negative`, `neutral`) (tùy chọn)

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
- `type`: Loại notification (`trend_alert`, `system`, `career`, etc.)
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

**Notification Sources:**
- **Trend Alert**: ETL radar phát event `trend.alerts` lên Kafka khi một công nghệ tăng ≥ threshold (mặc định 20%) MoM. `TrendAlertDispatcher` fan-out tới user có công nghệ đó trong `user_profile.technologies` (kênh in-app + email theo `notify_inapp`/`notify_email`).
- **System**: Notifications từ hệ thống (welcome, maintenance, etc.)
- **Career**: Notifications liên quan đến career path

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

### CMS Management

#### GET `/admin/cms`

Lấy danh sách nội dung CMS.

**Authentication:** Admin role required

**Query Parameters:**
- `page`: Số trang (mặc định: 0)
- `size`: Số item mỗi trang (mặc định: 20)

**Response:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "770e8400-e29b-41d4-a716-446655440000",
        "title": "Technology Trends Report Q2 2024",
        "type": "REPORT",
        "content": "Full report content...",
        "content_date": "2024-06-30",
        "status": "PUBLISHED",
        "created_at": "2024-06-30T10:00:00Z"
      }
    ],
    "total_elements": 25,
    "total_pages": 2,
    "current_page": 0,
    "size": 20
  },
  "message": null,
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### POST `/admin/cms`

Tạo nội dung CMS mới.

**Authentication:** Admin role required

**Request Body:**
```json
{
  "title": "Technology Trends Report Q3 2024",
  "type": "REPORT",
  "content": "Full report content...",
  "content_date": "2024-09-30",
  "status": "DRAFT"
}
```

**Fields:**
- `title` (required): Tiêu đề
- `type` (optional): Loại nội dung (`REPORT`, `ARTICLE`, `NEWS`), mặc định `ARTICLE`
- `content` (required): Nội dung
- `content_date` (optional): Ngày của nội dung
- `status` (optional): Status (`DRAFT`, `PUBLISHED`, `ARCHIVED`), mặc định `DRAFT`

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "880e8400-e29b-41d4-a716-446655440000",
    "title": "Technology Trends Report Q3 2024",
    "type": "REPORT",
    "content": "Full report content...",
    "content_date": "2024-09-30",
    "status": "DRAFT",
    "created_at": "2024-07-01T10:00:00Z"
  },
  "message": "Content created successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### PUT `/admin/cms/{id}`

Cập nhật nội dung CMS.

**Authentication:** Admin role required

**Request Body:** (tất cả fields optional)
```json
{
  "title": "Updated Title",
  "content": "Updated content...",
  "status": "PUBLISHED"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "id": "880e8400-e29b-41d4-a716-446655440000",
    "title": "Updated Title",
    "type": "REPORT",
    "content": "Updated content...",
    "content_date": "2024-09-30",
    "status": "PUBLISHED",
    "created_at": "2024-07-01T10:00:00Z"
  },
  "message": "Content updated successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

---

#### DELETE `/admin/cms/{id}`

Xóa nội dung CMS.

**Authentication:** Admin role required

**Response:**
```json
{
  "success": true,
  "data": null,
  "message": "Content deleted successfully",
  "error_code": null,
  "timestamp": 1719792000000
}
```

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
- `/actuator/**`
- Swagger UI (`/swagger-ui/**`, `/v3/api-docs/**`)

### Admin Endpoints

Yêu cầu role `ADMIN`:

- Tất cả endpoints dưới `/admin/**`

### Authenticated Endpoints

Yêu cầu JWT hợp lệ:

- Tất cả endpoints còn lại không thuộc Public hoặc Admin

**Note:** `spring.webflux.base-path` bị strip **trước** security filter, nên matcher trong `SecurityConfig.PUBLIC_PATHS` được khai báo **không** kèm `/api/v1`.

---

## Proxy sang Python

Spring Boot gateway proxy các request đến Python services với header bảo mật.

### ai-rag-core Proxy

**Endpoints được proxy:**
- `/chat/**`
- `/compare/llm-summary`

**Configuration:**
- Environment variable: `PYTHON_RAG_BASE_URL` (mặc định: `http://ai-rag-core:8000`)
- Security header: `X-Internal-Auth: <INTERNAL_API_TOKEN>`
- Timeout: 120 seconds

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

---

## Rate Limiting

Các endpoint có thể bị rate limit để bảo vệ hệ thống:

- **Auth endpoints**: 10 requests/minute/IP
- **Chat endpoints**: 30 requests/minute/user
- **Graph endpoints**: 20 requests/minute/user
- **Clustering endpoints**: 10 requests/minute/user

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

### v1.0 (Current)
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
