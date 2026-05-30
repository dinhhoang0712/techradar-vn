"""
Base crawler class with Kafka integration.
"""

import os
import json
import csv
import logging
from datetime import datetime
from abc import ABC, abstractmethod
from typing import List, Dict, Any, Optional

from kafka_producer import CrawlerKafkaProducer

logger = logging.getLogger(__name__)


class BaseCrawler(ABC):
    """Base class for all crawlers with Kafka and CSV support."""
    
    # Number of articles to crawl per run
    MAX_ARTICLES = 150
    
    def __init__(self, source_platform: str):
        """
        Initialize base crawler.
        
        Args:
            source_platform: Name of the source (VNExpress, GenK, DanTri, TopCV)
        """
        self.source_platform = source_platform
        self.kafka_producer = CrawlerKafkaProducer()
        self.output_dir = os.path.join(os.path.dirname(__file__), "data", "raw", source_platform.lower())
        self.total_articles = 0
        self.kafka_enabled = False
        
    def setup(self):
        """Setup crawler: create directories, connect to Kafka."""
        os.makedirs(self.output_dir, exist_ok=True)
        
        # Try to connect to Kafka (optional)
        try:
            self.kafka_enabled = self.kafka_producer.connect()
            if self.kafka_enabled:
                logger.info(f"Kafka enabled for {self.source_platform}")
            else:
                logger.warning(f"Kafka not available for {self.source_platform}, data will only be saved to CSV")
        except Exception as e:
            logger.warning(f"Kafka connection failed: {e}")
            self.kafka_enabled = False
    
    def get_csv_path(self) -> str:
        """Get CSV file path for today."""
        today_str = datetime.now().strftime("%d_%m_%Y")
        return os.path.join(self.output_dir, f"{today_str}.csv")
    
    def init_csv(self, fieldnames: List[str]):
        """Initialize CSV file with headers."""
        csv_path = self.get_csv_path()
        if not os.path.exists(csv_path):
            with open(csv_path, "w", encoding="utf-8", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=fieldnames)
                writer.writeheader()
    
    def save_to_csv(self, data: Dict[str, Any], fieldnames: List[str]):
        """Save data to CSV file."""
        csv_path = self.get_csv_path()
        with open(csv_path, "a", encoding="utf-8", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction='ignore')
            writer.writerow(data)
    
    def send_to_kafka_article(self, title: str, content: str, source_url: str, publish_date: str = ""):
        """Send article to Kafka."""
        if self.kafka_enabled:
            self.kafka_producer.send_article(
                title=title,
                content=content,
                source_url=source_url,
                source_platform=self.source_platform,
                publish_date=publish_date
            )
    
    def send_to_kafka_job(
        self,
        job_title: str,
        company_name: str,
        location: str,
        salary: str,
        level: str,
        description: str,
        requirement: str,
        benefit: str,
        skills: List[str],
        source_url: str,
        posted_date: str = ""
    ):
        """Send job to Kafka."""
        if self.kafka_enabled:
            self.kafka_producer.send_job(
                job_title=job_title,
                company_name=company_name,
                location=location,
                salary=salary,
                level=level,
                description=description,
                requirement=requirement,
                benefit=benefit,
                skills=skills,
                source_url=source_url,
                posted_date=posted_date,
                source_platform=self.source_platform
            )
    
    def cleanup(self):
        """Cleanup resources."""
        if self.kafka_producer:
            self.kafka_producer.close()
    
    @abstractmethod
    def crawl(self):
        """Main crawl method to be implemented by subclasses."""
        pass
    
    def run(self):
        """Run the crawler."""
        try:
            self.setup()
            self.crawl()
        finally:
            self.cleanup()
            logger.info(f"Crawler finished. Total articles: {self.total_articles}")