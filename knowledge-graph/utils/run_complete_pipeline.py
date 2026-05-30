"""
Complete pipeline: Import data and create relationships
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from import_multi_source import RelationshipBuilder, find_latest_data_files
from neo4j import GraphDatabase
from neo4j_config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE

if __name__ == "__main__":
    print("\n" + "="*70)
    print("🚀 COMPLETE PIPELINE: Import Data + Create Relationships")
    print("="*70 + "\n")

    # Define data paths
    base_path = Path(__file__).parent.parent.parent.parent
    data_dir = base_path / "src" / "data-pipeline"
    extracted_dir = data_dir / "extracted_data"
    
    # Use the new function to find latest data files (prioritizes today's files)
    # Returns tuple: (news_paths, topcv_path)
    news_paths, topcv_path = find_latest_data_files(extracted_dir)
    
    if not news_paths:
        print(f"⚠️ Warning: No news files found in {extracted_dir}")
    else:
        print(f"📂 Found {len(news_paths)} news data files")
    
    if topcv_path:
        print(f"📂 Found TopCV data: {Path(topcv_path).name}")
    else:
        print(f"⚠️ Warning: No TopCV data found")

    # Run import pipeline
    importer = RelationshipBuilder()
    stats = importer.run_import_pipeline(news_paths, topcv_path)

    # Note: Relationships are now created within run_import_pipeline() using batch processing
    # This avoids N+1 query problem and cross-product issues
    print("\n" + "="*70)
    print("🚀 Relationships created during import pipeline (batch processing)")
    print("="*70 + "\n")

    # Print final statistics
    print("\n" + "="*70)
    print("📊 FINAL DATABASE STATISTICS")
    print("="*70 + "\n")

    driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))

    try:
        with driver.session(database=NEO4J_DATABASE) as session:
            stats = {}

            node_types = ['Article', 'Technology', 'Company', 'Skill', 'Person', 'Job']
            for node_type in node_types:
                result = session.run(f"MATCH (n:{node_type}) RETURN count(n) as count")
                record = result.single()
                stats[node_type] = record['count'] if record else 0

            result = session.run("MATCH ()-[r]->() RETURN count(r) as count")
            record = result.single()
            stats['Relationships'] = record['count'] if record else 0

            for node_type, count in stats.items():
                label = node_type if node_type == 'Relationships' else node_type
                print(f"   {label:15s}: {count:5d}")

    finally:
        driver.close()

    print("\n" + "="*70)
    print("✅ COMPLETE PIPELINE FINISHED!")
    print("="*70 + "\n")
