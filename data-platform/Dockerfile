FROM python:3.12-slim

WORKDIR /app

COPY data-platform/requirements.txt .
# llm-gateway (services/llm-gateway/) — package dùng chung, cài kiểu editable qua "-e
# ../services/llm-gateway" trong requirements.txt, resolve tương đối theo WORKDIR (/app) nên
# phải nằm đúng ở /services/llm-gateway. Build context là repo root (xem docker-compose.yml)
# để COPY được đường dẫn này.
COPY services/llm-gateway /services/llm-gateway
RUN pip install --no-cache-dir --timeout 600 -r requirements.txt

COPY data-platform/ .

RUN useradd --create-home --uid 1000 appuser && chown -R appuser:appuser /app

ENV PYTHONPATH=/app
ENV PYTHONUNBUFFERED=1

USER appuser

CMD ["python", "main.py"]
