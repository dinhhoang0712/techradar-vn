# ADR-0009: `ConversationParticipants` — value object cho canonical pair

**Status**: Accepted

## Context

`conversation` (V9) có `CHECK (user_a_id < user_b_id)` + `UNIQUE(user_a_id, user_b_id)` để tránh
tạo 2 conversation cho cùng 1 cặp user theo 2 thứ tự gọi khác nhau (A nhắn B trước, hay B nhắn A
trước, phải cùng ra 1 conversation). Trước ADR này, logic "sắp xếp 2 UUID theo canonical order"
nằm thẳng trong `PostgresConversationRepository.findOrCreate` — không có khái niệm domain nào để
tái dùng logic đó, dù nó có 1 chi tiết khá tinh vi:

> Postgres so sánh giá trị `uuid` theo byte (unsigned), khác với `java.util.UUID#compareTo` (so
> sánh signed long trên các byte cao nhất) mỗi khi byte cao nhất của 2 UUID khác nhau ở sign bit.

Ví dụ thật (xem `ConversationParticipantsTest.of_ordersByCanonicalStringForm_notJavaUuidCompareTo`):
UUID bắt đầu bằng `80...` có `mostSigBits` âm khi coi là signed long, khiến
`java.util.UUID#compareTo` xếp nó NHỎ HƠN một UUID bắt đầu bằng `70...` — ngược với thứ tự
string/byte thông thường mà Postgres dùng. Nếu ai đó "đơn giản hoá" bằng `compareTo()` thay vì so
sánh chuỗi canonical, code vẫn compile, có vẻ chạy đúng với hầu hết UUID ngẫu nhiên, nhưng âm thầm
vi phạm `CHECK` constraint cho đúng những cặp UUID rơi vào trường hợp lệch dấu này.

Tại thời điểm viết ADR này chỉ có 1 chỗ gọi (`GetOrCreateConversationUseCase`), nên chưa có
duplication thật — nhưng logic tricky này "mắc kẹt" trong tầng adapter, không phải 1 khái niệm
domain tái dùng được. Thêm 1 chỗ tạo conversation nữa trong tương lai (vd. tool admin) rất dễ viết
lại sai chi tiết byte-order này từ đầu.

## Decision

Tạo `ConversationParticipants` (record, `features/messaging/domain/`) với factory
`ConversationParticipants.of(userX, userY)`:
- Trả về `(userA, userB)` đã sắp xếp theo canonical string form (khớp Postgres), bất kể thứ tự
  tham số đầu vào.
- Ném `IllegalArgumentException` nếu `userX.equals(userY)` — defense-in-depth ở tầng domain, KHÔNG
  thay thế check hiện có ở `GetOrCreateConversationUseCase` (vẫn ném `BadRequestException`
  user-facing trước khi chạm domain). Value object crash cứng nếu invariant bị vi phạm là hành vi
  đúng cho 1 constructor assertion — còn use case vẫn là nơi dịch sang lỗi HTTP thân thiện.

`PostgresConversationRepository.findOrCreate` giờ chỉ gọi `ConversationParticipants.of(userX,
userY)` rồi bind `participants.userA()`/`participants.userB()` — không còn tự viết lại phép so
sánh chuỗi.

## Consequences

- **Được**: logic canonical-ordering giờ test được độc lập (`ConversationParticipantsTest`),
  không cần mock `DatabaseClient`. Chỗ gọi tương lai (nếu có) chỉ cần gọi `.of(...)`, không thể vô
  tình viết lại sai bằng `compareTo()`.
- **Đánh đổi**: thêm 1 file domain nhỏ cho 1 hành vi duy nhất (sort 2 UUID) — chấp nhận được vì
  đây đúng là loại logic "nhỏ nhưng dễ sai lại" mà tách riêng ra mới đáng.
- Không tạo aggregate `Conversation` đầy đủ (với `id`, participants, timestamps) vì repository
  hiện tại không có nhu cầu load cả entity vào memory để chạy business logic — chỉ cần đúng 1
  hành vi (canonical pair), nên value object nhỏ là đủ, không over-engineer thành aggregate không
  ai cần.
