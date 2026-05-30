// Package config provides centralized configuration management for TechPulse VN pipeline
package config

import (
	"os"
	"strconv"
	"time"
)

// Config holds all configuration for the pipeline services
type Config struct {
	// Kafka Configuration
	Kafka KafkaConfig

	// Neo4j Configuration
	Neo4j Neo4jConfig

	// Qdrant Configuration
	Qdrant QdrantConfig

	// Embedding Configuration
	Embedding EmbeddingConfig

	// Processing Configuration
	Processing ProcessingConfig
}

// KafkaConfig holds Kafka-related configuration
type KafkaConfig struct {
	Brokers               []string
	TopicRawArticles      string
	TopicRawJobs          string
	TopicExtractedArticles string
	TopicExtractedJobs    string
	TopicArticleVectors   string
	TopicJobVectors       string
}

// Neo4jConfig holds Neo4j connection configuration
type Neo4jConfig struct {
	URI      string
	Username string
	Password string
	Database string
}

// QdrantConfig holds Qdrant connection configuration
type QdrantConfig struct {
	Host    string
	Port    int
	APIKey  string
	UseTLS  bool
}

// EmbeddingConfig holds embedding service configuration
type EmbeddingConfig struct {
	APIUrl    string
	APIKey    string
	Model     string
	Dimension int
}

// ProcessingConfig holds processing-related configuration
type ProcessingConfig struct {
	BatchSize        int
	BatchTimeoutSec  int
}

// Load reads configuration from environment variables
func Load() *Config {
	return &Config{
		Kafka: KafkaConfig{
			Brokers:               getSliceEnv("KAFKA_BOOTSTRAP_SERVERS", []string{"localhost:29092"}),
			TopicRawArticles:      getEnv("KAFKA_TOPIC_RAW_ARTICLES", "raw_articles"),
			TopicRawJobs:          getEnv("KAFKA_TOPIC_RAW_JOBS", "raw_jobs"),
			TopicExtractedArticles: getEnv("KAFKA_TOPIC_EXTRACTED_ARTICLES", "extracted_articles"),
			TopicExtractedJobs:    getEnv("KAFKA_TOPIC_EXTRACTED_JOBS", "extracted_jobs"),
			TopicArticleVectors:   getEnv("KAFKA_TOPIC_ARTICLE_VECTORS", "article_vectors"),
			TopicJobVectors:       getEnv("KAFKA_TOPIC_JOB_VECTORS", "job_vectors"),
		},
		Neo4j: Neo4jConfig{
			URI:      getEnv("NEO4J_URI", ""),
			Username: getEnv("NEO4J_USERNAME", ""),
			Password: getEnv("NEO4J_PASSWORD", ""),
			Database: getEnv("NEO4J_DATABASE", ""),
		},
		Qdrant: QdrantConfig{
			Host:   getEnv("QDRANT_HOST", ""),
			Port:   getIntEnv("QDRANT_PORT", 6334),
			APIKey: getEnv("QDRANT_API_KEY", ""),
			UseTLS: getBoolEnv("QDRANT_USE_TLS", true),
		},
		Embedding: EmbeddingConfig{
			APIUrl:    getEnv("HF_API_URL", ""),
			APIKey:    getEnv("HF_API_KEY", ""),
			Model:     getEnv("EMBEDDING_MODEL", "intfloat/multilingual-e5-base"),
			Dimension: getIntEnv("EMBEDDING_DIMENSION", 768),
		},
		Processing: ProcessingConfig{
			BatchSize:       getIntEnv("BATCH_SIZE", 30),
			BatchTimeoutSec: getIntEnv("BATCH_TIMEOUT_SEC", 10),
		},
	}
}

// Helper functions for environment variables

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getSliceEnv(key string, defaultValue []string) []string {
	if value := os.Getenv(key); value != "" {
		// Simple split by comma
		var result []string
		for _, v := range splitString(value, ",") {
			if v != "" {
				result = append(result, v)
			}
		}
		if len(result) > 0 {
			return result
		}
	}
	return defaultValue
}

func getIntEnv(key string, defaultValue int) int {
	if value := os.Getenv(key); value != "" {
		if intVal, err := strconv.Atoi(value); err == nil {
			return intVal
		}
	}
	return defaultValue
}

func getBoolEnv(key string, defaultValue bool) bool {
	if value := os.Getenv(key); value != "" {
		if boolVal, err := strconv.ParseBool(value); err == nil {
			return boolVal
		}
	}
	return defaultValue
}

func splitString(s, sep string) []string {
	var result []string
	start := 0
	for i := 0; i <= len(s)-len(sep); i++ {
		if s[i:i+len(sep)] == sep {
			result = append(result, s[start:i])
			start = i + len(sep)
			i += len(sep) - 1
		}
	}
	result = append(result, s[start:])
	return result
}

// Common constants
const (
	// Collection names for Qdrant
	CollectionArticles = "techpulse_articles"
	CollectionJobs     = "techpulse_jobs"

	// Consumer group names
	GroupEntityExtractor = "entity-extractor-group"
	GroupNeo4jWriter     = "neo4j-writer-group"
	GroupEmbedding       = "embedding-service-group"
	GroupQdrantWriter    = "qdrant-writer-group"

	// Default timeouts
	DefaultTimeout = 30 * time.Second
)