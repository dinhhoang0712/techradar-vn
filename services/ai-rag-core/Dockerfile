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

# Create appuser BEFORE copying so --chown can apply at copy time. Doing `COPY` then a separate
# `RUN chown -R` over these dirs (several GB of torch/model files) makes buildkit copy-on-write
# the entire contents again into a new layer — nearly doubling the image size. chmod 755 /root
# (metadata-only, not recursive) still needed so appuser can traverse into /root/.local & .cache.
RUN useradd --create-home --uid 1000 appuser \
    && chmod 755 /root
ENV PATH=/root/.local/bin:$PATH
ENV HOME=/root

COPY --from=builder --chown=appuser:appuser /root/.local /root/.local
COPY --from=builder --chown=appuser:appuser /root/.cache /root/.cache
COPY --chown=appuser:appuser . .

EXPOSE 8000

USER appuser

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
