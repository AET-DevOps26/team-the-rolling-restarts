from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    llm_provider: str = "logos"
    llm_base_url: str = "https://logos.aet.cit.tum.de/v1"
    llm_model: str = "openai/gpt-oss-120b"
    llm_api_key: str = ""
    llm_temperature: float = 0.2
    llm_timeout_seconds: int = 60
    ollama_base_url: str = "http://ollama:11434"
    internal_api_url: str = "http://api-gateway:8080"
    log_level: str = "INFO"
    app_name: str = "GenAI Service"
    app_version: str = "0.1.0"


settings = Settings()
