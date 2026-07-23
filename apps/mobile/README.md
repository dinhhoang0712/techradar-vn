# TechRadar VN — Mobile

Ứng dụng di động (Expo / React Native, file-based routing) cho nền tảng TechRadar VN. **Không
phải bản scaffold** — đã có 15 màn hình thật (~5.600 dòng) nối tới 13 API service module: Auth,
Trend Radar, Knowledge Graph, Compare, Graph RAG Chat, Career, Salary, Report, Notifications,
Cluster, User/Profile.

## Phát triển

```bash
npm install
npx expo start        # mở trên Android emulator / iOS simulator / Expo Go
```

Code chính nằm trong thư mục `app/` (Expo Router).

## Kết nối API

- Gọi qua Spring gateway `/api/v1` — **hiện đang hard-code** hằng số
  `API_BASE_URL = 'https://datamining.ankkun.space/api/v1'` thẳng trong
  [utils/apiClient.js](utils/apiClient.js) (không đọc biến môi trường/`app.json extra` nào). Muốn
  trỏ về backend khác (vd LAN IP khi test build dev) phải sửa trực tiếp hằng số này trong code.
- API client refresh access token khi gặp 401 (cùng file trên).
- Dữ liệu theo **snake_case**; response bọc `ApiResponse{success, data, message}` trừ auth & `/status`
  (object thuần). Chi tiết: [docs/API_DOCs_v1.md](../../docs/API_DOCs_v1.md).

> Lưu ý: upload avatar trên mobile cần `expo-image-picker` (chưa tích hợp).
