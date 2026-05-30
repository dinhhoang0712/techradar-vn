"""
📦 Module: tests
=================

🎯 Mục đích:
    Chứa các bài test tích hợp (Integration Tests) và end-to-end (E2E Tests)
    cho toàn dự án TechPulse VN. Các unit tests nằm trong từng module riêng,
    module này chỉ chứa tests cần phối hợp nhiều module.

📋 Chức năng chính:
    1. Integration Tests (integration/):
       - test_pipeline_to_neo4j.py: Test luồng Data Pipeline → Neo4j
         (crawl → ETL → load → verify data trong graph)
       - test_rag_pipeline.py: Test luồng Query → RAG → Response
         (embedding → vector search → graph traversal → LLM response)
       - test_api_to_rag.py: Test Backend API → AI RAG Core
         (HTTP request → service → RAG → streaming response)

    2. E2E Tests (e2e/):
       - test_chat_flow.py: Test full flow Chatbot
         (User input → API → RAG → Streaming Response → Frontend render)
       - test_search_flow.py: Test full flow Search
       - test_data_freshness.py: Test dữ liệu mới được crawl và
         có thể truy vấn được qua chatbot

    3. Performance Tests (performance/):
       - test_api_load.py: Load testing Backend API (concurrent requests)
       - test_neo4j_query.py: Benchmark Cypher query execution time
       - test_rag_latency.py: Đo latency của RAG pipeline

    4. Data Quality Tests (data_quality/):
       - test_node_integrity.py: Kiểm tra tính toàn vẹn nodes trong Neo4j
       - test_embedding_quality.py: Kiểm tra chất lượng embeddings
       - test_no_duplicate.py: Kiểm tra dữ liệu trùng lặp

🛠️ Tech Stack:
    - pytest + pytest-asyncio
    - httpx (async API testing)
    - JMeter (performance testing)
    - Faker (test data generation)

🔧 Cách chạy:
    - All tests:        pytest tests/
    - Integration:      pytest tests/integration/
    - E2E:              pytest tests/e2e/
    - Có coverage:      pytest tests/ --cov --cov-report=html

👤 Owner: QC / Tester (chịu trách nhiệm chính) + All members
"""
