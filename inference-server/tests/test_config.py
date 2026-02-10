"""Tests for configuration loading (STORY-3.1/3.2)."""

from config.settings import Settings, FacialConfig, SpeechConfig, FusionConfig, get_settings


class TestConfigDefaults:
    def test_server_port_default(self):
        s = Settings()
        assert s.server_port == 8000

    def test_log_level_default(self):
        s = Settings()
        assert s.log_level == "INFO"

    def test_facial_threshold_default(self):
        s = Settings()
        assert s.facial.concerning_threshold == 0.40

    def test_speech_threshold_default(self):
        s = Settings()
        assert s.speech.concerning_threshold == 0.45

    def test_fusion_weights_default(self):
        s = Settings()
        assert s.fusion.facial_weight == 0.60
        assert s.fusion.speech_weight == 0.40

    def test_fusion_thresholds_default(self):
        s = Settings()
        assert s.fusion.green_threshold == 0.25
        assert s.fusion.red_threshold == 0.50


class TestConfigOverrides:
    def test_override_server_port(self):
        s = Settings(server_port=9000)
        assert s.server_port == 9000

    def test_override_nested_facial_config(self):
        s = Settings(facial=FacialConfig(concerning_threshold=0.55, backend="pytorch"))
        assert s.facial.concerning_threshold == 0.55
        assert s.facial.backend == "pytorch"

    def test_override_from_dict(self):
        data = {
            "server_port": 3000,
            "facial": {"concerning_threshold": 0.80},
        }
        s = Settings(**data)
        assert s.server_port == 3000
        assert s.facial.concerning_threshold == 0.80
        # Non-overridden values keep defaults
        assert s.speech.concerning_threshold == 0.45


class TestGetSettings:
    def test_get_settings_returns_settings_instance(self):
        get_settings.cache_clear()
        s = get_settings()
        assert isinstance(s, Settings)

    def test_get_settings_loads_config_yaml_values(self):
        get_settings.cache_clear()
        s = get_settings()
        assert s.server_port == 8000
        assert s.facial.concerning_threshold == 0.40
