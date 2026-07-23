FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir --timeout 600 -r requirements.txt

COPY . .

RUN useradd --create-home --uid 1000 appuser && chown -R appuser:appuser /app

ENV PYTHONPATH=/app
ENV PYTHONUNBUFFERED=1

USER appuser

CMD ["python", "main.py"]
