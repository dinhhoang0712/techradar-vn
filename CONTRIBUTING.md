# Đóng góp cho TechRadar VN

Cảm ơn bạn đã quan tâm đóng góp cho dự án. File này tóm tắt quy trình và quy ước — với hướng dẫn
setup môi trường đầy đủ từng service, xem [`docs/DEVELOPMENT_GUIDE.md`](docs/DEVELOPMENT_GUIDE.md).

## Trước khi bắt đầu

Đọc [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) để nắm bức tranh tổng thể, và
[`docs/adr/`](docs/adr/) để biết những quyết định kiến trúc đã có hiệu lực — đặc biệt nếu một chỗ
trong code trông "không nhất quán" (vd. `/auth/*` không bọc `ApiResponse`), rất có thể đó là quyết
định có chủ đích chứ không phải bug, và đã có ADR giải thích lý do.

## Quy trình PR

1. Tạo nhánh từ `master`, đặt tên theo dạng `feature/...`, `fix/...`, `docs/...`.
2. Code + test. Yêu cầu tối thiểu trước khi mở PR:
   - **Backend (`apps/backend`)**: `mvn test` pass (unit + integration qua Testcontainers, không
     cần cài Postgres/Neo4j/Redis thủ công).
   - **Frontend (`apps/web`)**: `npm test` pass, `npm run lint` sạch.
   - **Python (`services/*`, `data-platform`)**: `pytest` pass, `ruff check`/`ruff format` sạch
     (CI chạy `ruff` chung cho toàn bộ Python trong repo).
3. Commit message: mô tả **vì sao** thay đổi (không chỉ **cái gì**) — xem `git log` để thấy văn
   phong đang dùng trong repo (tiếng Việt, giải thích motivation/root cause khi fix bug).
4. Nếu thay đổi một quyết định kiến trúc đã ghi trong `docs/adr/` (không chỉ implementation chi
   tiết), thêm một ADR mới đánh dấu ADR cũ là `Superseded by ADR-000X` — không sửa nội dung ADR cũ.

## Quy ước theo từng phần

### Backend (Java/Spring Boot)

Xem [`apps/backend/README.md`](apps/backend/README.md) cho quy ước chi tiết (envelope response,
RBAC theo permission code, R2DBC `bindNull`, transactional outbox). Tóm tắt nhanh:

- Feature mới phải theo cấu trúc hexagonal `domain/ports/application/adapters/{input,output}` —
  xem [ADR-0001](docs/adr/0001-hexagonal-feature-modules.md).
- Không dùng blocking I/O trong request path (WebFlux + R2DBC + Neo4j async driver xuyên suốt).
- Endpoint mới mặc định bọc `ApiResponse` — không tự thêm ngoại lệ "trả trần" ngoài
  `/auth/*`/`/status` đã có.

### Frontend (React/TypeScript)

- TypeScript strict (`allowJs: false`) — không thêm file `.js`/`.jsx` mới, không dùng `any` để né
  lỗi type.
- Component mới đặt trong `src/components/` (dùng lại nếu có thể) hoặc `src/pages/` (route-level).
- Chạy `npm run dev` và tự kiểm tra bằng tay trên trình duyệt với thay đổi UI trước khi mở PR —
  test tự động không thay thế việc xác nhận tính năng thật sự hoạt động.

### Python (ai-rag-core, ml-clustering, data-platform, crawler, embedding-service, qdrant-writer)

- Format bằng `ruff format`, lint bằng `ruff check` trước khi commit.
- Service mới hoặc thay đổi schema Postgres: nhớ cả 2 phía nếu Python đọc/ghi bảng Flyway quản lý
  — Flyway (`apps/backend`) là nguồn DUY NHẤT tạo/sửa DDL, xem
  [`docs/DATABASE.md`](docs/DATABASE.md) §2.

## Docs cần đồng bộ khi đổi behavior

Một số tài liệu mô tả hành vi runtime thực tế (không chỉ hướng dẫn) — cập nhật cùng lúc với code
nếu PR làm thay đổi những gì chúng mô tả:

| Thay đổi | Cập nhật |
|---|---|
| Schema Postgres (bảng/cột/index mới) | `apps/backend/src/main/resources/db/README.md` (nguồn sự thật DDL) + `docs/DATABASE.md` (bức tranh toàn hệ + ERD) |
| Node/relationship Neo4j mới, hoặc ai ghi/đọc gì | `docs/DATABASE.md` §4 |
| Luồng nghiệp vụ/pipeline mới hoặc đổi luồng cũ | `docs/ARCHITECTURE.md` (diagram Mermaid tương ứng) |
| Quyết định kiến trúc mới (không phải chi tiết implementation) | `docs/adr/` (ADR mới, đánh số tăng dần) |
| API endpoint mới | annotation springdoc (`@Operation`/`@Schema`) trên controller/DTO + `docs/API_DOCs_v1.md` nếu cần ví dụ request/response đầy đủ |

## Câu hỏi

Xem [`docs/README.md`](docs/README.md) để tra mục lục toàn bộ tài liệu, hoặc mở issue trên
GitHub.
