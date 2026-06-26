"""
Fixed version with proper relationships between Job, Company, Person nodes
"""
import logging
import os
from typing import Dict, List
from datetime import datetime
import json
import sys
from pathlib import Path
from dataclasses import dataclass

# Add this script's own directory (utils/) to the path so the sibling modules
# `neo4j_config` / `database_connection` resolve regardless of the cwd.
sys.path.insert(0, str(Path(__file__).resolve().parent))

from neo4j_config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE, BATCH_SIZE
from database_connection import Neo4jJobImporter, TechNode, CompanyNode, JobNode, SkillNode, Person

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


# Define Article and Job classes
@dataclass
class Article:
    """Article Node"""
    title: str
    content: str
    source: str
    published_date: datetime
    sentiment_score: float = 0.0

    def __hash__(self):
        return hash(self.title.lower())


class RelationshipBuilder:
    """Build relationships between nodes"""

    def __init__(self):
        self.technologies = []
        self.companies = []
        self.skills = []
        self.articles = []
        self.persons = set()
        self.jobs = []
        self._job_role_cache = set()
        self._tech_category_map = {}
        self._job_company_map = {}
        self._job_tech_map = {}
        self._job_skills_map = {}
        self._article_company_map = {}
        self._article_tech_map = {}

    def _detect_tech_category(self, tech_name: str) -> str:
        """Detect technology category"""
        tech_lower = tech_name.lower()
        for category, keywords in self._tech_category_map.items():
            if any(kw.lower() in tech_lower for kw in keywords):
                return category
        return "Other"

    COMPANY_BLACKLIST = {
        'Việt Nam', 'Hà Nội', 'TPHCM', 'TP.HCM', 'Mỹ', 'Mỹ.', 'Nhật Bản', 'Trung Quốc',
        'TRUNG Quốc', 'Lầu Năm Góc', 'ba', 'CEO', '##ic', '##AI', '##hr', 'Open', 'Ant', 
        'Linked', '90', '40', 'Công nghệ', 'Báo', 'Reuters', 'Bloomberg', 'CNBC'
    }

    PERSON_BLACKLIST = {
        'Claude', 'ChatGPT', 'Anthropic', 'OpenAI', 'Google', 'Meta', 'Microsoft', 
        'Amazon', 'Intel', 'Apple', 'NVIDIA', 'Reuters', 'Bloomberg', 'CNBC', 
        'The Verge', 'Guardian', 'TechCrunch', 'WSJ', 'Bloomberg', 'FT'
    }

    def _add_company(self, company: CompanyNode) -> bool:
        """Add company and update location if better info found"""
        for existing in self.companies:
            if existing.name.lower() == company.name.lower():
                if (existing.location == "Unknown" or not existing.location) and company.location != "Unknown":
                    existing.location = company.location
                return False
        self.companies.append(company)
        return True

    def _add_if_not_exists(self, lst, item):
        """Add item to list if it doesn't already exist"""
        if not any(existing.name == item.name for existing in lst):
            lst.append(item)

    def _parse_date(self, date_str: str) -> datetime:
        """Parse date string in various formats"""
        date_formats = [
            '%d/%m/%Y',
            '%Y-%m-%d',
            '%Y-%m-%d %H:%M:%S',
            '%d/%m/%Y %H:%M:%S',
        ]

        for fmt in date_formats:
            try:
                return datetime.strptime(date_str, fmt)
            except ValueError:
                continue

        logger.warning(f"Could not parse date: {date_str}")
        return datetime.now()

    def _extract_job_level(self, job_title: str) -> str:
        """Extract job level from job title"""
        title_lower = job_title.lower()

        if 'fresher' in title_lower or 'thực tập sinh' in title_lower:
            return 'fresher'
        elif 'junior' in title_lower or 'nhân viên mới' in title_lower or 'nhân viên' in title_lower:
            return 'junior'
        elif 'mid' in title_lower or 'trung cấp' in title_lower:
            return 'mid'
        elif 'senior' in title_lower or 'trưởng nhóm' in title_lower or 'làm việc trực tiếp' in title_lower:
            return 'senior'
        elif 'lead' in title_lower or 'team lead' in title_lower or 'kiến trúc sư' in title_lower:
            return 'lead'

        return 'mid'

    def _parse_salary(self, salary_str: str) -> tuple:
        """Parse salary string and return (min, max) in millions VND"""
        salary_str = salary_str.lower()

        if 'tr' in salary_str or 'triệu' in salary_str:
            import re
            numbers = re.findall(r'\d+', salary_str)
            if len(numbers) >= 2:
                return int(numbers[0]), int(numbers[1])

        import re
        numbers = re.findall(r'\d+', salary_str)
        if len(numbers) >= 2:
            return int(numbers[0]), int(numbers[1])

        return None, None

    def import_news_data(self, data_path: str, source_type: str) -> Dict:
        """Import news data from JSON file"""
        logger.info(f"🔄 Importing {source_type} data from {data_path}...")

        with open(data_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        post_detail = data.get('post_detail', [])
        source_platform = data.get('source_platform', source_type)

        stats = {
            'articles': 0,
            'technologies': 0,
            'companies': 0,
            'job_roles': 0,
            'skills': 0,
            'total_articles': len(post_detail)
        }

        for raw_article in post_detail:
            if not raw_article or 'title' not in raw_article:
                continue

            try:
                created_at = raw_article.get('created_at', '')
                try:
                    published_date = self._parse_date(created_at)
                except:
                    published_date = datetime.now()

                # Create Article node with transformed source name
                article = Article(
                    title=raw_article.get('title', ''),
                    content=raw_article.get('content', raw_article.get('description', '')),
                    source=get_display_source(source_platform),
                    published_date=published_date,
                    sentiment_score=0.0
                )
                self.articles.append(article)

                # Extract entities
                entities = raw_article.get('entities', {})

                # Process Technology entities. The NER pipeline (extract_data.py
                # → group_entities) only emits a TECH key; there is no SKILL/TECH
                # group for news, so skills are sourced from TopCV postings only.
                tech_names = entities.get('TECH', [])

                for tech_name in tech_names:
                    tech = TechNode(
                        name=tech_name,
                        category=self._detect_tech_category(tech_name),
                        description=f"Mentioned in {source_platform}",
                        trend_score=1.0
                    )
                    self._add_if_not_exists(self.technologies, tech)

                # Process Organization entities (ORG)
                org_names = entities.get('ORG', [])
                loc_names = entities.get('LOC', [])
                primary_location = loc_names[0] if loc_names else "Unknown"

                # Filter organization names
                valid_org_names = [
                    name for name in org_names 
                    if name not in self.COMPANY_BLACKLIST and len(name) > 2
                ]

                for org_name in valid_org_names:
                    company = CompanyNode(
                        name=org_name,
                        field="Technology",
                        location=primary_location,
                        rating=0.0
                    )
                    self._add_company(company)
                    # Map article to company
                    self._article_company_map[article.title] = org_name

                # Process Job Role entities (JOB_ROLE)
                job_roles = entities.get('JOB_ROLE', [])
                for job_name in job_roles:
                    job_role_key = (job_name.lower(), "")
                    if job_role_key not in self._job_role_cache:
                        # Job roles mentioned in news only carry a title; the rich
                        # fields (salary/requirement/...) come from TopCV postings.
                        job = JobNode(title=job_name)
                        self.jobs.append(job)
                        self._job_role_cache.add(job_role_key)

                # Process Person entities (PER)
                per_names = entities.get('PER', [])
                for per_name in per_names:
                    if per_name not in self.PERSON_BLACKLIST and len(per_name) > 3:
                        person = Person(name=per_name, role="Unknown")
                        self.persons.add(person)

                stats['articles'] += 1

            except Exception as e:
                logger.warning(f"Error processing article: {e}")
                continue

        stats['technologies'] = len(self.technologies)
        stats['companies'] = len(self.companies)
        stats['job_roles'] = len(self.jobs)
        stats['skills'] = len(self.skills)

        logger.info(f"✅ Imported {stats['articles']} articles from {source_type}")
        logger.info(f"   - Technologies: {stats['technologies']}")
        logger.info(f"   - Companies: {stats['companies']}")
        logger.info(f"   - Job Roles: {stats['job_roles']}")
        logger.info(f"   - Skills: {stats['skills']}")

        return stats

    def import_topcv_data(self, data_path: str) -> Dict:
        """Import job postings from TopCV - new format with job, company, skills, technologies"""
        logger.info(f"🔄 Importing TopCV data from {data_path}...")

        with open(data_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        post_detail = data.get('post_detail', [])
        source_platform = data.get('source_platform', 'TopCV')

        stats = {
            'job_postings': 0,
            'technologies': 0,
            'job_roles': 0,
            'skills': 0,
            'companies': 0
        }

        self._job_role_cache = set()

        for job_posting in post_detail:
            if not job_posting:
                continue

            try:
                # New format: { job: {...}, company: {...}, skills: [...], technologies: [...] }
                job_data = job_posting.get('job', {})
                company_data = job_posting.get('company', {})
                skills_list = job_posting.get('skills', [])
                technologies_list = job_posting.get('technologies', [])

                if not job_data or 'title' not in job_data:
                    continue

                job_title = job_data.get('title', '')
                job_role_key = (job_title.lower(), "")
                
                if job_role_key not in self._job_role_cache:
                    # Parse due_date
                    due_date_str = job_data.get('due_date', '')
                    due_date = None
                    if due_date_str:
                        try:
                            due_date = self._parse_date(due_date_str)
                        except:
                            due_date = None

                    # Create Job node with new schema
                    job = JobNode(
                        title=job_title,
                        description=job_data.get('description', ''),
                        requirement=job_data.get('requirement', ''),
                        benefit=job_data.get('benefit', ''),
                        salary=job_data.get('salary', ''),
                        due_date=due_date,
                        source_url=job_data.get('source_url', '')
                    )
                    self.jobs.append(job)
                    self._job_role_cache.add(job_role_key)

                # Process Company
                if company_data and 'name' in company_data:
                    company_name = company_data.get('name', '')
                    if company_name and company_name not in self.COMPANY_BLACKLIST:
                        company = CompanyNode(
                            name=company_name,
                            field=company_data.get('field', 'Technology'),
                            size=company_data.get('size', 'Unknown'),
                            location=company_data.get('location', 'Unknown'),
                            rating=0.0
                        )
                        self._add_company(company)
                        # Map job to company
                        self._job_company_map[job_title] = company_name

                # Process Skills
                for skill_name in skills_list:
                    if skill_name:
                        skill = SkillNode(
                            name=skill_name,
                            category="General",
                            demand_score=0.7
                        )
                        self._add_if_not_exists(self.skills, skill)
                        self._job_skills_map[job_title] = self._job_skills_map.get(job_title, []) + [skill_name]

                # Process Technologies
                for tech_name in technologies_list:
                    if tech_name:
                        tech = TechNode(
                            name=tech_name,
                            category=self._detect_tech_category(tech_name),
                            description=f"Required in {source_platform}",
                            trend_score=1.0
                        )
                        self._add_if_not_exists(self.technologies, tech)
                        self._job_tech_map[job_title] = self._job_tech_map.get(job_title, []) + [tech_name]

                stats['job_postings'] += 1

            except Exception as e:
                logger.warning(f"Error processing job posting: {e}")
                continue

        stats['technologies'] = len(self.technologies)
        stats['job_roles'] = len(self.jobs)
        stats['skills'] = len(self.skills)
        stats['companies'] = len(self.companies)

        logger.info(f"✅ Imported {stats['job_postings']} job postings from TopCV")
        logger.info(f"   - Technologies: {stats['technologies']}")
        logger.info(f"   - Job Roles: {stats['job_roles']}")
        logger.info(f"   - Skills: {stats['skills']}")
        logger.info(f"   - Companies: {stats['companies']}")

        return stats

    def run_import_pipeline(self, news_paths: List[str], topcv_path: str = None) -> Dict:
        """Run complete import pipeline"""
        print("\n" + "="*70)
        print("🚀 MULTI-SOURCE DATA IMPORT PIPELINE TO NEO4J AURA")
        print("="*70 + "\n")

        stats = {}

        # Step 1: Import News Data
        if news_paths:
            print("📂 STEP 1: Importing News Data\n")
            for news_path in news_paths:
                if os.path.exists(news_path):
                    source_type = news_path.split('/')[-1].replace('extracted_data_', '').replace('.json', '')
                    source_stats = self.import_news_data(news_path, source_type)
                    stats[source_type] = source_stats
                else:
                    logger.warning(f"News file not found: {news_path}")

        # Step 2: Import TopCV Data
        if topcv_path and os.path.exists(topcv_path):
            print("\n📂 STEP 2: Importing TopCV Job Data\n")
            topcv_stats = self.import_topcv_data(topcv_path)
            stats['topcv'] = topcv_stats

        # Step 3: Transform and Prepare for Import
        print("\n📂 STEP 3: Transforming Data to Job Market Schema\n")

        # Step 4: Connect to Neo4j
        print("\n📂 STEP 4: Connecting to Neo4j Aura\n")
        importer = Neo4jJobImporter(NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE)

        if not importer.connect():
            logger.error("Failed to connect to Neo4j Aura")
            return stats

        print("📂 STEP 5: Creating Constraints and Indexes\n")
        importer.create_constraints_and_indexes()

        # Step 6: Import Nodes
        print("\n📂 STEP 6: Importing Nodes\n")

        tech_list = self.technologies
        importer.import_technologies_list(tech_list)

        company_list = self.companies
        importer.import_companies_list(company_list)

        job_role_list = self.jobs
        importer.import_jobs_list(job_role_list)

        skill_list = self.skills
        importer.import_skills_list(skill_list)

        person_list = self.persons
        importer.import_persons(person_list)

        # Step 7: Import Articles
        article_list = self.articles
        importer.import_articles(article_list)

        # Step 8: Create Relationships
        print("\n📂 STEP 7: Creating Relationships\n")
        self.create_article_mentions_relationships(importer)
        self.create_job_company_relationships(importer)
        self.create_job_tech_relationships(importer)
        self.create_job_skill_relationships(importer)

        # Step 8b: Derived relationships (co-mention heuristics + authorship/employment).
        # These were implemented but never wired in; they complete the graph schema.
        print("\n📂 STEP 7b: Creating Derived Relationships\n")
        importer.create_company_uses_technology_relationships()    # (Company)-[:USES]->(Technology)
        importer.create_technology_related_to_relationships()      # (Technology)-[:RELATED_TO]->(Technology)
        importer.create_person_works_at_relationships()            # (Person)-[:WORKS_AT]->(Company)
        importer.create_person_wrote_article_relationships()       # (Person)-[:WROTE]->(Article)

        # Step 9: Print Final Statistics
        print("\n📊 FINAL IMPORT STATISTICS\n")
        stats = importer.get_statistics()

        for node_type, count in stats.items():
            if isinstance(count, int):
                label = node_type if node_type == 'Relationships' else node_type
                print(f"   {label:15s}: {count:5d}")

        # Disconnect
        importer.disconnect()

        print("\n" + "="*70)
        print("✅ PIPELINE COMPLETED SUCCESSFULLY!")
        print("="*70 + "\n")

        return stats

    def create_article_mentions_relationships(self, importer: Neo4jJobImporter):
        """Create MENTIONS relationships: Article → Technology/Company using batch processing"""
        logger.info("Creating Article → Technology/Company relationships...")

        with importer.driver.session(database=NEO4J_DATABASE) as session:
            # Batch create Article → Technology relationships
            # Pre-compute matches to avoid N+1 queries
            article_tech_pairs = []
            for article in self.articles:
                for tech in self.technologies:
                    if tech.name.lower() in article.content.lower() or \
                       tech.name.lower() in article.title.lower():
                        article_tech_pairs.append({
                            'article_title': article.title,
                            'tech_name': tech.name
                        })

            if article_tech_pairs:
                session.run(
                    """
                    UNWIND $pairs AS pair
                    MATCH (a:Article {title: pair.article_title})
                    MATCH (t:Technology {name: pair.tech_name})
                    MERGE (a)-[:MENTIONS]->(t)
                    """,
                    parameters={'pairs': article_tech_pairs}
                )
                logger.info(f"   ✅ Created {len(article_tech_pairs)} Article → Technology relationships")

            # Batch create Article → Company relationships
            article_company_pairs = []
            for article in self.articles:
                company_name = self._article_company_map.get(article.title)
                if company_name:
                    article_company_pairs.append({
                        'article_title': article.title,
                        'company_name': company_name
                    })

            if article_company_pairs:
                session.run(
                    """
                    UNWIND $pairs AS pair
                    MATCH (a:Article {title: pair.article_title})
                    MATCH (c:Company {name: pair.company_name})
                    MERGE (a)-[:MENTIONS]->(c)
                    """,
                    parameters={'pairs': article_company_pairs}
                )
                logger.info(f"   ✅ Created {len(article_company_pairs)} Article → Company relationships")

        logger.info(f"✅ Completed Article → Technology/Company relationships")

    def create_job_company_relationships(self, importer: Neo4jJobImporter):
        """Create HIRES_FOR relationships: Job → Company using batch processing"""
        logger.info("Creating Job → Company relationships...")

        # Build pairs list
        job_company_pairs = []
        for job in self.jobs:
            company_name = self._job_company_map.get(job.title)
            if company_name:
                job_company_pairs.append({
                    'job_title': job.title,
                    'company_name': company_name
                })

        if job_company_pairs:
            with importer.driver.session(database=NEO4J_DATABASE) as session:
                session.run(
                    """
                    UNWIND $pairs AS pair
                    MATCH (j:Job {title: pair.job_title})
                    MATCH (c:Company {name: pair.company_name})
                    MERGE (j)-[:HIRES_FOR]->(c)
                    """,
                    parameters={'pairs': job_company_pairs}
                )

        logger.info(f"✅ Created {len(job_company_pairs)} Job → Company relationships")

    def create_job_tech_relationships(self, importer: Neo4jJobImporter):
        """Create REQUIRES relationships: Job → Technology using batch processing"""
        logger.info("Creating Job → Technology relationships...")

        # Build pairs list
        job_tech_pairs = []
        for job in self.jobs:
            tech_names = self._job_tech_map.get(job.title, [])
            for tech_name in tech_names:
                job_tech_pairs.append({
                    'job_title': job.title,
                    'tech_name': tech_name
                })

        if job_tech_pairs:
            with importer.driver.session(database=NEO4J_DATABASE) as session:
                session.run(
                    """
                    UNWIND $pairs AS pair
                    MATCH (j:Job {title: pair.job_title})
                    MATCH (t:Technology {name: pair.tech_name})
                    MERGE (j)-[:REQUIRES {is_mandatory: true, frequency: 1}]->(t)
                    """,
                    parameters={'pairs': job_tech_pairs}
                )

        logger.info(f"✅ Created {len(job_tech_pairs)} Job → Technology relationships")

    def create_job_skill_relationships(self, importer: Neo4jJobImporter):
        """Create REQUIRES relationships: Job → Skill using batch processing"""
        logger.info("Creating Job → Skill relationships...")

        # Build pairs list
        job_skill_pairs = []
        for job in self.jobs:
            skill_names = self._job_skills_map.get(job.title, [])
            for skill_name in skill_names:
                job_skill_pairs.append({
                    'job_title': job.title,
                    'skill_name': skill_name
                })

        if job_skill_pairs:
            with importer.driver.session(database=NEO4J_DATABASE) as session:
                session.run(
                    """
                    UNWIND $pairs AS pair
                    MATCH (j:Job {title: pair.job_title})
                    MATCH (s:Skill {name: pair.skill_name})
                    MERGE (j)-[:REQUIRES {is_mandatory: true, frequency: 1}]->(s)
                    """,
                    parameters={'pairs': job_skill_pairs}
                )

        logger.info(f"✅ Created {len(job_skill_pairs)} Job → Skill relationships")


def find_latest_data_files(extracted_dir: Path) -> tuple:
    """
    Tìm các file dữ liệu mới nhất trong ngày hiện tại.
    Ưu tiên file theo format ngày DD_MM_YYYY_*.json, sau đó mới đến extracted_data_phobert_*.json
    
    Returns:
        tuple: (news_paths, topcv_path) - danh sách file news và file TopCV
    """
    today = datetime.now()
    today_str = today.strftime("%d_%m_%Y")
    
    news_paths = []
    topcv_path = None
    
    # 1. Tìm file theo format ngày mới: DD_MM_YYYY_SourceName.json
    today_files = list(extracted_dir.glob(f"{today_str}_*.json"))
    
    if today_files:
        logger.info(f"Found {len(today_files)} files for today ({today_str}): {[p.name for p in today_files]}")
        
        for f in today_files:
            if "topCV" in f.name or "topcv" in f.name.lower():
                topcv_path = str(f)
                logger.info(f"Found TopCV data: {f.name}")
            else:
                news_paths.append(str(f))
    
    # 2. Nếu không có file hôm nay, tìm file PhoBERT-processed (chỉ cho news)
    if not news_paths:
        phobert_files = list(extracted_dir.glob("extracted_data_phobert_*.json"))
        # Loại bỏ file TopCV
        phobert_files = [p for p in phobert_files if "topcv" not in p.name.lower()]
        if phobert_files:
            logger.info(f"Found {len(phobert_files)} PhoBERT-processed files: {[p.name for p in phobert_files]}")
            news_paths.extend([str(p) for p in phobert_files])
    
    # 3. Nếu vẫn không có TopCV, tìm file TopCV cũ
    if not topcv_path:
        # Tìm file TopCV trong extracted_data
        topcv_files = [p for p in extracted_dir.glob("*topCV*.json")]
        if topcv_files:
            # Lấy file mới nhất
            topcv_files.sort(key=lambda x: x.name, reverse=True)
            topcv_path = str(topcv_files[0])
            logger.info(f"Found TopCV data (fallback): {topcv_files[0].name}")
    
    # 4. Nếu vẫn không có news, tìm tất cả file JSON (trừ file TopCV)
    if not news_paths:
        all_json_files = [p for p in extracted_dir.glob("*.json") 
                         if "topCV" not in p.name.lower() and "topcv" not in p.name.lower()]
        if all_json_files:
            logger.info(f"Found {len(all_json_files)} JSON files: {[p.name for p in all_json_files]}")
            news_paths.extend([str(p) for p in all_json_files])
    
    return news_paths, topcv_path


def main():
    """Main function"""
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(levelname)s - %(message)s',
        handlers=[
            logging.StreamHandler()
        ]
    )

    # Define data paths: <repo_root>/pipelines/data-pipeline/extracted_data
    repo_root = Path(__file__).resolve().parents[2]
    data_dir = repo_root / "pipelines" / "data-pipeline"
    extracted_dir = data_dir / "extracted_data"

    # Find latest data files (prioritize today's files)
    # Returns tuple: (news_paths, topcv_path)
    news_paths, topcv_path = find_latest_data_files(extracted_dir)
    
    if not news_paths:
        logger.warning(f"No news data files found in {extracted_dir}")
    else:
        logger.info(f"Using {len(news_paths)} news data files for import")

    # Log TopCV data source
    if topcv_path:
        logger.info(f"Using TopCV data: {topcv_path}")
    else:
        logger.warning("No TopCV data files found")

    # Run import pipeline
    importer = RelationshipBuilder()
    stats = importer.run_import_pipeline(news_paths, topcv_path)


if __name__ == "__main__":
    main()
