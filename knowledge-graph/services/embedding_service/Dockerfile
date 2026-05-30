FROM python:3.11-slim

WORKDIR /app

# Install PyTorch CPU-only first (smaller, ~200MB vs ~2GB with CUDA)
RUN pip install --no-cache-dir --timeout 600 \
    torch==2.2.0+cpu \
    --index-url https://download.pytorch.org/whl/cpu

# Install other dependencies
RUN pip install --no-cache-dir --timeout 600 \
    kafka-python==2.0.2 \
    sentence-transformers==2.7.0 \
    numpy==1.26.4 \
    transformers==4.38.0

# Copy application code
COPY embedding_service.py .

# Create cache directory for model
RUN mkdir -p /app/.cache

# Set environment variables
ENV TRANSFORMERS_CACHE=/app/.cache
ENV HF_HOME=/app/.cache

# Run the service
CMD ["python", "embedding_service.py"]