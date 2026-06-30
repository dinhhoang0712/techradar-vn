"""
Technology taxonomy for the TechRadar knowledge graph.

Two-level hierarchy: category → subcategory → [keywords].
Keywords are lowercase and matched case-insensitively against tech names.

Rules for adding entries:
  - Put a tech in the MOST SPECIFIC category for Vietnamese job market context.
  - If a tech fits multiple subcategories, use the primary usage pattern
    (e.g. Kubernetes → DevOps/Orchestration, not Cloud/Cloud Native).
  - Keywords should be the canonical form (already resolved through
    entity_resolution/aliases.json) plus any remaining common variants.
"""

TAXONOMY: dict[str, dict[str, list[str]]] = {
    # ---- AI / Machine Learning ----------------------------------------
    "AI/ML": {
        "Machine Learning": [
            "machine learning", "scikit-learn", "xgboost", "lightgbm",
            "catboost", "random forest", "gradient boosting",
        ],
        "Deep Learning": [
            "deep learning", "tensorflow", "pytorch", "keras", "jax",
            "mxnet", "paddle",
        ],
        "NLP": [
            "nlp", "natural language processing", "bert", "roberta", "electra",
            "phobert", "word2vec", "fasttext", "spacy", "nltk", "transformers",
        ],
        "Generative AI": [
            "generative ai", "llm", "large language model", "chatgpt", "gpt",
            "claude", "gemini", "llama", "mistral", "rag",
            "retrieval augmented generation", "stable diffusion", "midjourney",
            "langchain", "llamaindex", "openai",
        ],
        "Computer Vision": [
            "computer vision", "opencv", "yolo", "detectron", "resnet",
            "efficientnet", "image recognition", "object detection",
        ],
        "MLOps": [
            "mlops", "mlflow", "kubeflow", "feast", "seldon", "bentoml",
            "weights and biases", "neptune",
        ],
    },

    # ---- Cloud ----------------------------------------------------------
    "Cloud": {
        "AWS": [
            "aws", "amazon web services", "ec2", "s3", "lambda", "sagemaker",
            "eks", "rds", "dynamodb", "cloudformation", "cloudwatch", "route53",
            "api gateway", "amazon s3", "amazon ec2", "aws lambda",
        ],
        "GCP": [
            "google cloud platform", "gcp", "bigquery", "gke", "vertex ai",
            "cloud run", "cloud functions", "google cloud",
        ],
        "Azure": [
            "microsoft azure", "azure", "aks", "cosmos db", "azure devops",
            "azure functions", "azure sql",
        ],
        "Cloud Native": [
            "serverless", "service mesh", "istio", "envoy", "consul",
            "cloud foundry",
        ],
    },

    # ---- Frontend -------------------------------------------------------
    "Frontend": {
        "Framework": [
            "react", "vue", "angular", "svelte", "next.js", "nuxt.js",
            "gatsby", "remix", "solid",
        ],
        "Language": [
            "javascript", "typescript", "html", "css", "webassembly", "wasm",
        ],
        "Styling": [
            "tailwind css", "sass", "scss", "bootstrap", "material ui",
            "ant design", "chakra ui", "styled-components",
        ],
        "Build Tools": [
            "webpack", "vite", "rollup", "babel", "esbuild",
            "eslint", "prettier",
        ],
    },

    # ---- Backend --------------------------------------------------------
    "Backend": {
        "Python": [
            "python", "django", "fastapi", "flask", "sqlalchemy", "celery",
            "pydantic", "aiohttp",
        ],
        "Java/JVM": [
            "java", "spring boot", "spring", "kotlin", "scala", "quarkus",
            "micronaut", "vert.x", "hibernate",
        ],
        "Go": ["go", "golang", "gin", "fiber", "echo", "gorm"],
        "Node.js": ["node.js", "express.js", "nestjs", "fastify", "koa"],
        "PHP": ["php", "laravel", "symfony", "codeigniter"],
        "Rust": ["rust", "actix", "axum", "tokio"],
        ".NET": [".net", "c#", "asp.net", "blazor", "entity framework"],
        "Ruby": ["ruby", "rails", "ruby on rails", "sinatra"],
    },

    # ---- Mobile ---------------------------------------------------------
    "Mobile": {
        "Cross-platform": [
            "react native", "flutter", "xamarin", "ionic", "capacitor",
        ],
        "iOS": ["ios", "swift", "objective-c", "xcode", "swiftui"],
        "Android": ["android", "android sdk", "jetpack compose"],
    },

    # ---- Database -------------------------------------------------------
    "Database": {
        "Relational": [
            "postgresql", "mysql", "sqlite", "mariadb", "oracle", "db2",
        ],
        "NoSQL": [
            "mongodb", "cassandra", "couchdb", "firestore", "hbase",
        ],
        "Cache": ["redis", "memcached", "hazelcast"],
        "Search": ["elasticsearch", "opensearch", "solr", "typesense"],
        "Vector DB": [
            "qdrant", "pinecone", "weaviate", "milvus", "chroma",
            "pgvector",
        ],
        "Graph DB": ["neo4j", "arangodb", "amazon neptune", "tigergraph"],
        "Time Series": [
            "influxdb", "timescaledb", "prometheus", "victoriametrics",
        ],
    },

    # ---- DevOps ---------------------------------------------------------
    "DevOps": {
        "CI/CD": [
            "ci/cd", "jenkins", "github actions", "gitlab ci", "circle ci",
            "travis ci", "argocd", "tekton", "drone",
        ],
        "Container": ["docker", "containerd", "podman", "buildah"],
        "Orchestration": [
            "kubernetes", "docker swarm", "nomad", "openshift",
        ],
        "Infrastructure": [
            "terraform", "ansible", "pulumi", "chef", "puppet", "crossplane",
        ],
        "Monitoring": [
            "grafana", "datadog", "new relic", "elastic stack",
            "elk", "loki", "jaeger", "opentelemetry",
        ],
    },

    # ---- Data Engineering -----------------------------------------------
    "Data Engineering": {
        "Processing": [
            "apache spark", "flink", "apache beam", "dask", "ray",
        ],
        "Streaming": [
            "apache kafka", "kafka", "rabbitmq", "pulsar", "nats", "activemq",
        ],
        "Orchestration": [
            "apache airflow", "airflow", "prefect", "dagster", "luigi", "mage",
        ],
        "Data Warehouse": [
            "snowflake", "bigquery", "redshift", "clickhouse", "dbt",
            "apache hive", "trino", "presto",
        ],
        "ETL": ["etl", "fivetran", "airbyte", "stitch", "talend"],
    },

    # ---- Security -------------------------------------------------------
    "Security": {
        "Auth": [
            "oauth", "jwt", "openid connect", "keycloak", "auth0",
            "okta", "saml",
        ],
        "Network": [
            "vpn", "firewall", "ssl", "tls", "nginx", "waf",
            "cloudflare", "f5",
        ],
        "AppSec": [
            "owasp", "penetration testing", "sonarqube", "snyk",
            "checkmarx", "veracode",
        ],
    },

    # ---- Blockchain -----------------------------------------------------
    "Blockchain": {
        "Platform": [
            "ethereum", "bitcoin", "solana", "polygon", "hyperledger",
            "binance smart chain", "avalanche",
        ],
        "Development": ["solidity", "web3", "truffle", "hardhat", "foundry"],
    },
}

# Fallback for unrecognized techs
DEFAULT_CATEGORY = "Other"
DEFAULT_SUBCATEGORY = "General"
