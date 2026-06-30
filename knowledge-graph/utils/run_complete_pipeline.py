"""
Complete pipeline: Import data → Create relationships → Compute analytics scores
"""
import logging
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from import_multi_source import RelationshipBuilder, find_latest_data_files
from neo4j import GraphDatabase
from neo4j_config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE
from analytics import compute_and_update_trend_scores, compute_and_update_demand_scores

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


if __name__ == "__main__":
    print("\n" + "=" * 70)
    print("COMPLETE PIPELINE: Import Data + Relationships + Analytics Scores")
    print("=" * 70 + "\n")

    # Locate data files
    base_path = Path(__file__).resolve().parents[2]
    extracted_dir = base_path / "pipelines" / "data-pipeline" / "extracted_data"

    news_paths, topcv_path = find_latest_data_files(extracted_dir)

    if not news_paths:
        logger.warning("No news files found in %s", extracted_dir)
    else:
        logger.info("Found %d news data files", len(news_paths))

    if topcv_path:
        logger.info("Found TopCV data: %s", Path(topcv_path).name)
    else:
        logger.warning("No TopCV data found")

    # ---- Step 1: Import nodes + relationships ----
    importer = RelationshipBuilder()
    importer.run_import_pipeline(news_paths, topcv_path)

    # ---- Step 2: Compute & persist analytics scores ----
    print("\n" + "=" * 70)
    print("STEP 2: Computing Analytics Scores")
    print("=" * 70 + "\n")

    driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))
    try:
        trend_scores  = compute_and_update_trend_scores(driver, NEO4J_DATABASE)
        demand_scores = compute_and_update_demand_scores(driver, NEO4J_DATABASE)

        print(f"\n  trend_score  updated: {len(trend_scores)} technologies")
        print(f"  demand_score updated: "
              f"{len(demand_scores['technologies'])} technologies, "
              f"{len(demand_scores['skills'])} skills")
    finally:
        driver.close()

    # ---- Step 3: Final statistics ----
    print("\n" + "=" * 70)
    print("FINAL DATABASE STATISTICS")
    print("=" * 70 + "\n")

    driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))
    try:
        with driver.session(database=NEO4J_DATABASE) as session:
            node_types = ["Article", "Technology", "Company", "Skill", "Person", "Job"]
            for node_type in node_types:
                result = session.run(f"MATCH (n:{node_type}) RETURN count(n) AS count")
                record = result.single()
                print(f"   {node_type:15s}: {record['count'] if record else 0:5d}")

            result = session.run("MATCH ()-[r]->() RETURN count(r) AS count")
            record = result.single()
            print(f"   {'Relationships':15s}: {record['count'] if record else 0:5d}")
    finally:
        driver.close()

    print("\n" + "=" * 70)
    print("COMPLETE PIPELINE FINISHED!")
    print("=" * 70 + "\n")
