# Documentation — TechRadar VN

> Tài liệu kỹ thuật đầy đủ cho dự án TechRadar VN.

---

## 📚 Mục lục tài liệu

### Tổng quan

- **[Architecture Overview](./ARCHITECTURE.md)** - Kiến trúc tổng thể của hệ thống, bao gồm các thành phần, luồng dữ liệu và thiết kế kỹ thuật.

### Hướng dẫn phát triển

- **[Development Guide](./DEVELOPMENT_GUIDE.md)** - Hướng dẫn thiết lập môi trường, quy trình phát triển, coding standards và troubleshooting.

### Backend

- **[Backend Guide](./BACKEND_GUIDE.md)** - Tài liệu chi tiết về Spring Boot backend, kiến trúc Hexagonal, database layer, security và testing.

### Frontend

- **[Frontend Guide](./FRONTEND_GUIDE.md)** - Tài liệu chi tiết về React frontend, component architecture, state management, routing và styling.

### AI Services

- **[AI Platform](./AI_PLATFORM.md)** - Tài liệu kỹ thuật đầy đủ cho 2 Python AI services: `ai-rag-core` và `ml-clustering`.

### API

- **[API Documentation v1](./API_DOCs_v1.md)** - Tài liệu API endpoints, request/response formats và authentication.

### Deployment

- **[Deployment Guide](./DEPLOYMENT.md)** - Hướng dẫn deployment với Docker Compose, cấu hình môi trường và production notes.

### Subsystem Documentation

- **[Knowledge Graph](../knowledge-graph/README.md)** - Tài liệu về Knowledge Graph subsystem, bao gồm schema, entity resolution, ontology và crawlers.
- **[Data Platform](../data-platform/README.md)** - Tài liệu gốc về Data Platform, bao gồm Bronze/Silver/Gold layers, scheduler và ETL pipelines.
- **[Data Platform Guide](./DATA_PLATFORM.md)** - Tài liệu chi tiết về Data Platform cho developers, bao gồm kiến trúc Medallion, crawlers, processing layers, monitoring và troubleshooting.

---

## 🚀 Quick Start

### 1. Clone repository

```bash
git clone https://github.com/dinhhoang0712/techradar-vn.git
cd techradar-vn
```

### 2. Chạy với Docker Compose (Khuyến nghị)

```bash
cp .env.docker.example .env
# Edit .env với API keys của bạn
docker compose up --build
```

### 3. Truy cập ứng dụng

- **Web App**: http://localhost:5173
- **API Gateway**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Neo4j Browser**: http://localhost:7474

### 4. Tài khoản dev

- **Email**: admin@techradar.vn
- **Password**: Admin@12345

---

## 📖 Tài liệu theo vai trò

### Cho Developer mới

1. Đọc [Architecture Overview](./ARCHITECTURE.md) để hiểu tổng quan hệ thống
2. Đọc [Development Guide](./DEVELOPMENT_GUIDE.md) để thiết lập môi trường
3. Đọc [Backend Guide](./BACKEND_GUIDE.md) hoặc [Frontend Guide](./FRONTEND_GUIDE.md) tùy theo vai trò

### Cho Backend Developer

1. [Architecture Overview](./ARCHITECTURE.md) - Phần Backend Architecture
2. [Backend Guide](./BACKEND_GUIDE.md) - Chi tiết implementation
3. [API Documentation](./API_DOCs_v1.md) - API endpoints
4. [AI Platform](./AI_PLATFORM.md) - Tích hợp với AI services

### Cho Frontend Developer

1. [Architecture Overview](./ARCHITECTURE.md) - Phần Frontend Architecture
2. [Frontend Guide](./FRONTEND_GUIDE.md) - Chi tiết implementation
3. [API Documentation](./API_DOCs_v1.md) - API integration

### Cho AI/ML Engineer

1. [Architecture Overview](./ARCHITECTURE.md) - Phần AI Services
2. [AI Platform](./AI_PLATFORM.md) - Chi tiết AI services
3. [Knowledge Graph](../knowledge-graph/README.md) - Graph data
4. [Data Platform](../data-platform/README.md) - Data pipeline

### Cho DevOps Engineer

1. [Deployment Guide](./DEPLOYMENT.md) - Docker Compose deployment
2. [Development Guide](./DEVELOPMENT_GUIDE.md) - Environment setup
3. [Architecture Overview](./ARCHITECTURE.md) - System architecture

---

## 🔗 Liên kết hữu ích

### Internal Links

- **GitHub Repository**: https://github.com/dinhhoang0712/techradar-vn
- **Issues**: https://github.com/dinhhoang0712/techradar-vn/issues
- **Discussions**: https://github.com/dinhhoang0712/techradar-vn/discussions

### External Documentation

- **Spring Boot**: https://docs.spring.io/spring-boot/
- **React**: https://react.dev/
- **FastAPI**: https://fastapi.tiangolo.com/
- **Neo4j**: https://neo4j.com/docs/
- **Docker**: https://docs.docker.com/
- **PostgreSQL**: https://www.postgresql.org/docs/

---

## 📝 Tài liệu tham khảo nhanh

### Cấu trúc dự án

```
techradar-vn/
├── apps/
│   ├── backend/          # Spring Boot API Gateway
│   ├── web/              # React Frontend
│   └── mobile/           # Expo Mobile App
├── services/
│   ├── ai-rag-core/      # Graph RAG Service
│   ├── ml-clustering/    # ML Clustering Service
│   ├── crawler/          # Web Crawlers
│   ├── embedding-service/# Embedding Service
│   └── qdrant-writer/    # Qdrant Writer
├── knowledge-graph/      # Knowledge Graph Subsystem
├── data-platform/        # Data Platform (Bronze/Silver/Gold)
├── infrastructure/       # Infrastructure configs
├── docs/                 # Documentation (thư mục này)
├── tests/                # Cross-service tests
└── docker-compose.yml    # Full stack orchestration
```

### Ports mặc định

| Service | Port |
|---------|------|
| Web (Nginx) | 5173 |
| Spring API | 8080 |
| ai-rag-core | 8000 |
| ml-clustering | 8001 |
| PostgreSQL | 5432 |
| Neo4j Browser | 7474 |
| Neo4j Bolt | 7687 |
| Redis | 6379 |
| MinIO API | 9000 |
| MinIO Console | 9001 |
| Kafka | 9092 |
| Qdrant HTTP | 6333 |
| Qdrant gRPC | 6334 |
| MailHog SMTP | 1025 |
| MailHog Web | 8025 |
| Grafana | 3001 |

### Environment Variables quan trọng

```bash
# Backend
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=techradar
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
NEO4J_URI=bolt://neo4j:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=password
JWT_SECRET=your-secret
PYTHON_RAG_BASE_URL=http://ai-rag-core:8000
PYTHON_ML_CLUSTERING_BASE_URL=http://ml-clustering:8001
INTERNAL_API_TOKEN=techradar-internal-secret

# AI Services
OPENAI_API_KEY=your-key
GEMINI_API_KEY=your-key
LLM_PROVIDER=openai

# Frontend
VITE_API_URL=http://localhost:8080
```

---

## 🤝 Contributing

Để đóng góp vào tài liệu:

1. Fork repository
2. Tạo branch feature mới
3. Thêm hoặc cập nhật tài liệu
4. Submit Pull Request với mô tả rõ ràng

Xem [Development Guide](./DEVELOPMENT_GUIDE.md) để biết thêm chi tiết về quy trình đóng góp.

---

## 📄 License

MIT License - Xem file LICENSE ở repository root để biết chi tiết.

---

## 📞 Support

Nếu bạn có câu hỏi hoặc cần hỗ trợ:

- Mở Issue trên GitHub
- Tham gia Discussions
- Liên hệ team qua email

---

**Last Updated**: 2026-07-01
