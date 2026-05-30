FROM python:3.11-slim

WORKDIR /app

# Cài system deps cần cho torch và asyncpg
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Cài Python packages cho deploy API
COPY requirements-deploy.txt .
RUN pip install --no-cache-dir -r requirements-deploy.txt

# Copy source
COPY . .

# Download models lúc build (tránh download lại mỗi lần start)
RUN python -c "\
from sentence_transformers import SentenceTransformer; \
from transformers import AutoTokenizer, AutoModelForTokenClassification; \
from huggingface_hub import snapshot_download; \
SentenceTransformer('intfloat/multilingual-e5-base'); \
snapshot_download( \
    repo_id='woxpas-ai/bge-reranker-v2-m3-onnx', \
    allow_patterns=[ \
        'onnx/model_quantized.onnx', \
        'config.json', \
        'sentencepiece.bpe.model', \
        'special_tokens_map.json', \
        'tokenizer.json', \
        'tokenizer_config.json', \
    ], \
); \
AutoTokenizer.from_pretrained('NlpHust/ner-vietnamese-electra-base'); \
AutoModelForTokenClassification.from_pretrained('NlpHust/ner-vietnamese-electra-base')"

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
