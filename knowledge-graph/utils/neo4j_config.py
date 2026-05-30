"""
Neo4j Aura Configuration
"""
import os
from dotenv import load_dotenv

load_dotenv()

# Neo4j Aura Connection Details
NEO4J_URI = os.getenv('NEO4J_URI', 'neo4j+s://your-instance.databases.neo4j.io')
NEO4J_USERNAME = os.getenv('NEO4J_USERNAME', 'neo4j')
NEO4J_PASSWORD = os.getenv('NEO4J_PASSWORD', 'your-password')
NEO4J_DATABASE = os.getenv('NEO4J_DATABASE', 'neo4j')

# Batch size for imports
BATCH_SIZE = int(os.getenv('BATCH_SIZE', '100'))

# Data paths
DATA_PATH_VNEP = os.getenv(
    'DATA_PATH_VNEP',
    'src/data-pipeline/extracted_data/extracted_data_VN-EP.json'
)
DATA_PATH_DT = os.getenv(
    'DATA_PATH_DT',
    'src/data-pipeline/extracted_data/extracted_data_DT.json'
)