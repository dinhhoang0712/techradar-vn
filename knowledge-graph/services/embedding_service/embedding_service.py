"""
Embedding Service using SentenceTransformers.
Generates embeddings for articles and jobs using intfloat/multilingual-e5-base.
"""

import os
import json
import logging
import hashlib
from typing import Optional, List, Dict, Any

from kafka import KafkaConsumer, KafkaProducer
from sentence_transformers import SentenceTransformer
import numpy as np

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class EmbeddingService:
    """Service for generating embeddings using SentenceTransformers."""
    
    def __init__(
        self,
        model_name: str = "intfloat/multilingual-e5-base",
        bootstrap_servers: str = "kafka:29092",
        topic_extracted_articles: str = "extracted_articles",
        topic_extracted_jobs: str = "extracted_jobs",
        topic_article_vectors: str = "article_vectors",
        topic_job_vectors: str = "job_vectors",
        embedding_dimension: int = 768
    ):
        self.model_name = model_name
        self.bootstrap_servers = bootstrap_servers
        self.topic_extracted_articles = topic_extracted_articles
        self.topic_extracted_jobs = topic_extracted_jobs
        self.topic_article_vectors = topic_article_vectors
        self.topic_job_vectors = topic_job_vectors
        self.embedding_dimension = embedding_dimension
        
        self.model = None
        self.producer = None
        self.article_consumer = None
        self.job_consumer = None
        
    def load_model(self):
        """Load the embedding model."""
        logger.info(f"Loading embedding model: {self.model_name}")
        self.model = SentenceTransformer(self.model_name)
        logger.info(f"Model loaded successfully. Embedding dimension: {self.model.get_sentence_embedding_dimension()}")
        
    def connect_kafka(self):
        """Connect to Kafka."""
        logger.info(f"Connecting to Kafka at {self.bootstrap_servers}")
        
        # Producer for vectors
        self.producer = KafkaProducer(
            bootstrap_servers=self.bootstrap_servers.split(","),
            value_serializer=lambda v: json.dumps(v, ensure_ascii=False).encode('utf-8'),
            key_serializer=lambda k: k.encode('utf-8') if k else None,
            acks=1,
            retries=3
        )
        
        # Consumer for extracted articles
        self.article_consumer = KafkaConsumer(
            self.topic_extracted_articles,
            bootstrap_servers=self.bootstrap_servers.split(","),
            auto_offset_reset='earliest',
            enable_auto_commit=True,
            group_id='embedding-service-articles',
            value_deserializer=lambda m: json.loads(m.decode('utf-8'))
        )
        
        # Consumer for extracted jobs
        self.job_consumer = KafkaConsumer(
            self.topic_extracted_jobs,
            bootstrap_servers=self.bootstrap_servers.split(","),
            auto_offset_reset='earliest',
            enable_auto_commit=True,
            group_id='embedding-service-jobs',
            value_deserializer=lambda m: json.loads(m.decode('utf-8'))
        )
        
        logger.info("Connected to Kafka successfully")
        
    def generate_embedding(self, text: str) -> List[float]:
        """Generate embedding for a text."""
        if not text or not text.strip():
            return [0.0] * self.embedding_dimension
        
        # Add prefix for E5 model
        prefixed_text = f"passage: {text}"
        
        # Generate embedding
        embedding = self.model.encode(prefixed_text, normalize_embeddings=True)
        return embedding.tolist()
    
    @staticmethod
    def generate_id(source_url: str) -> str:
        """Generate MD5 ID from source URL."""
        return hashlib.md5(source_url.encode()).hexdigest()
    
    def process_article(self, message: Dict[str, Any]) -> Dict[str, Any]:
        """Process an article and generate embedding."""
        data = message.get("data", {})
        source_url = data.get("source_url", "")
        content = data.get("content", "")
        
        # Generate embedding from content
        embedding = self.generate_embedding(content)
        
        # Generate ID from source URL
        article_id = self.generate_id(source_url)
        
        return {
            "message_type": "article_vector",
            "id": article_id,
            "source_url": source_url,
            "source_platform": message.get("source_platform", ""),
            "embedding": embedding,
            "metadata": {
                "title": data.get("title", ""),
                "published_date": data.get("publish_date", "")
            }
        }
    
    def process_job(self, message: Dict[str, Any]) -> Dict[str, Any]:
        """Process a job and generate embedding."""
        data = message.get("data", {})
        job = data.get("job", {})
        
        source_url = job.get("source_url", "")
        
        # Combine description, requirement, and benefit for embedding
        description = job.get("description", "")
        requirement = job.get("requirement", "")
        benefit = job.get("benefit", "")
        combined_text = f"{description} {requirement} {benefit}"
        
        # Generate embedding
        embedding = self.generate_embedding(combined_text)
        
        # Generate ID from source URL
        job_id = self.generate_id(source_url)
        
        return {
            "message_type": "job_vector",
            "id": job_id,
            "source_url": source_url,
            "source_platform": message.get("source_platform", ""),
            "embedding": embedding,
            "metadata": {
                "title": job.get("title", ""),
                "company_name": data.get("company", {}).get("name", ""),
                "location": data.get("company", {}).get("location", ""),
                "salary": job.get("salary", "")
            }
        }
    
    def send_vector(self, topic: str, vector_data: Dict[str, Any]):
        """Send vector to Kafka topic."""
        try:
            future = self.producer.send(
                topic,
                key=vector_data.get("id"),
                value=vector_data
            )
            future.get(timeout=10)
            logger.debug(f"Sent vector to {topic}: {vector_data.get('id')}")
        except Exception as e:
            logger.error(f"Error sending vector to {topic}: {e}")
    
    def run(self):
        """Run the embedding service."""
        logger.info("Starting Embedding Service...")
        
        # Load model
        self.load_model()
        
        # Connect to Kafka
        self.connect_kafka()
        
        logger.info("Embedding Service started. Waiting for messages...")
        
        try:
            while True:
                # Process articles
                for message in self.article_consumer:
                    try:
                        logger.info(f"Processing article: {message.value.get('data', {}).get('title', 'Unknown')[:50]}...")
                        vector = self.process_article(message.value)
                        self.send_vector(self.topic_article_vectors, vector)
                        logger.info(f"Generated embedding for article: {vector['id'][:16]}...")
                    except Exception as e:
                        logger.error(f"Error processing article: {e}")
                
                # Process jobs
                for message in self.job_consumer:
                    try:
                        logger.info(f"Processing job: {message.value.get('data', {}).get('job', {}).get('title', 'Unknown')[:50]}...")
                        vector = self.process_job(message.value)
                        self.send_vector(self.topic_job_vectors, vector)
                        logger.info(f"Generated embedding for job: {vector['id'][:16]}...")
                    except Exception as e:
                        logger.error(f"Error processing job: {e}")
                        
        except KeyboardInterrupt:
            logger.info("Shutting down Embedding Service...")
        finally:
            self.close()
    
    def close(self):
        """Close all connections."""
        if self.producer:
            self.producer.flush()
            self.producer.close()
        if self.article_consumer:
            self.article_consumer.close()
        if self.job_consumer:
            self.job_consumer.close()
        logger.info("Embedding Service closed")


def main():
    """Main entry point."""
    # Get configuration from environment
    bootstrap_servers = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092")
    model_name = os.getenv("EMBEDDING_MODEL", "intfloat/multilingual-e5-base")
    
    # Create and run service
    service = EmbeddingService(
        model_name=model_name,
        bootstrap_servers=bootstrap_servers
    )
    service.run()


if __name__ == "__main__":
    main()