"""
📦 Module: data-pipeline
=========================

🎯 Mục đích:
    Pipeline thu thập và xử lý dữ liệu tự động cho TechPulse VN.
    Thực hiện toàn bộ luồng ETL: Crawl dữ liệu thô từ đa nguồn →
    Làm sạch & chuẩn hóa → Trích xuất thực thể (NER) → Load vào Neo4j.

📋 Chức năng chính:
    1. Crawlers (crawlers/):
       - VibleCrawler: Thu thập bài viết từ Viblo.asia (Scrapy)
       - TopDevCrawler: Thu thập tin tuyển dụng từ TopDev (Scrapy + Selenium)
       - RedditCrawler: Thu thập bài viết từ r/cscareerquestions, r/programming
       - FacebookCrawler: Thu thập từ các group IT VN (Selenium, xử lý anti-bot)
       - Chống block/captcha, retry logic, proxy rotation

    2. ETL Pipeline (etl/):
       - Extract: Đọc raw data từ crawlers (JSON/CSV)
       - Transform: Làm sạch văn bản, chuẩn hóa tiếng Việt (underthesea/pyvi),
         loại bỏ HTML tags, normalize whitespace
       - Load: Chuyển dữ liệu đã xử lý vào staging area hoặc trực tiếp Neo4j

    3. NLP Processors (processors/):
       - NER Extractor: Bóc tách thực thể — Kỹ năng (Python, React),
         Công ty (FPT, VNG), Mức lương, Vị trí, Địa điểm
       - Sentiment Analyzer: Phân tích cảm xúc bài viết/review (positive/negative/neutral)
       - Text Classifier: Phân loại chủ đề bài viết

    4. Schedulers (schedulers/):
       - Cronjob definitions (crontab / APScheduler)
       - Lên lịch crawl tự động (daily/weekly)
       - Retry policy cho failed jobs

🛠️ Tech Stack:
    - Scrapy (web crawling framework)
    - Selenium (dynamic page rendering)
    - underthesea / pyvi (Vietnamese NLP)
    - APScheduler (job scheduling)

🔧 Cách chạy:
    - Crawl Viblo:    python -m data-pipeline.crawlers.viblo_crawler
    - Run ETL:        python -m data-pipeline.etl.pipeline
    - Cronjob:        python -m data-pipeline.schedulers.main

👤 Owner: Data Engineer (DE)
"""
