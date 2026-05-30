"""
Script to create relationships between Job, Company, and Technology nodes in Neo4j
"""

from neo4j import GraphDatabase
from neo4j_config import NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE

def create_relationships():
    """Create all relationships between nodes"""

    # Connect to Neo4j
    driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))

    try:
        with driver.session(database=NEO4J_DATABASE) as session:
            print("Creating relationships...")

            # 1. Create Job → Company relationships (HIRES_FOR)
            print("1. Creating Job → Company relationships...")
            result = session.run("""
                MATCH (j:Job)
                WHERE j.company_name IS NOT NULL AND j.company_name <> ''
                MATCH (c:Company {name: j.company_name})
                MERGE (j)-[:HIRES_FOR]->(c)
                RETURN count(j) as rel_count
            """)
            record = result.single()
            rel_count = record['rel_count'] if record else 0
            print(f"   ✅ Created {rel_count} HIRES_FOR relationships")

            # 2. Create Job → Technology relationships (REQUIRES)
            print("2. Creating Job → Technology relationships...")
            result = session.run("""
                MATCH (j:Job)
                MATCH (t:Technology)
                WHERE t.name IN ['Python', 'Java', 'React', 'Node.js', 'Go', 'C#', 'SQL', 'MongoDB', 'Docker', 'Git']
                MERGE (j)-[:REQUIRES {is_mandatory: true, frequency: 1}]->(t)
                RETURN count(j) as rel_count
            """)
            record = result.single()
            rel_count = record['rel_count'] if record else 0
            print(f"   ✅ Created {rel_count} REQUIRES (Job→Technology) relationships")

            # 3. Create Job → Skill relationships (REQUIRES)
            print("3. Creating Job → Skill relationships...")
            result = session.run("""
                MATCH (j:Job)
                MATCH (s:Skill)
                WHERE s.name IN ['Python', 'Java', 'React', 'JavaScript', 'SQL', 'Git', 'Docker', 'CI/CD']
                MERGE (j)-[:REQUIRES {is_mandatory: true, frequency: 1}]->(s)
                RETURN count(j) as rel_count
            """)
            record = result.single()
            rel_count = record['rel_count'] if record else 0
            print(f"   ✅ Created {rel_count} REQUIRES (Job→Skill) relationships")

            # 4. Create Company → Technology relationships (USES)
            print("4. Creating Company → Technology relationships...")
            result = session.run("""
                MATCH (c:Company)
                MATCH (t:Technology)
                WHERE t.name IN ['Python', 'Java', 'React', 'Node.js', 'AWS', 'Azure', 'Docker']
                MERGE (c)-[:USES {frequency: 1}]->(t)
                RETURN count(c) as rel_count
            """)
            record = result.single()
            rel_count = record['rel_count'] if record else 0
            print(f"   ✅ Created {rel_count} USES (Company→Technology) relationships")

            # 5. Create Person → Company relationships (WORKS_AT)
            print("5. Creating Person → Company relationships...")
            result = session.run("""
                MATCH (p:Person)
                MATCH (c:Company)
                MERGE (p)-[:WORKS_AT]->(c)
                RETURN count(p) as rel_count
            """)
            record = result.single()
            rel_count = record['rel_count'] if record else 0
            print(f"   ✅ Created {rel_count} WORKS_AT relationships")

            # 6. Create Article → Technology relationships (MENTIONS)
            print("6. Creating Article → Technology relationships...")
            result = session.run("""
                MATCH (a:Article)
                MATCH (t:Technology)
                WHERE t.name IN ['AI', 'Machine Learning', 'GPT', 'Docker', 'Kubernetes']
                MERGE (a)-[:MENTIONS]->(t)
                RETURN count(a) as rel_count
            """)
            record = result.single()
            rel_count = record['rel_count'] if record else 0
            print(f"   ✅ Created {rel_count} MENTIONS (Article→Technology) relationships")

            # 7. Create Article → Company relationships (MENTIONS)
            print("7. Creating Article → Company relationships...")
            result = session.run("""
                MATCH (a:Article)
                MATCH (c:Company)
                MERGE (a)-[:MENTIONS]->(c)
                RETURN count(a) as rel_count
            """)
            record = result.single()
            rel_count = record['rel_count'] if record else 0
            print(f"   ✅ Created {rel_count} MENTIONS (Article→Company) relationships")

        print("\n✅ All relationships created successfully!")

    finally:
        driver.close()


def verify_relationships():
    """Verify all relationships have been created"""

    driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USERNAME, NEO4J_PASSWORD))

    try:
        with driver.session(database=NEO4J_DATABASE) as session:
            print("\n📊 Verifying relationships...\n")

            rel_types = [
                'HIRES_FOR',
                'REQUIRES',
                'USES',
                'WORKS_AT',
                'MENTIONS'
            ]

            for rel_type in rel_types:
                result = session.run(f"MATCH ()-[r:{rel_type}]->() RETURN count(r) as count")
                record = result.single()
                count = record['count'] if record else 0
                print(f"   {rel_type:15s}: {count:5d}")

            print("\n✅ Verification complete!")

    finally:
        driver.close()


if __name__ == "__main__":
    print("="*70)
    print("🚀 NEO4J RELATIONSHIPS CREATION")
    print("="*70 + "\n")

    create_relationships()
    verify_relationships()
