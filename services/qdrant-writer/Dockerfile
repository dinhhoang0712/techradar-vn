FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY qdrant_writer.py .
CMD ["python", "qdrant_writer.py"]
