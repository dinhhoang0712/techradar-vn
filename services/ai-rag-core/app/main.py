import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes_chat import router as chat_router
from app.api.routes_embed import router as embed_router
from app.api.routes_health import router as health_router
from app.config import get_settings
from app.db.neo4j_client import close_driver
from app.db.postgres_client import close_engine, create_tables


def _warmup_models(include_ner: bool) -> None:
    from app.core.embedder import get_embedder
    from app.core.reranker import get_reranker

    print("[Startup] Loading embedding model...")
    get_embedder()
    print("[Startup] Loading reranker model...")
    get_reranker()

    if include_ner:
        from app.core.entity_extractor import get_ner_pipeline

        print("[Startup] Loading NER model...")
        get_ner_pipeline()

    print("[Startup] All models ready.")


async def _warmup_models_background(include_ner: bool) -> None:
    try:
        await asyncio.to_thread(_warmup_models, include_ner)
    except asyncio.CancelledError:
        raise
    except Exception as e:
        print(f"[WARNING] Model warmup failed: {e}")


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    warmup_mode = (settings.model_warmup or "none").lower()
    warmup_task: asyncio.Task | None = None

    if warmup_mode == "blocking":
        await asyncio.to_thread(_warmup_models, settings.warmup_ner_model)
    elif warmup_mode == "background":
        warmup_task = asyncio.create_task(_warmup_models_background(settings.warmup_ner_model))

    # Postgres optional (RAG core vẫn chạy khi Postgres down)
    try:
        await create_tables()
    except Exception as e:
        print(f"[WARNING] Postgres không kết nối được: {e}. Chat history sẽ không được lưu.")
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

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health_router)
app.include_router(chat_router)
app.include_router(embed_router)
