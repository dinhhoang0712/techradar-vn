"""
Import transformed data to Neo4j Aura using Job Market Schema
"""
import logging
from typing import Dict, List
from neo4j import GraphDatabase
from neo4j.exceptions import ServiceUnavailable
from dataclasses import dataclass, field
from datetime import datetime

from schema_define import (
    Technology, Company, Skill, Article, Person,
    RelationshipType
)

# Helper classes for multi-source import
@dataclass
class TechNode:
    """Helper Technology Node"""
    name: str
    category: str = ""
    description: str = ""
    trend_score: float = 0.5
    aliases: List[str] = field(default_factory=list)

    def __hash__(self):
        return hash(self.name.lower())

    def __eq__(self, other):
        if not isinstance(other, TechNode):
            return False
        return self.name.lower() == other.name.lower()

@dataclass
class SkillNode:
    """Helper Skill Node"""
    name: str
    category: str = ""
    demand_score: float = 0.5

    def __hash__(self):
        return hash(self.name.lower())

    def __eq__(self, other):
        if not isinstance(other, SkillNode):
            return False
        return self.name.lower() == other.name.lower()

@dataclass
class CompanyNode:
    """Helper Company Node"""
    name: str
    field: str = "Technology"
    size: str = "Unknown"
    location: str = "Unknown"
    rating: float = 0.0

    def __hash__(self):
        return hash(self.name.lower())

    def __eq__(self, other):
        if not isinstance(other, CompanyNode):
            return False
        return self.name.lower() == other.name.lower()

@dataclass
class JobNode:
    """Helper Job Node"""
    title: str
    description: str = ""
    requirement: str = ""
    benefit: str = ""
    salary: str = ""  # Combined salary range as string
    due_date: datetime = None
    source_url: str = ""

logger = logging.getLogger(__name__)

class Neo4jJobImporter:
    """Import Job Market data to Neo4j"""
    
    def __init__(self, uri: str, username: str, password: str, database: str = "neo4j"):
        self.uri = uri
        self.username = username
        self.password = password
        self.database = database
        self.driver = None
        self.technologies = set()
        self.jobs = []
        self.skills = set()
    
    def connect(self) -> bool:
        """Connect to Neo4j"""
        try:
            self.driver = GraphDatabase.driver(self.uri, auth=(self.username, self.password))
            self.driver.verify_connectivity()
            logger.info("✅ Connected to Neo4j Aura")
            return True
        except ServiceUnavailable:
            logger.error("❌ Neo4j unavailable")
            return False
        except Exception as e:
            logger.error(f"❌ Connection error: {e}")
            return False
    
    def disconnect(self):
        """Close connection"""
        if self.driver:
            self.driver.close()
            logger.info("Disconnected from Neo4j")
    
    def create_constraints_and_indexes(self) -> bool:
        """Create constraints and indexes for all node types"""
        try:
            with self.driver.session(database=self.database) as session:
                constraints = [
                    "CREATE CONSTRAINT IF NOT EXISTS FOR (a:Article) REQUIRE a.title IS UNIQUE",
                    "CREATE CONSTRAINT IF NOT EXISTS FOR (t:Technology) REQUIRE t.name IS UNIQUE",
                    "CREATE CONSTRAINT IF NOT EXISTS FOR (c:Company) REQUIRE c.name IS UNIQUE",
                    "CREATE CONSTRAINT IF NOT EXISTS FOR (j:Job) REQUIRE j.title IS UNIQUE",
                    "CREATE CONSTRAINT IF NOT EXISTS FOR (s:Skill) REQUIRE s.name IS UNIQUE",
                    "CREATE CONSTRAINT IF NOT EXISTS FOR (p:Person) REQUIRE p.name IS UNIQUE",
                ]

                for constraint in constraints:
                    session.run(constraint)
                    logger.info(f"✅ {constraint.split('FOR')[1][:30]}...")

                # Property indexes for common filters
                indexes = [
                    "CREATE INDEX article_published_date IF NOT EXISTS FOR (a:Article) ON (a.published_date)",
                    "CREATE INDEX article_source IF NOT EXISTS FOR (a:Article) ON (a.source)",
                    "CREATE INDEX technology_category IF NOT EXISTS FOR (t:Technology) ON (t.category)",
                    "CREATE INDEX skill_category IF NOT EXISTS FOR (s:Skill) ON (s.category)",
                    "CREATE INDEX company_location IF NOT EXISTS FOR (c:Company) ON (c.location)",
                ]
                for index in indexes:
                    session.run(index)

                # Vector index used by ai-rag-core RAG retrieval (768-dim, cosine).
                # Mirrors services/ai-rag-core/scripts/create_vector_index.py.
                session.run(
                    "CREATE VECTOR INDEX article_embedding_index IF NOT EXISTS "
                    "FOR (a:Article) ON (a.embedding) "
                    "OPTIONS {indexConfig: {`vector.dimensions`: 768, "
                    "`vector.similarity_function`: 'cosine'}}"
                )
                logger.info("✅ Property + vector indexes created")

            logger.info("✅ All constraints and indexes created")
            return True
        except Exception as e:
            logger.error(f"✗ Error creating constraints: {e}")
            return False

    def import_technologies_list(self, technologies: List[TechNode]) -> int:
        """Import multiple Technology nodes"""
        try:
            with self.driver.session(database=self.database) as session:
                for tech in technologies:
                    session.run(
                        """
                        MERGE (t:Technology {name: $name})
                        ON CREATE SET
                            t.category = $category,
                            t.description = $description,
                            t.trend_score = $trend_score
                        """,
                        parameters={
                            'name': tech.name,
                            'category': tech.category,
                            'description': tech.description,
                            'trend_score': tech.trend_score
                        }
                    )
            logger.info(f"✅ Imported {len(technologies)} technologies")
            return len(technologies)
        except Exception as e:
            logger.error(f"✗ Error importing technologies: {e}")
            return 0

    def import_companies_list(self, companies: List[CompanyNode]) -> int:
        """Import multiple Company nodes"""
        try:
            with self.driver.session(database=self.database) as session:
                for company in companies:
                    session.run(
                        """
                        MERGE (c:Company {name: $name})
                        ON CREATE SET
                            c.field = $field,
                            c.size = $size,
                            c.location = $location,
                            c.rating = $rating
                        ON MATCH SET
                            c.location = CASE 
                                WHEN c.location = "Unknown" OR c.location = "" 
                                THEN $location 
                                ELSE c.location 
                            END
                        """,
                        parameters={
                            'name': company.name,
                            'field': company.field,
                            'size': company.size,
                            'location': company.location,
                            'rating': company.rating
                        }
                    )
            logger.info(f"✅ Imported {len(companies)} companies")
            return len(companies)
        except Exception as e:
            logger.error(f"✗ Error importing companies: {e}")
            return 0

    def import_jobs_list(self, jobs: List[JobNode]) -> int:
        """Import multiple Job nodes"""
        try:
            with self.driver.session(database=self.database) as session:
                for job in jobs:
                    session.run(
                        """
                        MERGE (j:Job {title: $title})
                        ON CREATE SET
                            j.description = $description,
                            j.requirement = $requirement,
                            j.benefit = $benefit,
                            j.salary = $salary,
                            j.due_date = $due_date,
                            j.source_url = $source_url
                        """,
                        parameters={
                            'title': job.title,
                            'description': job.description,
                            'requirement': job.requirement,
                            'benefit': job.benefit,
                            'salary': job.salary,
                            'due_date': job.due_date.isoformat() if job.due_date else None,
                            'source_url': job.source_url
                        }
                    )
            logger.info(f"✅ Imported {len(jobs)} jobs")
            return len(jobs)
        except Exception as e:
            logger.error(f"✗ Error importing jobs: {e}")
            return 0

    def import_skills_list(self, skills: List[SkillNode]) -> int:
        """Import multiple Skill nodes"""
        try:
            with self.driver.session(database=self.database) as session:
                for skill in skills:
                    session.run(
                        """
                        MERGE (s:Skill {name: $name})
                        ON CREATE SET
                            s.category = $category,
                            s.demand_score = $demand_score
                        """,
                        parameters={
                            'name': skill.name,
                            'category': skill.category,
                            'demand_score': skill.demand_score
                        }
                    )
            logger.info(f"✅ Imported {len(skills)} skills")
            return len(skills)
        except Exception as e:
            logger.error(f"✗ Error importing skills: {e}")
            return 0

    def create_article_mentions_relationships_list(self, articles: List[Article], technologies: List[TechNode], companies: List[CompanyNode]) -> int:
        """Create MENTIONS relationships: Article → Technology/Company"""
        try:
            with self.driver.session(database=self.database) as session:
                rel_count = 0

                for article in articles:
                    # Article mentions Technologies
                    for tech in technologies:
                        if tech.name.lower() in article.content.lower() or \
                           tech.name.lower() in article.title.lower():
                            session.run(
                                """
                                MATCH (a:Article {title: $article_title})
                                MATCH (t:Technology {name: $tech_name})
                                MERGE (a)-[:MENTIONS]->(t)
                                """,
                                parameters={
                                    'article_title': article.title,
                                    'tech_name': tech.name
                                }
                            )
                            rel_count += 1

                    # Article mentions Companies
                    for company in companies:
                        if company.name.lower() in article.content.lower() or \
                           company.name.lower() in article.title.lower():
                            session.run(
                                """
                                MATCH (a:Article {title: $article_title})
                                MATCH (c:Company {name: $company_name})
                                MERGE (a)-[:MENTIONS]->(c)
                                """,
                                parameters={
                                    'article_title': article.title,
                                    'company_name': company.name
                                }
                            )
                            rel_count += 1

            logger.info(f"✅ Created {rel_count} MENTIONS relationships")
            return rel_count
        except Exception as e:
            logger.error(f"✗ Error creating relationships: {e}")
            return 0
    
    def import_articles(self, articles: List[Article]) -> int:
        """Import Article nodes"""
        try:
            with self.driver.session(database=self.database) as session:
                for article in articles:
                    session.run(
                        """
                        MERGE (a:Article {title: $title})
                        ON CREATE SET
                            a.content = $content,
                            a.source = $source,
                            a.published_date = $published_date,
                            a.sentiment_score = $sentiment_score
                        ON MATCH SET
                            a.content = CASE WHEN a.content = "" OR a.content IS NULL THEN $content ELSE a.content END
                        """,
                        parameters={
                            'title': article.title,
                            'content': article.content,
                            'source': article.source,
                            'published_date': article.published_date.isoformat(),
                            'sentiment_score': article.sentiment_score
                        }
                    )
            
            logger.info(f"✅ Imported {len(articles)} articles")
            return len(articles)
        except Exception as e:
            logger.error(f"✗ Error importing articles: {e}")
            return 0
    
    def import_technologies(self, technologies: List[Technology]) -> int:
        """Import Technology nodes"""
        try:
            with self.driver.session(database=self.database) as session:
                for tech in technologies:
                    session.run(
                        """
                        MERGE (t:Technology {name: $name})
                        ON CREATE SET
                            t.category = $category,
                            t.description = $description,
                            t.trend_score = $trend_score
                        """,
                        parameters={
                            'name': tech.name,
                            'category': tech.category,
                            'description': tech.description,
                            'trend_score': tech.trend_score
                        }
                    )
            
            logger.info(f"✅ Imported {len(technologies)} technologies")
            return len(technologies)
        except Exception as e:
            logger.error(f"✗ Error importing technologies: {e}")
            return 0
    
    def import_companies(self, companies: List[Company]) -> int:
        """Import Company nodes"""
        try:
            with self.driver.session(database=self.database) as session:
                for company in companies:
                    session.run(
                        """
                        MERGE (c:Company {name: $name})
                        ON CREATE SET
                            c.industry = $industry,
                            c.size = $size,
                            c.location = $location,
                            c.rating = $rating
                        ON MATCH SET
                            c.location = CASE 
                                WHEN c.location = "Unknown" OR c.location = "" 
                                THEN $location 
                                ELSE c.location 
                            END
                        """,
                        parameters={
                            'name': company.name,
                            'industry': company.industry,
                            'size': company.size,
                            'location': company.location,
                            'rating': company.rating
                        }
                    )
            
            logger.info(f"✅ Imported {len(companies)} companies")
            return len(companies)
        except Exception as e:
            logger.error(f"✗ Error importing companies: {e}")
            return 0
    
    def import_persons(self, persons: List[Person]) -> int:
        """Import Person nodes"""
        try:
            with self.driver.session(database=self.database) as session:
                for person in persons:
                    session.run(
                        """
                        MERGE (p:Person {name: $name})
                        ON CREATE SET
                            p.role = $role
                        """,
                        parameters={
                            'name': person.name,
                            'role': person.role
                        }
                    )
            
            logger.info(f"✅ Imported {len(persons)} persons")
            return len(persons)
        except Exception as e:
            logger.error(f"✗ Error importing persons: {e}")
            return 0
    
    def import_skills(self, skills: List[Skill]) -> int:
        """Import Skill nodes"""
        try:
            with self.driver.session(database=self.database) as session:
                for skill in skills:
                    session.run(
                        """
                        MERGE (s:Skill {name: $name})
                        ON CREATE SET
                            s.category = $category,
                            s.demand_score = $demand_score
                        """,
                        parameters={
                            'name': skill.name,
                            'category': skill.category,
                            'demand_score': skill.demand_score
                        }
                    )
            
            logger.info(f"✅ Imported {len(skills)} skills")
            return len(skills)
        except Exception as e:
            logger.error(f"✗ Error importing skills: {e}")
            return 0

    def verify_relationships(self) -> Dict[str, int]:
        """
        Verify all relationship types exist
        
        Returns:
            dict: Count of each relationship type
        """
        try:
            with self.driver.session(database=self.database) as session:
                relationship_types = [
                    'MENTIONS', 'USES', 'RELATED_TO', 
                    'WORKS_AT', 'WROTE', 'IS_TECHNOLOGY'
                ]
                
                rel_stats = {}
                for rel_type in relationship_types:
                    result = session.run(
                        f"MATCH ()-[r:{rel_type}]->() RETURN count(r) as count"
                    )
                    record = result.single()
                    count = record['count'] if record else 0
                    rel_stats[rel_type] = count
                    
                    if count > 0:
                        logger.info(f"✅ {rel_type:15s}: {count:5d}")
                    else:
                        logger.warning(f"⚠️  {rel_type:15s}: {count:5d} (MISSING)")
                
                return rel_stats
        except Exception as e:
            logger.error(f"✗ Error verifying relationships: {e}")
            return {}

    def create_company_uses_technology_relationships(self) -> int:
        """
        Create USES relationships: Company -> Technology
        Based on article mentions co-occurrence
        """
        try:
            with self.driver.session(database=self.database) as session:
                # Heuristic: Nếu article mention cả company và technology
                # → có khả năng company uses technology đó
                result = session.run(
                    """
                    MATCH (a:Article)-[:MENTIONS]->(c:Company)
                    MATCH (a)-[:MENTIONS]->(t:Technology)
                    WITH c, t, count(a) as co_mention_count
                    WHERE co_mention_count >= 2
                    MERGE (c)-[r:USES {frequency: co_mention_count}]->(t)
                    RETURN count(r) as rel_count
                    """
                )
                record = result.single()
                rel_count = record['rel_count'] if record else 0
                
                logger.info(f"✅ Created {rel_count} USES relationships")
                return rel_count
        except Exception as e:
            logger.error(f"✗ Error creating USES relationships: {e}")
            return 0
    
    def create_technology_related_to_relationships(self) -> int:
        """
        Create RELATED_TO relationships: Technology -> Technology
        Based on article co-mentions
        """
        try:
            with self.driver.session(database=self.database) as session:
                result = session.run(
                    """
                    MATCH (a:Article)-[:MENTIONS]->(t1:Technology)
                    MATCH (a)-[:MENTIONS]->(t2:Technology)
                    WHERE t1.name < t2.name
                    WITH t1, t2, count(a) as co_mention_count
                    WHERE co_mention_count >= 1
                    MERGE (t1)-[r:RELATED_TO {frequency: co_mention_count}]->(t2)
                    RETURN count(r) as rel_count
                    """
                )
                record = result.single()
                rel_count = record['rel_count'] if record else 0
                
                logger.info(f"✅ Created {rel_count} RELATED_TO relationships")
                return rel_count
        except Exception as e:
            logger.error(f"✗ Error creating RELATED_TO relationships: {e}")
            return 0
    
    def create_person_works_at_relationships(self) -> int:
        """
        Create WORKS_AT relationships: Person -> Company
        Based on article mentions patterns
        """
        try:
            with self.driver.session(database=self.database) as session:
                # Nếu person và company được mention trong cùng article
                # nhiều lần → có khả năng person works at company
                result = session.run(
                    """
                    MATCH (a:Article)-[:MENTIONS]->(p:Person)
                    MATCH (a)-[:MENTIONS]->(c:Company)
                    WITH p, c, count(a) as mention_count
                    WHERE mention_count >= 2
                    MERGE (p)-[r:WORKS_AT {confidence: mention_count}]->(c)
                    RETURN count(r) as rel_count
                    """
                )
                record = result.single()
                rel_count = record['rel_count'] if record else 0
                
                logger.info(f"✅ Created {rel_count} WORKS_AT relationships")
                return rel_count
        except Exception as e:
            logger.error(f"✗ Error creating WORKS_AT relationships: {e}")
            return 0
    
    def create_person_wrote_article_relationships(self) -> int:
        """
        Create WROTE relationships: Person -> Article
        Based on heuristic: if person mentioned in article, likely involved
        """
        try:
            with self.driver.session(database=self.database) as session:
                result = session.run(
                    """
                    MATCH (a:Article)-[:MENTIONS]->(p:Person)
                    MERGE (p)-[:WROTE]->(a)
                    RETURN count(*) as rel_count
                    """
                )
                record = result.single()
                rel_count = record['rel_count'] if record else 0
                
                logger.info(f"✅ Created {rel_count} WROTE relationships")
                return rel_count
        except Exception as e:
            logger.error(f"✗ Error creating WROTE relationships: {e}")
            return 0
    
    def create_skill_relationships(self, transformer) -> int:
        """
        Create relationships between Skills and Technologies
        Inferred from technology mentions
        """
        try:
            with self.driver.session(database=self.database) as session:
                rel_count = 0
                
                # Skill có cùng tên với Technology → RELATED
                for skill in transformer.skills:
                    result = session.run(
                        """
                        MATCH (s:Skill {name: $skill_name})
                        MATCH (t:Technology {name: $tech_name})
                        MERGE (s)-[:IS_TECHNOLOGY]->(t)
                        RETURN count(*) as count
                        """,
                        parameters={
                            'skill_name': skill.name,
                            'tech_name': skill.name
                        }
                    )
                    record = result.single()
                    if record:
                        rel_count += record['count'] if record['count'] else 0
                
                logger.info(f"✅ Created {rel_count} Skill-Technology relationships")
                return rel_count
        except Exception as e:
            logger.error(f"✗ Error creating skill relationships: {e}")
            return 0

    def get_statistics(self) -> Dict[str, int]:
        """Get database statistics"""
        try:
            with self.driver.session(database=self.database) as session:
                stats = {}
                
                node_types = ['Article', 'Technology', 'Company', 'Skill', 'Person', 'Job']
                for node_type in node_types:
                    result = session.run(f"MATCH (n:{node_type}) RETURN count(n) as count")
                    record = result.single()
                    stats[node_type] = record['count'] if record else 0
                
                result = session.run("MATCH ()-[r]->() RETURN count(r) as count")
                record = result.single()
                stats['Relationships'] = record['count'] if record else 0
                
                return stats
        except Exception as e:
            logger.error(f"✗ Error getting statistics: {e}")
            return {}

