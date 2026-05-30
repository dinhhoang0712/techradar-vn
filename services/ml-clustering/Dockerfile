FROM python:3.11-slim

# Mirror host layout so conf/config.py's Path(__file__).resolve().parents[3]
# resolves to /app (acting as project root). Layout inside container:
#   /app/src/ml-clustering/conf/config.py
#   parents[0]=conf, parents[1]=ml-clustering, parents[2]=src, parents[3]=/app
WORKDIR /app/src/ml-clustering

COPY requirements-api.txt ./
RUN pip install --no-cache-dir -r requirements-api.txt

COPY . .

RUN mkdir -p /tmp/ml-clustering-cache

EXPOSE 8001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
