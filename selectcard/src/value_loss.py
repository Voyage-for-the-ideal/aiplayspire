"""Format-12 objectives; raw outcomes supervise only the observed stopping bin."""
import torch
from torch.nn import functional as F
try:
    from .config import Config
    from .dataset import GlobalFeatureEncoder
    from .multiscale import ValueOutputs, distribution
except ImportError:
    from config import Config
    from dataset import GlobalFeatureEncoder
    from multiscale import ValueOutputs, distribution


def band_tensor(globals_):
    levels = torch.round(globals_[:, GlobalFeatureEncoder.feature_names.index("ascension")] * 20).long()
    bands = torch.zeros_like(levels)
    for lower, upper, band in ((1,5,1),(6,10,2),(11,15,3),(16,19,4),(20,20,5)):
        bands[(levels >= lower) & (levels <= upper)] = band
    return bands


def value_loss_components(outputs, targets, masks, floors, global_features,
                          band_weights, baseline_rates, *, terminal_floors=None,
                          key_logits=None, key_labels=None, key_masks=None, loss_mode=None):
    if not isinstance(outputs, ValueOutputs):
        outputs = ValueOutputs.from_flat(outputs)
    if terminal_floors is None:
        raise ValueError("Raw terminal floors are required for stopping-position supervision")
    mode = loss_mode or Config.VALUE_LOSS_MODE
    if mode not in {"macro_relative_nll", "multiscale", "micro_nll"}:
        raise ValueError("Unsupported value loss mode")
    dist = distribution(outputs, floors, Config.RESOLUTION_MODE)
    w = band_weights[band_tensor(global_features)]
    hm = masks[:, :-1] * dist["active"] * w[:, None]
    elements = F.binary_cross_entropy_with_logits(outputs.hazards, targets[:, :-1], reduction="none")
    numerators, denominators = (hm * elements).sum(0), hm.sum(0)
    supervised = denominators > 0
    rates = torch.as_tensor(baseline_rates, dtype=elements.dtype, device=elements.device)
    if rates.shape != (24,) or not torch.isfinite(rates).all() or ((rates < 0) | (rates > 1)).any():
        raise ValueError("baseline rates must define 23 hazards and Heart probabilities")
    p = rates.clamp(1e-7, 1 - 1e-7)
    entropy = (-(p * p.log() + (1-p) * (1-p).log())).clamp_min(Config.BASELINE_ENTROPY_FLOOR)
    zero = outputs.sum() * 0
    per_head = numerators / denominators.clamp_min(1e-12)
    macro = per_head[supervised].mean() if supervised.any() else zero
    relative = (per_head[supervised] / entropy[:-1][supervised]).mean() if supervised.any() else zero
    micro = numerators.sum() / denominators.sum().clamp_min(1e-12)
    near_mask = hm * dist["near"]
    far_mask = hm * ~dist["near"]
    near = (elements * near_mask).sum() / near_mask.sum().clamp_min(1e-12)
    far = (elements * far_mask).sum() / far_mask.sum().clamp_min(1e-12)
    log_p = dist["log_heart"]
    # Compute log(1-exp(log_p)) stably, including very confident Heart outputs.
    safe_log_p = log_p.clamp(max=-torch.finfo(log_p.dtype).eps)
    log_not_p = torch.where(safe_log_p < -0.6931471805599453,
                           torch.log1p(-torch.exp(safe_log_p)), torch.log(-torch.expm1(safe_log_p)))
    heart_elements = torch.where(targets[:, -1].bool(), -log_p, -log_not_p)
    heart_mask = masks[:, -1] * w
    heart_nll = (heart_elements * heart_mask).sum() / heart_mask.sum().clamp_min(1e-12)
    heart_relative = heart_nll / entropy[-1]
    width = (dist["ends"][None] - 1 - dist["starts"]).clamp_min(0)
    mean_target = (terminal_floors[:, None] - dist["starts"]) / width.clamp_min(1)
    stopped = hm * targets[:, :-1] * masks[:, -1, None] * (width > 0)
    mean_loss = ((torch.sigmoid(outputs.means) - mean_target).square() * stopped).sum() / stopped.sum().clamp_min(1e-12)
    key_loss = zero
    if key_logits is not None:
        if key_labels is None or key_masks is None:
            raise ValueError("key labels and masks are required")
        key_elements = F.binary_cross_entropy_with_logits(key_logits, key_labels, reduction="none")
        key_loss = (key_elements * key_masks).sum() / key_masks.sum().clamp_min(1)
    if mode == "macro_relative_nll":
        value = (relative + Config.HEART_HEAD_LOSS_WEIGHT * heart_relative) / (1 + Config.HEART_HEAD_LOSS_WEIGHT)
    elif mode == "micro_nll":
        value = micro + Config.HEART_HEAD_LOSS_WEIGHT * heart_nll
    else:
        value = near + Config.FAR_LOSS_WEIGHT * far + Config.HEART_HEAD_LOSS_WEIGHT * heart_relative
    total = value + Config.MEAN_LOSS_WEIGHT * mean_loss + Config.KEY_HEAD_LOSS_WEIGHT * key_loss
    return dict(total=total, value=value, hazard_micro_nll=micro, hazard_macro_nll=macro,
                hazard_relative_nll=relative, heart_joint_nll=heart_nll,
                heart_relative_nll=heart_relative, key_bce=key_loss, near_nll=near, far_nll=far,
                mean_loss=mean_loss, near_coverage=(hm.bool() & dist["near"]).sum(),
                far_coverage=(hm.bool() & ~dist["near"]).sum(), mean_coverage=stopped.bool().sum(),
                heart_coverage=heart_mask.bool().sum(),
                legacy_value=micro + Config.HEART_HEAD_LOSS_WEIGHT * heart_nll,
                relative_value=(relative + Config.HEART_HEAD_LOSS_WEIGHT * heart_relative) / (1+Config.HEART_HEAD_LOSS_WEIGHT))
