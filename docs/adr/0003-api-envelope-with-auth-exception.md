# ADR-0003: Envelope `ApiResponse` snake_case, ngoại lệ có chủ đích cho `/auth/*` và `/status`

**Status**: Accepted

## Context

Toàn bộ DTO được Jackson serialize `snake_case`
(`spring.jackson.property-naming-strategy: SNAKE_CASE`, xem
[`DATABASE.md` §6](../DATABASE.md#6-quy-ước--gotchas-cross-service)), và phần lớn endpoint bọc
response trong `ApiResponse<T>` (`{success, data, message}` hoặc `{success, error, error_code}`)
để client có 1 chỗ duy nhất kiểm tra thành công/thất bại.

`AuthController` (`/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/me`) và
`StatusController` (`/status`) lại trả **object trần**, không bọc `ApiResponse`. Đây từng bị coi
là "inconsistency cần fix" khi đọc code lần đầu — nhưng đọc kỹ thì đây là quyết định có chủ đích,
đã ghi chú ngay trong code (`AuthController.java`, comment ngay trên các endpoint):

> auth + /status responses are returned BARE (no ApiResponse envelope) because the web/mobile
> clients read these fields at the top level (e.g. `res.access_token`, `user.role`).

## Decision

Giữ nguyên 2 nhóm response:

1. **Bọc `ApiResponse`** — mọi endpoint nghiệp vụ khác (`/radar/*`, `/chat/*`, `/companies/*`,
   `/jobs/*`, ...). Endpoint mới **mặc định phải theo nhóm này**.
2. **Trả trần (bare object)** — chỉ `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/me`,
   `/status`, vì FE web (`apiClient.js`, `authService.js`) và mobile đọc trực tiếp
   `res.access_token`/`user.role` ở top level, không qua `res.data`. Đổi sang bọc envelope ở
   nhóm này là **breaking change cho mọi client đang chạy**, không phải "sửa lỗi".

Ngoại lệ này **không được mở rộng** cho endpoint mới ngoài 2 controller trên. Nếu một endpoint
`/auth/*` mới thấy cần trả lỗi, vẫn dùng `ApiResponse.error(...)` cho error body (chỉ success
body mới trần) — giữ đúng pattern hiện có, không tự sáng tác quy ước thứ 3.

## Consequences

- **Được**: FE không phải viết `res?.data ?? res` cho auth — điểm vào ứng dụng (login/register)
  thường là code path nhạy cảm nhất với thay đổi contract.
- **Đánh đổi**: người đọc API lần đầu (kể cả LLM review code) dễ tưởng đây là bug nếu không đọc
  comment tại chỗ — ADR này + comment trong code là 2 nơi cùng giải thích, nên giữ đồng bộ nếu
  một trong hai đổi.
- Swagger UI (springdoc) nên annotate rõ 2 response shape khác nhau cho auth endpoint (thay vì
  chỉ dựa vào comment Java không hiện ra ở UI) — xem việc làm giàu annotation ở
  `AuthController`/`API_DOCs_v1.md`.
