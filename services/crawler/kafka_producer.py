"""
Kafka Producer utility for crawlers.
Sends crawled data to Kafka topics for processing.
"""

import os
import json
import logging
from datetime import datetime
from typing import Dict, Any, Optional

from kafka import KafkaProducer
from kafka.errors import KafkaError

logger = logging.getLogger(__name__)


class CrawlerKafkaProducer:
    """Kafka producer for crawler data."""
    
    def __init__(
        self,
        bootstrap_servers: Optional[str] = None,
        topic_articles: str = "raw_articles",
        topic_jobs: str = "raw_jobs"
    ):
        """
        Initialize Kafka producer.
        
        Args:
            bootstrap_servers: Kafka broker addresses (comma-separated)
            topic_articles: Topic for article data
            topic_jobs: Topic for job data
        """
        self.bootstrap_servers = bootstrap_servers or os.getenv(
            "KAFKA_BOOTSTRAP_SERVERS", "localhost:9094"
        )
        self.topic_articles = topic_articles
        self.topic_jobs = topic_jobs
        self.producer = None
        
    def connect(self) -> bool:
        """Connect to Kafka broker."""
        try:
            self.producer = KafkaProducer(
                bootstrap_servers=self.bootstrap_servers.split(","),
                value_serializer=lambda v: json.dumps(v, ensure_ascii=False, default=str).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                acks=1,
                retries=3,
                retry_backoff_ms=1000,
                request_timeout_ms=10000,
            )
            logger.info(f"Connected to Kafka at {self.bootstrap_servers}")
            return True
        except KafkaError as e:
            logger.error(f"Failed to connect to Kafka: {e}")
            return False
    
    def send_article(
        self,
        title: str,
        content: str,
        source_url: str,
        source_platform: str,
        publish_date: str = "",
        key: Optional[str] = None
    ) -> bool:
        """
        Send article data to Kafka.
        
        Args:
            title: Article title
            content: Article content
            source_url: Original URL
            source_platform: Platform name (VNExpress, GenK, DanTri)
            publish_date: Publication date
            key: Optional message key
            
        Returns:
            True if sent successfully
        """
        if not self.producer:
            logger.warning("Producer not connected, skipping Kafka send")
            return False
        
        message = {
            "message_type": "article",
            "source_platform": source_platform,
            "crawled_at": datetime.utcnow().isoformat() + "Z",
            "data": {
                "title": title,
                "publish_date": publish_date,
                "content": content,
                "source_url": source_url
            }
        }
        
        try:
            future = self.producer.send(
                self.topic_articles,
                key=key,
                value=message
            )
            # Wait for confirmation (optional, can be removed for async)
            future.get(timeout=10)
            logger.debug(f"Sent article to Kafka: {title[:50]}...")
            return True
        except KafkaError as e:
            logger.error(f"Failed to send article to Kafka: {e}")
            return False
    
    def send_job(
        self,
        job_title: str,
        company_name: str,
        location: str,
        salary: str,
        level: str,
        description: str,
        requirement: str,
        benefit: str,
        skills: list,
        source_url: str,
        posted_date: str = "",
        source_platform: str = "TopCV",
        key: Optional[str] = None
    ) -> bool:
        """
        Send job data to Kafka.
        
        Args:
            job_title: Job title
            company_name: Company name
            location: Job location
            salary: Salary range
            level: Job level
            description: Job description
            requirement: Job requirements
            benefit: Job benefits
            skills: Required skills
            source_url: Original URL
            posted_date: Posted date
            source_platform: Platform name
            key: Optional message key
            
        Returns:
            True if sent successfully
        """
        if not self.producer:
            logger.warning("Producer not connected, skipping Kafka send")
            return False
        
        message = {
            "message_type": "job",
            "source_platform": source_platform,
            "crawled_at": datetime.utcnow().isoformat() + "Z",
            "data": {
                "job_title": job_title,
                "company_name": company_name,
                "location": location,
                "salary": salary,
                "level": level,
                "description": description,
                "requirement": requirement,
                "benefit": benefit,
                "skills": skills if isinstance(skills, list) else [],
                "source_url": source_url,
                "posted_date": posted_date
            }
        }
        
        try:
            future = self.producer.send(
                self.topic_jobs,
                key=key,
                value=message
            )
            future.get(timeout=10)
            logger.debug(f"Sent job to Kafka: {job_title[:50]}...")
            return True
        except KafkaError as e:
            logger.error(f"Failed to send job to Kafka: {e}")
            return False
    
    def flush(self):
        """Flush pending messages."""
        if self.producer:
            self.producer.flush()
    
    def close(self):
        """Close the producer."""
        if self.producer:
            self.producer.flush()
            self.producer.close()
            logger.info("Kafka producer closed")


def get_kafka_producer() -> CrawlerKafkaProducer:
    """Factory function to create a Kafka producer."""
    return CrawlerKafkaProducer()