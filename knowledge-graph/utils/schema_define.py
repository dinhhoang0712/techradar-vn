"""
Node Definitions for Job Market Graph
"""
from dataclasses import dataclass, field
from typing import Optional, List
from datetime import datetime

# Note: Article and Job classes are defined in database_connection.py
# to avoid circular import issues

@dataclass
class Technology:
    """Technology Node"""
    name: str
    category: str  # AI, Cloud, DevOps, Frontend, Backend, etc.
    description: str = ""
    trend_score: float = 0.5  # 0-1, based on article mentions frequency
    aliases: List[str] = field(default_factory=list)  # AI, Artificial Intelligence, ML
    
    def __hash__(self):
        return hash(self.name.lower())

@dataclass
class Company:
    """Company Node"""
    name: str
    field: str  # Tech, Finance, Healthcare, etc.
    size: str = "Unknown"  # Startup, SME, Enterprise
    location: str = "Unknown"
    rating: float = 0.0  # Based on sentiment from articles
    
    def __hash__(self):
        return hash(self.name.lower())

@dataclass
class Job:
    """Job Node"""
    title: str
    description: str = ""
    requirement: str = ""
    benefit: str = ""
    salary: str = ""  # Combined salary range as string (e.g., "15-25 triệu", "Thoả thuận")
    due_date: Optional[datetime] = None
    source_url: str = ""

    def __hash__(self):
        return hash(self.title.lower())

@dataclass
class Article:
    """Article Node"""
    title: str
    content: str
    source: str  # VN-Express, Dân Trí
    published_date: datetime
    sentiment_score: float = 0.0  # -1 to 1

    def __hash__(self):
        return hash(self.title.lower())

@dataclass
class Skill:
    """Skill Node"""
    name: str
    category: str  # Programming, DevOps, Soft Skills, etc.
    demand_score: float = 0.5  # 0-1, based on job requirements frequency

    def __hash__(self):
        return hash(self.name.lower())

@dataclass
class Person:
    """Person Node"""
    name: str
    role: str = "Unknown"  # CEO, CTO, Engineer, Researcher, etc.

    def __hash__(self):
        return hash(self.name.lower())

# Relationship Types
class RelationshipType:
    REQUIRES = "REQUIRES"           # Job -> Skill
    USES = "USES"                   # Company -> Technology
    POSTED_BY = "POSTED_BY"         # Job -> Company
    MENTIONS_TECH = "MENTIONS"      # Article -> Technology
    MENTIONS_COMPANY = "MENTIONS"   # Article -> Company
    RELATED_TO = "RELATED_TO"       # Technology -> Technology
    HAS_SENTIMENT = "HAS_SENTIMENT" # Article -> Sentiment
    WORKS_AT = "WORKS_AT"           # Person -> Company
    WROTE = "WROTE"                 # Person -> Article