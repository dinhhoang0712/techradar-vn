# Build stage: cần build-essential/libpq-dev để pip build vài native wheel (asyncpg, onnxruntime...).
# Toolchain này KHÔNG cần lúc runtime nên bị loại khỏi stage cuối để giảm size image.
FROM python:3.11-slim AS builder

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements-deploy.txt .

# Cài torch bản CPU-only TRƯỚC (mặc định pip sẽ kéo bản GPU/CUDA nặng hơn nhiều lần dù stack
# này chỉ chạy CPU — không có cấu hình GPU nào trong docker-compose.yml). Cùng pattern đã dùng
# ở services/embedding-service/Dockerfile.
RUN pip install --no-cache-dir --user --timeout 600 \
    torch==2.2.0+cpu \
    --index-url https://download.pytorch.org/whl/cpu

RUN pip install --no-cache-dir --user --timeout 600 -r requirements-deploy.txt

# Download models NGAY SAU khi cài dependency, TRƯỚC khi copy source code — để sửa code
# không làm invalidate cache của bước download model nặng (vài GB) này ở các lần build sau.
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

# Groq LLM provider (langchain_groq) — cài riêng SAU bước tải model nặng ở trên, để
# thêm/đổi version ở đây không làm invalidate cache của layer torch/model (nhiều GB).
RUN pip install --no-cache-dir --user langchain-groq==0.1.9

# Runtime stage: không có compiler toolchain, chỉ có package + model đã tải sẵn từ builder.
FROM python:3.11-slim

WORKDIR /app

COPY --from=builder /root/.local /root/.local
COPY --from=builder /root/.cache /root/.cache
ENV PATH=/root/.local/bin:$PATH

# Copy source
COPY . .

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
