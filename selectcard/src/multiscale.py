"""Differentiable format-12 distribution, shared by training and inference."""

from typing import NamedTuple
import torch
from torch.nn import functional as F

try:
    from .data_contract import (HAZARD_OUTPUT_DIM, MULTISCALE_HAZARD_DIM,
                                STAGE_ENDPOINTS, VALUE_OUTPUT_DIM)
except ImportError:
    from data_contract import (HAZARD_OUTPUT_DIM, MULTISCALE_HAZARD_DIM,
                               STAGE_ENDPOINTS, VALUE_OUTPUT_DIM)


class ValueOutputs(NamedTuple):
    hazards: torch.Tensor
    means: torch.Tensor
    heart: torch.Tensor

    @classmethod
    def from_flat(cls, values):
        if values.ndim != 2 or values.shape[1] != VALUE_OUTPUT_DIM:
            raise ValueError(f"Expected {VALUE_OUTPUT_DIM} format-12 outputs")
        n = MULTISCALE_HAZARD_DIM
        return cls(values[:, :n], values[:, n:2*n], values[:, -1])

    @property
    def shape(self):
        return torch.Size((len(self.heart), VALUE_OUTPUT_DIM))

    @property
    def fine_logits(self):
        return self.hazards[:, :HAZARD_OUTPUT_DIM]

    @property
    def coarse_logits(self):
        return self.hazards[:, HAZARD_OUTPUT_DIM:]

    def sum(self):
        return self.hazards.sum() + self.means.sum() + self.heart.sum()

    @property
    def flat(self):
        return torch.cat((self.hazards, self.means, self.heart[:, None]), 1)


def distribution(outputs, floors, resolution="multiscale"):
    """Compose active hazards in endpoint order, with constrained stopping means."""
    try:
        from .data_contract import HAZARD_ENDPOINTS, COARSE_ENDPOINTS
    except ImportError:
        from data_contract import HAZARD_ENDPOINTS, COARSE_ENDPOINTS
    if not isinstance(outputs, ValueOutputs):
        outputs = ValueOutputs.from_flat(outputs)
    if resolution not in {"multiscale", "full_fine"}:
        raise ValueError("Invalid resolution")
    if not torch.isfinite(floors).all() or ((floors < 0) | (floors > 57)).any():
        raise ValueError("floor must be finite and between 0 and 57")
    device, dtype = outputs.hazards.device, outputs.hazards.dtype
    floors = floors.to(device=device, dtype=dtype)
    ends = torch.tensor(HAZARD_ENDPOINTS + COARSE_ENDPOINTS, device=device, dtype=dtype)
    stages = torch.bucketize(floors.contiguous(), floors.new_tensor([17, 34, 51]), right=True)
    head_stages = torch.bucketize(ends - 1, floors.new_tensor([17, 34, 51]), right=True)
    fine = torch.arange(len(ends), device=device) < HAZARD_OUTPUT_DIM
    if resolution == "full_fine":
        active = (ends[None] > floors[:, None]) & fine[None]
    else:
        active = (ends[None] > floors[:, None]) & torch.where(
            fine[None], head_stages[None] == stages[:, None], head_stages[None] > stages[:, None])
    order = torch.argsort(ends, stable=True)
    inverse = torch.argsort(order)
    enabled = active[:, order]
    ordered_ends = ends[order][None].expand(len(floors), -1)
    ordered_hazards = outputs.hazards[:, order]
    factors = torch.where(enabled, F.logsigmoid(-ordered_hazards), torch.zeros_like(ordered_hazards))
    cumulative = torch.cumsum(factors, dim=1)
    before = torch.cat((torch.zeros_like(floors[:, None]), cumulative[:, :-1]), dim=1)
    previous_ends = torch.cummax(torch.where(enabled, ordered_ends, floors[:, None]), dim=1).values
    ordered_starts = torch.cat((floors[:, None], previous_ends[:, :-1]), dim=1)
    widths = (ordered_ends - 1 - ordered_starts).clamp_min(0)
    ordered_means = ordered_starts + widths * torch.sigmoid(outputs.means[:, order])
    masses = torch.where(enabled, torch.exp(before) * torch.sigmoid(ordered_hazards),
                         torch.zeros_like(ordered_hazards))
    expected = (masses * ordered_means).sum(1)
    log_survival = cumulative[:, -1]
    means = torch.where(enabled, ordered_means, torch.zeros_like(ordered_means))[:, inverse]
    starts, stop_mass = ordered_starts[:, inverse], masses[:, inverse]
    survivals = torch.exp(cumulative)[:, inverse]
    final_survival = torch.exp(log_survival)
    expected = expected + 57 * final_survival
    log_heart = log_survival + F.logsigmoid(outputs.heart)
    milestones = []
    for endpoint in STAGE_ENDPOINTS:
        include = active & (ends[None] <= endpoint)
        log_p = torch.where(include, F.logsigmoid(-outputs.hazards),
                            torch.zeros_like(outputs.hazards)).sum(1)
        milestones.append(torch.exp(log_p))
    return dict(active=active, near=active & (head_stages[None] == stages[:, None]),
                starts=starts, ends=ends, means=means, stop_mass=stop_mass,
                survival=survivals, final_survival=final_survival,
                log_heart=log_heart, heart_probability=torch.exp(log_heart),
                expected_floor=expected, milestones=torch.stack(milestones, 1),
                order=order)
