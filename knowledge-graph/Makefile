.PHONY: all docker-up docker-down docker-infra docker-crawlers docker-build logs clean setup-dirs

# Default target
all: setup-dirs

# Run all services with Docker
docker-up:
	docker compose up -d

# Run infrastructure only (Kafka, Zookeeper)
docker-infra:
	docker compose up -d zookeeper kafka

# Run crawlers
docker-crawlers:
	docker compose --profile crawlers up -d

# Stop all services
docker-down:
	docker compose down

# View logs
logs:
	docker compose logs -f

# Build Docker images
docker-build:
	docker compose build

# Clean build artifacts
clean:
	rm -rf bin/

# Create data directories
setup-dirs:
	mkdir -p data/raw/vnexpress data/raw/genk data/raw/dantri data/raw/topcv
