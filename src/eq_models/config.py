"""Configuration loader — reads config.yaml once at module import time."""

from pathlib import Path

import yaml

_CONFIG_PATH = Path(__file__).resolve().parents[2] / "config.yaml"


def _load_config() -> dict:
    with open(_CONFIG_PATH, "r") as f:
        return yaml.safe_load(f)


config: dict = _load_config()
