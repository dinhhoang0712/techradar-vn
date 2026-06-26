# TechRadar VN — Web

Single-page app (React 19 + Vite) cho nền tảng TechRadar VN: Trend Radar, Knowledge Graph Explorer,
Compare, Graph RAG Chat và trang quản trị (Admin).

## Phát triển

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # build production vào dist/
npm run preview    # xem thử bản build
```

## Kết nối API

- API client gọi **đường dẫn tương đối** `/api/v1` (xem [src/utils/apiClient.js](src/utils/apiClient.js)),
  nên không cần đặt URL backend lúc build.
- **Khi chạy dev**: cấu hình proxy của Vite (hoặc chạy backend cùng origin) để `/api` trỏ tới `http://localhost:8080`.
- **Khi chạy Docker**: Nginx ([nginx.conf](nginx.conf)) proxy `location /api` → `spring-api:8080`,
  đồng thời tắt buffering để **SSE** chat stream hoạt động.
- `apiClient` tự động refresh access token khi gặp 401 (single-flight) và gắn `Authorization: Bearer`.

## Hợp đồng dữ liệu

- Toàn bộ field theo **snake_case**.
- Response thường bọc `ApiResponse{success, data, message}`; riêng auth (`/auth/login|register|refresh|me`)
  và `/status` trả **object thuần**. Client đọc theo `res?.data ?? res`.

Chi tiết endpoint: [docs/API_DOCs_v1.md](../../docs/API_DOCs_v1.md).

## Docker

Image production (multi-stage: build Vite → serve bằng Nginx) được dựng tự động bởi compose ở repo root:

```bash
docker compose up --build web      # http://localhost:5173
```
