# ADR-0001: Hexagonal Architecture theo feature module

**Status**: Accepted

## Context

`apps/backend` là một Spring Boot monolith duy nhất (không phải nhiều microservice Go), nhưng
phục vụ 15+ domain khác biệt (auth, radar, chat, company, job, messaging, social, notification,
roadmap, salary, graph, clustering, aiproxy, kgreview, system). Không có ranh giới module nào thì
domain logic của các feature sẽ trộn lẫn qua thời gian, và test một feature buộc phải biết chi
tiết hạ tầng (Neo4j driver, R2DBC, WebClient) của feature đó.

## Decision

Mỗi feature là một module độc lập dưới `features/{name}/` theo đúng 1 cấu trúc:

```
features/{name}/
├── domain/       # Entity + business rule thuần, không phụ thuộc Spring/R2DBC/Neo4j driver
├── ports/         # Interface (input use case boundary + output repository/client boundary)
├── application/   # Use case (1 class = 1 hành vi nghiệp vụ, gọi qua port, không gọi thẳng adapter)
└── adapters/
    ├── input/     # REST controller — dịch HTTP ⇄ use case
    └── output/    # Repository/client implementation (Postgres/Neo4j/Redis/WebClient)
```

Không có `port/in|out` hay `adapter/in/web` lồng nhau — cấu trúc phẳng, tối đa 4 cấp.
`shared/` chỉ chứa hạ tầng dùng chung (`ApiResponse`, `ErrorCode`, Redis cache, Neo4j read
template) — không bao giờ chứa logic riêng của 1 feature.

## Consequences

- **Được**: domain logic của mỗi feature test được mà không cần Testcontainers (mock port);
  đổi hạ tầng (vd. đổi Neo4j query) chỉ chạm `adapters/output`, không lan sang `application`.
- **Đánh đổi**: nhiều feature nhỏ (`user`, `system`) có domain rất mỏng nhưng vẫn phải theo đủ 4
  thư mục — overhead cấu trúc chấp nhận được để giữ tính nhất quán toàn repo hơn là có ngoại lệ.
- Feature `aiproxy` là trường hợp đặc biệt: 6 module riêng biệt trước đây (mỗi module có
  `ModuleConfig` + `ServicePort` + `PythonXClient` typed riêng) đã bị gộp thành 1 forwarder dùng
  chung (`AiProxyRequestHandler` + `PythonAiProxyClient`, forward `Map<String,Object>` nguyên
  văn) — vì các route này chỉ proxy sang `ai-rag-core`, không có domain logic thật ở phía Java,
  nên tách riêng port/adapter cho từng route là over-engineering. Xem
  [`BACKEND_GUIDE.md` §4](../BACKEND_GUIDE.md#4).
