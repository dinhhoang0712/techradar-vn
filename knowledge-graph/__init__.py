"""
📦 Module: database
====================

🎯 Mục đích:
    Quản lý toàn bộ tầng dữ liệu Neo4j AuraDB cho TechPulse VN.
    Bao gồm định nghĩa schema đồ thị (Nodes & Edges), Cypher queries,
    database migrations, và seed data.

📋 Chức năng chính:
    1. Graph Schema (schemas/):
       - Node Definitions:
         • Technology (name, category, description, trend_score)
         • Company (name, industry, size, location, rating)
         • Job (title, salary_min, salary_max, level, source_url)
         • Skill (name, category, demand_score)
         • Article (title, content, source, published_date, sentiment_score)
         • Person (name, role — dùng cho review/author)
       - Edge (Relationship) Definitions:
         • REQUIRES (Job → Skill): Việc làm yêu cầu kỹ năng
         • USES (Company → Technology): Công ty sử dụng công nghệ
         • POSTED_BY (Job → Company): Việc làm đăng bởi công ty
         • MENTIONS (Article → Technology/Company): Bài viết đề cập
         • RELATED_TO (Technology → Technology): Công nghệ liên quan
         • HAS_SENTIMENT (Article → Sentiment): Điểm cảm xúc

    2. Cypher Queries (queries/):
       - CRUD operations cho từng loại node/edge
       - Complex queries: Trend analysis, skill demand ranking,
         salary comparison, community detection
       - Full-text search queries
       - Vector similarity search queries

    3. Migrations (migrations/):
       - Schema migration scripts (tạo constraints, indexes)
       - Vector index creation cho embedding search
       - Version tracking (migration_001, migration_002, ...)
       - Rollback support

    4. Seeds (seeds/):
       - Sample data cho development & testing
       - Initial constraints và indexes
       - Benchmark data (large dataset cho performance testing)

🛠️ Tech Stack:
    - Neo4j AuraDB (Free Tier, US region)
    - Neo4j Python Driver (neo4j package)
    - Cypher Query Language

🔧 Cách chạy:
    - Migrate:   python -m database.migrations.run
    - Seed:      python -m database.seeds.seed_all
    - Verify:    python -m database.queries.health_check

👤 Owner: Database & DevOps Engineer
"""
