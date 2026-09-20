from __future__ import annotations

from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    sec_user_agent: str = "ValueLens Dev dev@example.com"
    fred_api_key: str | None = None
    cache_db: Path = Path(".cache/edgar.sqlite")
    cache_ttl_seconds: int = 60 * 60 * 24  # companyfacts changes at most daily
    sec_max_requests_per_second: float = 8.0  # SEC limit is 10/s; stay under it

    # Per-client fairness: requests per minute per X-SEC-User-Agent (0 disables).
    client_rate_limit_per_minute: int = 60
    require_identity: bool = False  # True in hosted deployments: reject requests without a valid identity

    # Optional licensed price provider (https://polygon.io). Used first when set.
    polygon_api_key: str | None = None

    # Default market inputs, used when live providers are unavailable.
    default_aaa_yield_pct: float = 5.0
    default_treasury_10y_pct: float = 4.2
    default_hurdle_rate_pct: float = 10.0
    default_equity_risk_premium_pct: float = 5.0
    default_beta: float = 1.0
    default_terminal_growth_pct: float = 2.5
    default_exit_multiple: float = 15.0
    default_tax_rate_pct: float = 21.0


settings = Settings()
