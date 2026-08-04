# ADR-0006: RBAC theo permission code (không hard-code role)

**Status**: Accepted

## Context

Trước V24, phân quyền admin chỉ dựa vào `users.role = 'ADMIN'` kiểm tra rải rác trong code
(`hasRole("ADMIN")` ở nhiều controller). Thêm 1 role hẹp hơn (vd. `moderator` — chỉ xử lý report,
không được quản lý user) buộc phải sửa từng điểm check role, dễ bỏ sót và không có cách nào audit
"role nào có quyền gì" ngoài việc đọc hết code.

## Decision

V24 thêm bảng `roles`/`permissions`/`role_permissions` — quyền hạn được cấp theo **permission
code** (vd. `social:moderate`, `audit:view`, `kg:review`, `graph:manage`), không hard-code theo
tên role trong code Java. `users.role` là FK vào `roles(code)`. Thêm role mới (V25: `moderator`,
chỉ có `social:moderate` + `audit:view`) là thao tác **dữ liệu** (insert vào `role_permissions`),
không phải sửa code.

Kèm theo: `users.security_stamp` — đổi role/khoá tài khoản vô hiệu hoá JWT đang có ngay lập tức,
không cần chờ token hết hạn (xem `ARCHITECTURE.md` §10.4).

## Consequences

- **Được**: thêm role/quyền mới không đụng code; audit "ai có quyền gì" là 1 câu SQL join thay vì
  đọc toàn bộ controller. V25 (thêm `moderator`) chứng minh đúng việc này — 0 dòng code Java đổi.
- **Đánh đổi**: mọi endpoint admin mới phải nhớ khai permission code tương ứng (V27 `graph:manage`,
  V30 `kg:review`) — quên bước này nghĩa là endpoint mới hoặc mở cho mọi authenticated user (thiếu
  check) hoặc không ai gọi được (permission chưa cấp cho role nào).
- Không có UI quản lý role/permission runtime (chỉ Flyway migration) — chấp nhận được ở quy mô
  hiện tại (permission set thay đổi chậm, theo release), không phải multi-tenant SaaS cần admin
  tự tạo role tuỳ ý.
