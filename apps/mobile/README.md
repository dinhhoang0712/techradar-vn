# TechRadar VN — Mobile

Ứng dụng di động (Expo / React Native, file-based routing) cho nền tảng TechRadar VN:
Trend Radar, Knowledge Graph, Compare và Graph RAG Chat.

## Phát triển

```bash
npm install
npx expo start        # mở trên Android emulator / iOS simulator / Expo Go
```

Code chính nằm trong thư mục `app/` (Expo Router).

## Kết nối API

- Gọi qua Spring gateway `/api/v1` — đặt base URL backend trong cấu hình môi trường của app
  (vd `http://<LAN_IP>:8080/api/v1` khi test trên thiết bị thật).
- API client refresh access token khi gặp 401 (xem [utils/apiClient.js](utils/apiClient.js)).
- Dữ liệu theo **snake_case**; response bọc `ApiResponse{success, data, message}` trừ auth & `/status`
  (object thuần). Chi tiết: [docs/API_DOCs_v1.md](../../docs/API_DOCs_v1.md).

> Lưu ý: upload avatar trên mobile cần `expo-image-picker` (chưa tích hợp).
