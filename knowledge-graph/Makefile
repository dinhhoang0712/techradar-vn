.PHONY: all build run docker-up docker-down test clean

# Default target
all: build

# Build the orchestrator
build:
	go mod tidy
	go build -o bin/orchestrator ./cmd/orchestrator

# Run the orchestrator locally
run:
	go run ./cmd/orchestrator

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
	docker compose logs -f orchestrator

# Build Docker images
docker-build:
	docker compose build
# Clean build artifacts
clean:
	rm -rf bin/
	go clean

# Test Go modules
test-deps:
	go mod download
	go mod verify

# Create data directories
setup-dirs:
	mkdir -p data/raw/vnexpress data/raw/genk data/raw/dantri data/raw/topcv