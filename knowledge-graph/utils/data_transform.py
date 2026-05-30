"""
Transform news data to Job Market Graph Schema
"""
import logging
from typing import Dict, List, Tuple, Set
from datetime import datetime
import json
from pathlib import Path

from schema_define import (
    Technology, Company, Skill, Person,
    RelationshipType
)

# Import Article and Job types from database_connection
from database_connection import Article, Job

logger = logging.getLogger(__name__)

# Source name mapping for display
SOURCE_DISPLAY_NAMES = {
    'GenK': 'GenK',
    'DanTri': 'Dân Trí',
    'Dân Trí': 'Dân Trí',
    'VN-EP': 'VNExpress',
    'VNExpress': 'VNExpress',
    'topCV': 'TopCV',
    'TopCV': 'TopCV',
}

def get_display_source(source: str) -> str:
    """
    Convert source code to display name.
    
    Examples:
        '06_05_2026_GenK' -> 'GenK'
        'DanTri' -> 'Dân Trí'
        'VN-EP' -> 'VNExpress'
        'topCV' -> 'TopCV'
    """
    if not source:
        return 'Unknown'
    
    # If source contains date pattern (DD_MM_YYYY_SourceName), extract source name
    import re
    date_pattern = r'\d{2}_\d{2}_\d{4}_(.+)'
    match = re.match(date_pattern, source)
    if match:
        source = match.group(1)
    
    # Look up display name
    return SOURCE_DISPLAY_NAMES.get(source, source)


class DataTransformer:
    """Transform extracted news data to Job Market schema"""
    
    def __init__(self):
        self.technologies: Set[Technology] = set()
        self.companies: Set[Company] = set()
        self.skills: Set[Skill] = set()
        self.articles: List[Article] = []
        self.persons: Set[Person] = set()
        self.job_roles: List[Job] = []
        
        # Tech category mapping
        self.tech_categories = {
            'AI': ['AI', 'Machine Learning', 'ML', 'Deep Learning', 'Neural Network', 
                   'GPT', 'LLM', 'NLP', 'Computer Vision'],
            'Cloud': ['AWS', 'Azure', 'GCP', 'Cloud Computing', 'Docker', 'Kubernetes', 
                     'Serverless', 'Container'],
            'DevOps': ['DevOps', 'CI/CD', 'Jenkins', 'GitLab', 'GitHub Actions', 
                      'Terraform', 'Ansible', 'Prometheus'],
            'Frontend': ['React', 'Vue', 'Angular', 'JavaScript', 'TypeScript', 
                        'CSS', 'HTML', 'Next.js'],
            'Backend': ['Python', 'Java', 'Node.js', 'Go', 'C#', '.NET', 
                       'Ruby', 'PHP', 'Spring Boot'],
            'Database': ['SQL', 'MongoDB', 'PostgreSQL', 'MySQL', 'Redis', 
                        'Elasticsearch', 'Cassandra', 'DynamoDB'],
            'Mobile': ['iOS', 'Android', 'React Native', 'Flutter', 'Swift', 'Kotlin'],
            'Data': ['Data Science', 'Analytics', 'BI', 'Spark', 'Hadoop', 
                    'Tableau', 'Power BI', 'ETL'],
        }
        
    def _detect_tech_category(self, tech_name: str) -> str:
        """Detect technology category"""
        tech_lower = tech_name.lower()
        for category, keywords in self.tech_categories.items():
            if any(kw.lower() in tech_lower for kw in keywords):
                return category
        return "Other"
    
    def _calculate_sentiment(self, text: str) -> float:
        """
        Calculate sentiment score from text (-1 to 1)
        Simple implementation using keyword matching
        """
        positive_keywords = [
            'tăng', 'tốt', 'thành công', 'đột phá', 'nâng cao', 
            'cải thiện', 'mạnh mẽ', 'hiệu quả', 'tiên tiến'
        ]
        negative_keywords = [
            'giảm', 'xấu', 'thất bại', 'lỗi', 'vấn đề', 
            'rủi ro', 'lo ngại', 'yếu', 'kém', 'cảnh báo'
        ]
        
        text_lower = text.lower()
        pos_count = sum(1 for kw in positive_keywords if kw in text_lower)
        neg_count = sum(1 for kw in negative_keywords if kw in text_lower)
        
        total = pos_count + neg_count
        if total == 0:
            return 0.0
        
        return (pos_count - neg_count) / total
    
    def _extract_role_from_context(self, name: str, context: str) -> str:
        """Extract person role from article context"""
        context_lower = context.lower()
        
        role_keywords = {
            'CEO': ['ceo', 'chủ tịch', 'giám đốc điều hành'],
            'CTO': ['cto', 'giám đốc công nghệ', 'chief technology'],
            'Founder': ['sáng lập viên', 'founder', 'nhà sáng lập'],
            'Engineer': ['engineer', 'kỹ sư', 'developer'],
            'Researcher': ['researcher', 'nhà nghiên cứu', 'research'],
            'Manager': ['quản lý', 'manager', 'giám đốc'],
        }
        
        for role, keywords in role_keywords.items():
            if any(kw in context_lower for kw in keywords):
                return role
        
        return "Unknown"
    
    def transform_article(self, raw_article: Dict) -> Tuple[Article, Dict]:
        """
        Transform raw article to Article node and extract entities
        
        Returns:
            Tuple of (Article node, extracted entities dict)
        """
        title = raw_article.get('title', '')
        description = raw_article.get('description', '')
        created_at = raw_article.get('created_at', '')
        platform = raw_article.get('source_platform', 'Unknown')
        entities = raw_article.get('entities', {})
        
        # Parse date
        try:
            published_date = datetime.fromisoformat(created_at)
        except:
            published_date = datetime.now()
        
        # Calculate sentiment
        sentiment_score = self._calculate_sentiment(title + " " + description)
        
        # Create Article node with transformed source name
        article = Article(
            title=title,
            content=description,
            source=get_display_source(platform),
            published_date=published_date,
            sentiment_score=sentiment_score
        )
        
        # Extract and transform entities
        extracted = {
            'technologies': [],
            'companies': [],
            'persons': [],
            'skills': []
        }
        
        # Transform technologies
        for tech_name in entities.get('technologies', []):
            tech = Technology(
                name=tech_name,
                category=self._detect_tech_category(tech_name),
                description=f"Mentioned in {platform}",
                trend_score=1.0  # Will be updated by frequency analysis
            )
            self.technologies.add(tech)
            extracted['technologies'].append(tech)
        
        # Transform organizations to companies
        for org_name in entities.get('organizations', []):
            company = Company(
                name=org_name,
                field="Technology",  # Default, can be enhanced
                location="Unknown",  # Can be extracted from location entities
                rating=sentiment_score
            )
            self.companies.add(company)
            extracted['companies'].append(company)
        
        # Transform persons
        for person_name in entities.get('persons', []):
            person = Person(
                name=person_name,
                role=self._extract_role_from_context(person_name, description)
            )
            self.persons.add(person)
            extracted['persons'].append(person)
        
        # Extract skills from article context (future: use NLP)
        # For now, infer from technologies mentioned
        for tech in extracted['technologies']:
            skill = Skill(
                name=tech.name,
                category=tech.category,
                demand_score=0.7  # Will be updated by frequency analysis
            )
            self.skills.add(skill)
            extracted['skills'].append(skill)
        
        self.articles.append(article)
        
        return article, extracted
    
    def batch_transform(self, json_file_path: str) -> Dict:
        """
        Transform all articles from JSON file
        
        Returns:
            dict with statistics
        """
        logger.info(f"Transforming data from {json_file_path}...")
        
        with open(json_file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        articles_data = data.get('post_detail', [])
        stats = {
            'articles': 0,
            'technologies': 0,
            'companies': 0,
            'persons': 0,
            'skills': 0
        }
        
        for raw_article in articles_data:
            if not raw_article:  # Skip empty entries
                continue
            
            try:
                article, entities = self.transform_article(raw_article)
                stats['articles'] += 1
                logger.debug(f"Transformed: {article.title}")
            except Exception as e:
                logger.warning(f"Error transforming article: {e}")
                continue
        
        stats['technologies'] = len(self.technologies)
        stats['companies'] = len(self.companies)
        stats['persons'] = len(self.persons)
        stats['skills'] = len(self.skills)
        
        return stats
    
    def export_to_json(self, output_dir: str = "data/transformed"):
        """Export transformed data to JSON format"""
        Path(output_dir).mkdir(parents=True, exist_ok=True)
        
        # Export nodes
        nodes_data = {
            'articles': [
                {
                    'title': a.title,
                    'content': a.content[:200],  # Truncate
                    'source': a.source,
                    'published_date': a.published_date.isoformat(),
                    'sentiment_score': a.sentiment_score
                }
                for a in self.articles
            ],
            'technologies': [
                {
                    'name': t.name,
                    'category': t.category,
                    'description': t.description,
                    'trend_score': t.trend_score
                }
                for t in self.technologies
            ],
            'companies': [
                {
                    'name': c.name,
                    'field': c.field,
                    'size': c.size,
                    'location': c.location,
                    'rating': c.rating
                }
                for c in self.companies
            ],
            'persons': [
                {
                    'name': p.name,
                    'role': p.role
                }
                for p in self.persons
            ],
            'skills': [
                {
                    'name': s.name,
                    'category': s.category,
                    'demand_score': s.demand_score
                }
                for s in self.skills
            ]
        }
        
        with open(f'{output_dir}/nodes.json', 'w', encoding='utf-8') as f:
            json.dump(nodes_data, f, ensure_ascii=False, indent=2)
        
        logger.info(f"✅ Exported nodes to {output_dir}/nodes.json")
    
    def get_summary(self) -> str:
        """Get transformation summary"""
        return f"""
╔════════════════════════════════════════╗
║   DATA TRANSFORMATION SUMMARY           ║
╚════════════════════════════════════════╝
📊 Articles:      {len(self.articles)}
🔧 Technologies: {len(self.technologies)}
🏢 Companies:     {len(self.companies)}
👤 Persons:       {len(self.persons)}
💡 Skills:        {len(self.skills)}
"""