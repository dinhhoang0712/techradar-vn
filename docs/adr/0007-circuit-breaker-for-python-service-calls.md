# ADR-0007: Circuit breaker cho các lệnh gọi Python service

**Status**: Accepted

## Context

`ARCHITECTURE.md` từng liệt kê "Resilience: Resilience4j (Circuit Breaker)" trong bảng tech
stack backend, nhưng thực tế `pom.xml` **chưa từng có** dependency Resilience4j — không chỉ là
"có dependency nhưng chưa dùng", mà tài liệu mô tả một khả năng chưa từng tồn tại trong code.

Trong khi đó, 5 client (`PythonChatClient`, `PythonAiClient`, `PythonAiProxyClient`,
`PythonModerationClient` — cả 4 gọi `ai-rag-core`; `PythonClusteringClient` — gọi
`ml-clustering`) đều kế thừa `AbstractPythonServiceClient`, vốn đã có retry + timeout +
error-mapping, nhưng không có cơ chế nào ngăn request tiếp tục dội vào một service Python đã biết
là đang down — mỗi request vẫn chờ hết `timeout` (tới 120s cho `/chat`) trước khi thất bại, dù
service đó đã fail liên tục 50 lần trước đó.

## Decision

Thêm circuit breaker thật (Resilience4j `resilience4j-reactor`, không phải chỉ annotation) vào
đúng điểm chung mà mọi client Python đã đi qua: `AbstractPythonServiceClient.mapMono`/`mapFlux`.
`CircuitBreakerOperator` bọc pipeline retry+timeout đã có sẵn, nên:

- Circuit breaker ghi nhận đúng 1 kết quả (thành công/thất bại) cho mỗi lệnh gọi từ góc nhìn của
  use case — không phải 1 kết quả cho mỗi lần retry.
- Timeout cũng được circuit breaker tính là thất bại (nó subscribe vào pipeline đã có `.timeout()`).

**Một circuit breaker instance cho mỗi SERVICE đích, không phải mỗi client class**:
`aiRagCoreCircuitBreaker` dùng chung bởi cả 4 client gọi `ai-rag-core`,
`mlClusteringCircuitBreaker` riêng cho `ml-clustering` (`Resilience4jConfig`). Lý do: nếu
`ai-rag-core` thật sự down, tất cả 4 client gọi nó phải cùng "biết" điều đó và cùng mở circuit —
tách theo class sẽ khiến mỗi client tự học lại tình trạng downtime của cùng 1 service, chậm hơn
và tốn thêm request thật vô ích trong lúc "học" riêng.

`CallNotPermittedException` (circuit đang mở, request bị từ chối không thử) được bắt riêng và map
thành `DatabaseUnavailableException` với message phân biệt rõ "circuit breaker open" — khác với
lỗi mạng/timeout thật, để log/observability phân biệt được 2 tình huống.

Cấu hình (`application.yml` §resilience4j.circuitbreaker.instances`): sliding window 20 lệnh gọi
gần nhất, cần tối thiểu 10 lệnh gọi mới tính tỷ lệ lỗi, mở circuit khi ≥50% lỗi, half-open thử lại
sau 30s với tối đa 5 lệnh gọi thử nghiệm.

## Consequences

- **Được**: khi `ai-rag-core`/`ml-clustering` thật sự down, request tiếp theo fail **ngay lập
  tức** (không chờ timeout 60-120s) — trải nghiệm người dùng tệ hơn "trả lời chậm", nhưng tốt hơn
  nhiều so với "treo cả phút rồi mới báo lỗi". `management.health.circuitbreakers.enabled=true`
  cũng phơi trạng thái circuit qua `/actuator/health` để giám sát.
- **Đánh đổi**: cần tinh chỉnh ngưỡng (`failure-rate-threshold`, `wait-duration-in-open-state`)
  theo dữ liệu thật khi vận hành — số hiện tại (50%/30s) là điểm khởi đầu hợp lý, chưa phải số đã
  qua kiểm chứng production.
- Thêm 1 tham số bắt buộc (`CircuitBreaker`) vào mọi lời gọi `mapMono`/`mapFlux` — 5 class con đều
  phải inject đúng bean qua `@Qualifier` (`aiRagCoreCircuitBreaker`/`mlClusteringCircuitBreaker`);
  thêm 1 client Python service mới bắt buộc phải quyết định dùng breaker nào (thường là tạo bean
  mới trong `Resilience4jConfig` nếu là service thứ 3), không được bỏ qua tham số này.
