import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Response
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

from app.api.routes_chat import router as chat_router
from app.api.routes_embed import router as embed_router
from app.api.routes_health import router as health_router
from app.api.routes_internal import router as internal_router
from app.api.routes_agent import router as agent_router
from app.api.routes_career import router as career_router
from app.api.routes_forecast import router as forecast_router
from app.api.routes_recommend import router as recommend_router
from app.api.routes_report import router as report_router
from app.api.routes_summarize import router as summarize_router
from app.config import get_settings
from app.db.neo4j_client import close_driver
from app.db.postgres_client import close_engine
from app.observability import RequestContextMiddleware, configure_logging

# Configure JSON logging before anything else emits logs.
configure_logging()
logger = logging.getLogger("ai-rag-core")


def _warmup_models(include_ner: bool) -> None:
    from app.core.embedder import get_embedder
    from app.core.reranker import get_reranker

    logger.info("Loading embedding model...")
    get_embedder()
    logger.info("Loading reranker model...")
    get_reranker()

    if include_ner:
        from app.core.entity_extractor import get_ner_pipeline

        logger.info("Loading NER model...")
        get_ner_pipeline()

    logger.info("All models ready.")


async def _warmup_models_background(include_ner: bool) -> None:
    try:
        await asyncio.to_thread(_warmup_models, include_ner)
    except asyncio.CancelledError:
        raise
    except Exception as e:
        logger.warning("Model warmup failed: %s", e)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    warmup_mode = (settings.model_warmup or "none").lower()
    warmup_task: asyncio.Task | None = None

    if warmup_mode == "blocking":
        await asyncio.to_thread(_warmup_models, settings.warmup_ner_model)
    elif warmup_mode == "background":
        warmup_task = asyncio.create_task(_warmup_models_background(settings.warmup_ner_model))

    # Schema Postgres do Flyway của backend (apps/backend) sở hữu DUY NHẤT.
    # ai-rag-core KHÔNG tự tạo bảng để tránh schema drift (model SQLAlchemy ở đây
    # là mirror để đọc user_profile + ghi chat_message, không phải nguồn sự thật).
    # Xem apps/backend/src/main/resources/db/README.md.
    yield
    # Shutdown
    if warmup_task and not warmup_task.done():
        warmup_task.cancel()
    await close_driver()
    await close_engine()


app = FastAPI(
    title="TechPulse RAG Service",
    description="RAG pipeline phân tích xu hướng công nghệ IT Việt Nam",
    version="1.0.0",
    lifespan=lifespan,
)

# Trace-id binding + access logging. Added before CORS so it is the OUTERMOST middleware
# (Starlette runs the last-added middleware first), giving every request a trace id.
app.add_middleware(RequestContextMiddleware)

_cors_origins = [o.strip() for o in get_settings().cors_origins.split(",") if o.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_cors_origins or ["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health_router)
app.include_router(chat_router)
app.include_router(embed_router)
app.include_router(internal_router)
app.include_router(recommend_router)
app.include_router(forecast_router)
app.include_router(career_router)
app.include_router(summarize_router)
app.include_router(report_router)
app.include_router(agent_router)


@app.get("/metrics", include_in_schema=False)
async def metrics():
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)
