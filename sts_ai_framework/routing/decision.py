"""Stable, serializable results produced by route planning."""

from dataclasses import dataclass, field
from typing import Optional, Tuple


@dataclass(frozen=True)
class ScoreBreakdown:
    immediate: float = 0.0
    future: float = 0.0
    recovery: float = 0.0
    optionality: float = 0.0
    key: float = 0.0
    risk: float = 0.0
    dead_end: float = 0.0

    @property
    def total(self) -> float:
        return sum((
            self.immediate,
            self.future,
            self.recovery,
            self.optionality,
            self.key,
            self.risk,
            self.dead_end,
        ))

    def as_dict(self):
        return {
            "immediate": self.immediate,
            "future": self.future,
            "recovery": self.recovery,
            "optionality": self.optionality,
            "key": self.key,
            "risk": self.risk,
            "dead_end": self.dead_end,
        }


@dataclass(frozen=True)
class CandidateAssessment:
    choice_index: int
    x: int
    y: int
    symbol: str
    score: float
    breakdown: ScoreBreakdown
    safe: bool = True
    key_feasible: bool = True
    risk_rank: float = 0.0
    reasons: Tuple[str, ...] = field(default_factory=tuple)
    best_path: Tuple[Tuple[int, int], ...] = field(default_factory=tuple)
    reaches_rest: bool = False
    reaches_burning_elite: bool = False

    def __post_init__(self):
        if abs(self.score - self.breakdown.total) > 1e-9:
            raise ValueError("candidate score does not equal its component sum")


@dataclass(frozen=True)
class RouteDecision:
    choice_index: Optional[int]
    score: float
    mode: str
    breakdown: ScoreBreakdown = field(default_factory=ScoreBreakdown)
    candidates: Tuple[CandidateAssessment, ...] = field(default_factory=tuple)
    explanation: str = ""

    def __post_init__(self):
        if abs(self.score - self.breakdown.total) > 1e-9:
            raise ValueError("route score does not equal its component sum")

    @property
    def components(self):
        return self.breakdown.as_dict()
