import logging
from functools import lru_cache
from pathlib import Path

import yaml
from pydantic import BaseModel

logger = logging.getLogger(__name__)

CONFIG_PATH = Path(__file__).resolve().parent.parent / "config.yaml"


class FacialConfig(BaseModel):
    concerning_threshold: float = 0.40
    backend: str = "tensorflow"


class SpeechConfig(BaseModel):
    concerning_threshold: float = 0.45
    model_path: str = "./models/sensevoice-small"
    sample_rate: int = 16000
    channels: int = 1


class FusionConfig(BaseModel):
    facial_weight: float = 0.60
    speech_weight: float = 0.40
    green_threshold: float = 0.25
    red_threshold: float = 0.50


class Settings(BaseModel):
    server_port: int = 8000
    log_level: str = "INFO"
    facial: FacialConfig = FacialConfig()
    speech: SpeechConfig = SpeechConfig()
    fusion: FusionConfig = FusionConfig()


@lru_cache()
def get_settings() -> Settings:
    """Load settings from config.yaml, falling back to defaults."""
    if CONFIG_PATH.exists():
        logger.info("Loading configuration from %s", CONFIG_PATH)
        with open(CONFIG_PATH, "r") as f:
            data = yaml.safe_load(f) or {}
        return Settings(**data)
    else:
        logger.warning("config.yaml not found at %s, using defaults", CONFIG_PATH)
        return Settings()
