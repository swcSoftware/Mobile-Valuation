"""
Fetch and lightly structure the XBRL "companyfacts" bundle for a company.

Endpoint: https://data.sec.gov/api/xbrl/companyfacts/CIK##########.json
Shape:    facts -> taxonomy ("us-gaap" | "dei") -> tag -> units -> unit -> [fact, ...]
Each fact: {end, val, accn, fy, fp, form, filed, frame?, start?}
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from .client import EdgarClient
from .tickers import CompanyRef

COMPANYFACTS_URL = "https://data.sec.gov/api/xbrl/companyfacts/CIK{cik}.json"


@dataclass(frozen=True)
class Fact:
    taxonomy: str
    tag: str
    unit: str
    value: float
    end: date
    start: date | None
    form: str
    fp: str | None
    fy: int | None
    accession: str
    filed: date
    frame: str | None

    @property
    def duration_days(self) -> int | None:
        if self.start is None:
            return None
        return (self.end - self.start).days

    @property
    def is_instant(self) -> bool:
        return self.start is None


@dataclass
class CompanyFacts:
    ref: CompanyRef
    entity_name: str
    facts: dict[str, list[Fact]]  # key: "taxonomy:tag"

    def get(self, taxonomy: str, tag: str) -> list[Fact]:
        return self.facts.get(f"{taxonomy}:{tag}", [])


def _parse_date(s: str | None) -> date | None:
    return date.fromisoformat(s) if s else None


async def fetch_companyfacts(client: EdgarClient, ref: CompanyRef) -> CompanyFacts:
    raw = await client.get_json(COMPANYFACTS_URL.format(cik=ref.cik_padded))
    return parse_companyfacts(raw, ref)


def parse_companyfacts(raw: dict, ref: CompanyRef) -> CompanyFacts:
    """Pure function so tests can feed recorded fixtures without the network."""
    facts: dict[str, list[Fact]] = {}
    for taxonomy, tags in raw.get("facts", {}).items():
        for tag, body in tags.items():
            key = f"{taxonomy}:{tag}"
            bucket: list[Fact] = []
            for unit, rows in body.get("units", {}).items():
                for row in rows:
                    try:
                        bucket.append(
                            Fact(
                                taxonomy=taxonomy,
                                tag=tag,
                                unit=unit,
                                value=float(row["val"]),
                                end=date.fromisoformat(row["end"]),
                                start=_parse_date(row.get("start")),
                                form=row.get("form", ""),
                                fp=row.get("fp"),
                                fy=row.get("fy"),
                                accession=row.get("accn", ""),
                                filed=date.fromisoformat(row["filed"]),
                                frame=row.get("frame"),
                            )
                        )
                    except (KeyError, ValueError, TypeError):
                        continue
            if bucket:
                facts[key] = bucket
    return CompanyFacts(ref=ref, entity_name=raw.get("entityName", ref.name), facts=facts)
