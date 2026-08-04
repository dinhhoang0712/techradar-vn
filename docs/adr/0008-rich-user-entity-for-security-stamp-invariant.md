# ADR-0008: Rich `User` entity cho invariant `securityStamp`

**Status**: Accepted

## Context

`User` (`features/auth/domain/User.java`) trước đây là 1 Lombok `@Data` thuần — getter/setter
public trên mọi field, không có behavior thật. Field `securityStamp` mang 1 invariant có ý nghĩa
bảo mật thật: "đổi password/role/status PHẢI đồng thời rotate `securityStamp`, để JWT đã phát
hành trước đó bị vô hiệu hoá ngay" (`SecurityStampService` so khớp stamp trong JWT với stamp hiện
tại của user mỗi request).

Trước ADR này, invariant đó chỉ tồn tại dưới dạng **quy ước lặp lại thủ công** ở 3 nơi:
- `ResetPasswordUseCase.execute()`: `setPasswordHash(...)` + `setSecurityStamp(UUID.randomUUID())`.
- `AdminUserService.alterUser()`: tự tính 1 biến `revokesExistingTokens` gộp từ 3 field khác nhau
  (password/status/role) rồi mới quyết định bump stamp.
- `ProfileService.applyAccountChanges()`: tính `emailChanging || passwordChanging` rồi bump.

Không có gì trong compiler hay runtime ngăn một điểm gọi tương lai set thẳng
`user.setRole(...)`/`setStatus(...)`/`setPasswordHash(...)` qua Lombok setter mà quên bump stamp —
hệ quả là 1 bug bảo mật âm thầm (đổi role xong nhưng JWT cũ vẫn dùng được).

## Decision

Đóng gói invariant vào chính `User`, không còn public setter cho `passwordHash`/`role`/`status`/
`securityStamp` (`@Setter(AccessLevel.NONE)` từng field, giữ `@Setter` mặc định cho các field
không mang invariant: `id`, `email`, `fullName`, `subscriptionTier`, `createdAt`, `updatedAt`).
Thay vào đó:

- `changePassword(hash)` — **luôn** rotate stamp (đổi password luôn phải revoke session khác).
- `changeRole(role)` / `changeStatus(status)` — rotate stamp **chỉ khi giá trị thực sự đổi**
  (no-op nếu gọi với giá trị y hệt hiện tại — khớp hành vi cũ ở `AdminUserService`).
- `rotateSecurityStamp()` — escape hatch public cho policy KHÔNG phải invariant phổ quát của
  entity nhưng vẫn cần force re-auth: `ProfileService` gọi thẳng method này khi user tự đổi email
  (đổi định danh khôi phục tài khoản), vì bản thân việc đổi email không kéo theo rotate ở tầng
  entity — `AdminUserService` đổi email hộ user thì KHÔNG rotate (bất đối xứng có chủ đích, xem
  Consequences).
- `ensureSecurityStamp()` — gán stamp lần đầu cho user mới tạo chưa có stamp nào (chỉ
  `PostgresUserRepository.insert()` gọi).

`AdminUserService.alterUser()` không còn tự tính boolean `revokesExistingTokens` thủ công nữa —
chỉ snapshot `securityStamp` TRƯỚC mọi mutation, rồi so sánh với giá trị SAU khi gọi các method
trên; khác nhau tức là có rotate, propagate qua `SecurityStampService.set(...)`. Cách này tự động
đúng dù rotate đến từ role, status, hay password, không cần cộng dồn cờ theo từng field.

## Vì sao email đổi bởi admin KHÔNG rotate stamp (bất đối xứng có chủ đích)

Hành vi này đã tồn tại trước ADR này — không phải thứ ADR tạo mới, chỉ giữ nguyên khi refactor.
Lý do hợp lý: admin đã là actor có quyền cao, hành động đã qua RBAC + audit log riêng; buộc revoke
mọi session khác của USER đó chỉ vì admin sửa email hộ là một chính sách, không phải bất biến của
riêng `User`. Tự đổi email (qua `ProfileService`) lại khác — đó là tín hiệu "chủ tài khoản tự thay
đổi định danh khôi phục", nên `ProfileService` chủ động gọi `rotateSecurityStamp()` thêm.

## Consequences

- **Được**: không còn cách nào mutate `passwordHash`/`role`/`status` mà quên rotate stamp — compiler
  chặn từ gốc (không có setter để gọi nhầm). `UserTest` pin trực tiếp hành vi rotate/no-op của
  từng method trên entity, độc lập với 3 use case gọi nó.
- **Đánh đổi**: `User` không còn là Lombok `@Data` thuần — thêm ít nhất 5 method có nghĩa (thay vì
  0), người đọc lần đầu cần biết field nào có method riêng (đọc Javadoc lớp là đủ, đã ghi rõ).
- Field/invariant tương lai theo cùng dạng ("đổi X phải kéo theo Y") nên đi theo pattern này —
  method có tên nghiệp vụ, không phải setter trần — thay vì lặp lại quy ước rải rác như trước.
