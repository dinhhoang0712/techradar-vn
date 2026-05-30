"""
📦 Module: docs
================

🎯 Mục đích:
    Tập trung toàn bộ tài liệu kỹ thuật, hướng dẫn và đặc tả API
    của dự án TechPulse VN. Phục vụ onboarding thành viên mới,
    review code, và bảo trì dài hạn.

📋 Chức năng chính:
    1. API Documentation (api/):
       - OpenAPI/Swagger spec (auto-generated từ FastAPI)
       - API Contract: Mô tả chi tiết từng endpoint (request/response format)
       - Postman Collection export

    2. Architecture (architecture/):
       - System Architecture Diagram (C4 Model)
       - Data Flow Diagram: Luồng dữ liệu từ Crawl → ETL → Neo4j → RAG → Response
       - Graph Schema Diagram: Visual mô tả Nodes & Edges
       - Infrastructure Diagram: Cloud deployment topology

    3. Guides (guides/):
       - SETUP.md: Hướng dẫn setup môi trường development
       - CONTRIBUTING.md: Quy tắc đóng góp code, Git Flow, Conventional Commits
       - DEPLOYMENT.md: Hướng dẫn deploy lên Vercel + Render
       - TESTING.md: Hướng dẫn viết và chạy test

    4. Business (business/):
       - Use Cases / User Stories
       - Business Logic: Công thức tính Trend Score, Sentiment Score
       - Glossary: Bảng thuật ngữ dự án

👤 Owner: Leader / PM (chịu trách nhiệm chính) + All members (đóng góp)
"""
