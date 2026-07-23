# Python Crawler Dockerfile
FROM python:3.11-slim

WORKDIR /app

# Dependencies hệ thống cho Chrome (Selenium/undetected-chromedriver) + tool để tải .deb.
# gosu: drops root -> appuser in the entrypoint after fixing volume ownership (see docker-entrypoint.sh).
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    unzip \
    gosu \
    libnss3 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libdrm2 \
    libxkbcommon0 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libxrandr2 \
    libgbm1 \
    libasound2 \
    libpango-1.0-0 \
    libcairo2 \
    libatspi2.0-0 \
    libxshmfence1 \
    fonts-liberation \
    libappindicator3-1 \
    libdbus-glib-1-2 \
    xdg-utils \
    && rm -rf /var/lib/apt/lists/*

# Pin Chrome ở đúng version đang chạy ổn định trong production (150.0.7871.128), tải trực
# tiếp file .deb theo version cụ thể — thay vì add apt repo Google rồi install bản mới nhất.
# "apt-get install google-chrome-stable" không pin version sẽ âm thầm đổi Chrome mỗi lần build
# lại image, và đã từng gây lỗi thật: chromedriver do undetected-chromedriver tự fetch lệch
# version với Chrome đã bake trong image cũ, ra lỗi "session not created: This version of
# ChromeDriver only supports Chrome version X" (xem chrome_utils.py — nơi work around lỗi này
# bằng cách detect version Chrome đã cài rồi truyền version_main= cho uc.Chrome()). Pin version
# ở đây giúp build tái lập được, Chrome không đổi bất ngờ giữa các lần build.
RUN CHROME_VERSION=150.0.7871.128 \
    && wget -q "https://dl.google.com/linux/chrome/deb/pool/main/g/google-chrome-stable/google-chrome-stable_${CHROME_VERSION}-1_amd64.deb" -O /tmp/chrome.deb \
    && apt-get update \
    && apt-get install -y --no-install-recommends /tmp/chrome.deb \
    && rm -rf /tmp/chrome.deb /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy crawler code
COPY . .

# Create data directories, plus a home dir for the non-root user (Chrome needs a writable
# HOME for its own profile/cache — it refuses to run as root without --no-sandbox anyway,
# and every crawler already sets --no-sandbox, so this doesn't change Chrome's launch flags).
RUN mkdir -p /app/data/raw/vnexpress \
    /app/data/raw/genk \
    /app/data/raw/dantri \
    /app/data/raw/topcv \
    /app/data/raw/topdev \
    /app/data/raw/vietnamworks \
    && useradd --create-home --uid 1000 appuser \
    && chown -R appuser:appuser /app \
    && chmod +x docker-entrypoint.sh

# Stays root here on purpose: the crawler_data volume (docker-compose.yml) keeps whatever
# ownership it had from earlier root-run containers, so entrypoint.sh chowns it fresh on every
# start (cheap — it's just JSON/text files) before dropping to appuser to actually run the crawl.
ENTRYPOINT ["./docker-entrypoint.sh"]

# Default command: runs all crawlers on a loop every CRAWL_INTERVAL_HOURS (default 6h)
CMD ["python", "run_all.py"]
