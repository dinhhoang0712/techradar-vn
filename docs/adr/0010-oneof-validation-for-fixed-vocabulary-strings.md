# ADR-0010: `@OneOf` validation cho string thuộc tập giá trị cố định

**Status**: Accepted

## Context

Audit validation phát hiện `users.status`, `users.subscription_tier`, `cms_content.status`,
`cms_content.type` là các field mang ý nghĩa "enum" (tập giá trị cố định, ghi trong comment) nhưng
**không được validate ở bất kỳ tầng nào** — không Bean Validation, không DB `CHECK`. Admin (và với
`subscriptionTier`, cả user tự đăng ký) có thể set bất kỳ string nào; code đọc lại dùng
`equalsIgnoreCase` nên gõ sai chính tả sẽ âm thầm sai logic thay vì báo lỗi.

Thêm vấn đề phát hiện khi audit: `docs/API_DOCs_v1.md` tài liệu hoá `users.status` là
`ACTIVE`/`INACTIVE`/`SUSPENDED` (viết hoa), trong khi code/test thực tế dùng `active`/`banned`
(viết thường) — 2 nguồn "sự thật" khác nhau, không cái nào được enforce. Quyết định: **coi tài
liệu API là hợp đồng đúng**, sửa code theo hướng viết hoa (không sửa tài liệu).

## Decision

1. **`shared/validation/OneOf`** — custom Bean Validation annotation (`shared/validation/`, vì
   dùng chung ≥4 field khác nhau ở 2 feature khác nhau — đúng tinh thần ADR-0001 cho `shared/`).
   Tham số là **1 enum**, không phải mảng string lặp lại ở từng field: `@OneOf(UserStatus.class)`
   thay vì `@OneOf({"ACTIVE","INACTIVE","SUSPENDED"})` nhắc lại y hệt ở cả `CreateUserRequest` và
   `UpdateUserRequest` (2 lần) và cả `RegisterRequest`/`CreateUserRequest`/`UpdateUserRequest` cho
   `SubscriptionTier` (3 lần). Java không cho tham chiếu 1 mảng `static final` dùng chung trong
   annotation (không phải compile-time constant), nên enum là cách duy nhất thật sự gom được 1
   nguồn duy nhất — validator đọc `enumClass.getEnumConstants()` qua `Enum#name()`. Case-sensitive
   (khớp chính xác `name()`) để một request pass Bean Validation thì chắc chắn cũng pass DB `CHECK`
   tương ứng, không có tầng nào lỏng hơn tầng kia gây khó hiểu.
2. **`UserStatus`** (`ACTIVE`/`INACTIVE`/`SUSPENDED`) và **`SubscriptionTier`**
   (`FREE`/`PRO`/`ENTERPRISE`) — enum đặt tại `features/auth/domain/` (cạnh `User`, entity sở hữu
   2 field này). Toàn bộ literal `"active"`/`"free"` trong code (`RegisterUseCase`,
   `AdminUserService`, `PostgresUserRepository`, `User.isActive()`, `SendAdminNotificationUseCase`)
   đổi sang viết hoa khớp `docs/API_DOCs_v1.md`.
3. **`CmsStatus`** (`Published`/`Analyzed`/`Pending`/`Archived`) và **`CmsType`**
   (`Report`/`Job`/`Keyword`) — enum tại `features/system/domain/` (cạnh `CmsContent`). Tên hằng số
   giữ nguyên Title-Case (lệch convention `UPPER_SNAKE_CASE` thông thường của Java enum) — có chủ
   đích: 2 enum này chỉ tồn tại để làm nguồn vocabulary cho `@OneOf` (không dùng làm kiểu field
   thật ở đâu khác), nên khớp thẳng string thật đang dùng khắp code thay vì thêm 1 tầng map
   label→value không cần thiết.
4. **Migration V37**: backfill `UPPER(status)`/`UPPER(subscription_tier)` cho data cũ (bắt buộc
   TRƯỚC khi thêm CHECK, nếu không migration tự fail trên chính data đang có), đổi `DEFAULT`, rồi
   thêm `CHECK` cho cả 4 field — CHECK là backstop cuối cùng dù Bean Validation có bị bỏ qua ở đâu
   đó (vd. gọi thẳng use case trong 1 job nội bộ, không qua controller).
5. **`GlobalExceptionHandler`**: thêm handler cho `DataIntegrityViolationException` (Spring tự
   dịch từ lỗi driver R2DBC khi vi phạm CHECK/UNIQUE/FK) → 409 thay vì rơi vào catch-all 500 — nếu
   không, CHECK constraint mới thêm sẽ "hoạt động" nhưng trả lỗi khó hiểu cho client.
6. `AdminCmsController.create/update` trước đây **thiếu `@Valid` hoàn toàn** — thêm luôn (không
   chỉ thêm annotation `@OneOf` mà không kích hoạt được).

## Consequences

- **Được**: 4 field trước đây "ai cũng có thể set bậy" giờ bị chặn ngay ở tầng request (400 rõ
  ràng field nào sai) THAY VÌ chỉ phát hiện khi đọc lại dữ liệu và logic so sánh case-insensitive
  âm thầm sai. DB CHECK là lưới an toàn cuối nếu có code path nào bỏ qua Bean Validation.
- **Đánh đổi**: đổi vocabulary `status`/`subscription_tier` sang viết hoa là **breaking change**
  cho bất kỳ client nào đang gửi `"active"`/`"free"` viết thường — chấp nhận được vì đây là admin
  API nội bộ (không phải API public rộng), và test đã cập nhật theo. Nếu FE/mobile có gọi trực
  tiếp field này, cần rà soát riêng (ngoài phạm vi ADR này — chỉ đổi phía backend).
- `@OneOf` case-sensitive khác với convention "case-insensitive" đã dùng cho `role`
  (`normalizeRole()` tự lowercase) — có chủ đích khác nhau: `role` có sẵn 1 tầng "normalize" qua
  use case trước khi validate; 4 field mới không có tầng đó, nên chọn strict ngay từ Bean
  Validation để không cần thêm bước normalize.
- Thêm feature mới cần 1 vocabulary cố định tương tự → tạo 1 enum cạnh domain entity sở hữu field
  đó rồi `@OneOf(TheEnum.class)`, không tự viết `String[]` inline nữa (annotation không còn hỗ trợ
  dạng đó).
