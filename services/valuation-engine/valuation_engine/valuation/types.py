"""Transparent metric container: value + formula + inputs + SEC sources."""
from __future__ import annotations

from dataclasses import dataclass, field

from ..normalize.statements import SourcedValue


@dataclass
class Metric:
    key: str
    label: str
    value: float | None
    unit: str            # "USD" | "USD/share" | "%" | "x" | "ratio" | "shares"
    formula: str
    inputs: dict[str, float | None] = field(default_factory=dict)
    sources: list[SourcedValue] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "key": self.key,
            "label": self.label,
            "value": self.value,
            "unit": self.unit,
            "formula": self.formula,
            "inputs": self.inputs,
            "sources": [s.to_dict() for s in self.sources],
            "notes": self.notes,
        }


@dataclass
class Assumptions:
    """Market inputs and model knobs. All percentages are in percent (5.0 == 5%)."""
    aaa_yield_pct: float
    treasury_10y_pct: float
    hurdle_rate_pct: float
    equity_risk_premium_pct: float
    beta: float
    terminal_growth_pct: float
    exit_multiple: float
    tax_rate_pct: float
    projection_years: int = 5
    max_growth_pct: float = 15.0
    mos_bands_pct: tuple[float, ...] = (25.0, 50.0)
    rate_source: str = "defaults"

    def to_dict(self) -> dict:
        return {
            "aaa_yield_pct": self.aaa_yield_pct,
            "treasury_10y_pct": self.treasury_10y_pct,
            "hurdle_rate_pct": self.hurdle_rate_pct,
            "equity_risk_premium_pct": self.equity_risk_premium_pct,
            "beta": self.beta,
            "terminal_growth_pct": self.terminal_growth_pct,
            "exit_multiple": self.exit_multiple,
            "tax_rate_pct": self.tax_rate_pct,
            "projection_years": self.projection_years,
            "max_growth_pct": self.max_growth_pct,
            "mos_bands_pct": list(self.mos_bands_pct),
            "rate_source": self.rate_source,
        }
