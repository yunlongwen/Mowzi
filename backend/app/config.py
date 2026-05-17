from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str = "sqlite:///./mowzi.db"
    llm_api_url: str = ""
    llm_api_key: str = ""
    llm_model: str = "gpt-4o-mini"
    xfyun_app_id: str = ""
    xfyun_api_key: str = ""
    xfyun_api_secret: str = ""
    max_audio_duration_sec: int = 60
    min_audio_duration_sec: float = 0.5
    silence_detection_sec: float = 3.0
    max_llm_tokens: int = 150
    context_window_tokens: int = 8000

    class Config:
        env_file = ".env"


settings = Settings()