import logging

from fastapi import FastAPI

from config.settings import get_settings
from models.schemas import HealthResponse
from routes.analyze import router as analyze_router

# Load settings
settings = get_settings()

# Configure logging
logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="EQ Meeting Coach — Inference Server",
    description="Receives image frames and audio clips, analyzes emotions, and returns a color verdict.",
    version="0.1.0",
)

# Include routes
app.include_router(analyze_router)

# Track whether models are loaded (will be set to True once EPIC-4 models initialize)
models_loaded = False


@app.on_event("startup")
async def startup() -> None:
    global models_loaded
    logger.info("Starting EQ Meeting Coach Inference Server on port %s", settings.server_port)
    logger.info("Configuration: %s", settings.model_dump())
    # With stubs, models are immediately "ready"
    models_loaded = True
    logger.info("Server ready — models loaded (stubs active)")


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    """Health check endpoint."""
    return HealthResponse(status="ok", models_loaded=models_loaded)
