"""Resolution-independent validation and run-paired format-12 exports."""
import time
import numpy as np
import torch

try:
    from .config import Config
    from .data_contract import HAZARD_ENDPOINTS, COARSE_ENDPOINTS, COMMON_TARGET_NAMES, COMMON_ENTROPY_FLOOR
    from .dataset import GlobalFeatureEncoder
    from .multiscale import distribution
except ImportError:
    from config import Config
    from data_contract import HAZARD_ENDPOINTS, COARSE_ENDPOINTS, COMMON_TARGET_NAMES, COMMON_ENTROPY_FLOOR
    from dataset import GlobalFeatureEncoder
    from multiscale import distribution


def common_entropy(rates):
    p = np.clip(np.asarray(rates, dtype=float), 1e-7, 1-1e-7)
    return np.maximum(-(p*np.log(p)+(1-p)*np.log1p(-p)), COMMON_ENTROPY_FLOOR)


def terminal_floor_mae(predictions, terminals):
    # Cast before subtraction: float32 rounding can turn an exact MAE tie into
    # a regression against the float64 contextual baseline on another platform.
    predicted = np.asarray(predictions, dtype=np.float64)
    observed = np.asarray(terminals, dtype=np.float64)
    return float(np.abs(predicted - observed).mean()) if predicted.size else float("nan")


def nll_metrics(probabilities, targets, masks, baseline_rates, sample_weights=None):
    p = np.clip(np.asarray(probabilities, dtype=float), 1e-7, 1-1e-7)
    y, m = np.asarray(targets, dtype=float), np.asarray(masks, dtype=bool)
    w = np.ones(len(p)) if sample_weights is None else np.asarray(sample_weights, dtype=float)
    if w.shape != (len(p),) or not np.isfinite(w).all() or (w <= 0).any():
        raise ValueError("sample weights must be finite and positive")
    if p.shape != y.shape or m.shape != y.shape:
        raise ValueError("Probability, label and mask shapes disagree")
    rates = np.clip(np.asarray(baseline_rates, dtype=float), 1e-7, 1-1e-7)
    if rates.shape != (p.shape[1],):
        raise ValueError("Prior width disagrees with evaluation targets")
    entropy = common_entropy(rates)
    elements = -(y*np.log(p)+(1-y)*np.log1p(-p))
    den = (m*w[:, None]).sum(0)
    num = (elements*m*w[:, None]).sum(0)
    losses = np.divide(num, den, out=np.full_like(den, np.nan), where=den > 0)
    relative = losses / entropy
    macro = float(np.nanmean(relative[:-1])) if (den[:-1] > 0).any() else float("nan")
    heart = float(relative[-1])
    # Fixed benchmark weight: independent of every candidate's training objective.
    score = (macro + heart)/2 if np.isfinite(macro) else heart
    return dict(relative_value_nll=score, common_nll=score, hazard_relative_nll=macro,
                heart_relative_nll=heart, heart_joint_nll=float(losses[-1]),
                hazard_nll_by_target=losses[:-1].tolist(),
                hazard_relative_nll_by_target=relative[:-1].tolist(),
                hazard_macro_nll=float(np.nanmean(losses[:-1])) if (den[:-1] > 0).any() else float("nan"),
                hazard_micro_nll=float(num[:-1].sum()/den[:-1].sum()) if den[:-1].sum() else float("nan"),
                target_coverage=den.tolist())


def evaluate(model, loader, device, progress=None, name="Validation", baseline_rates=None,
             baseline_statistics=None, band_weights=None, use_map=False, recorder=None,
             egg_token_ids=()):
    try:
        from .train import _split_batch, _classification_metrics, ascension_band_tensor
        from .evaluation_v4 import mechanism_counts
        from .experiments import selected_run
    except ImportError:
        from train import _split_batch, _classification_metrics, ascension_band_tensor
        from evaluation_v4 import mechanism_counts
        from experiments import selected_run
    resolution = model.model_config["resolution_mode"]
    model.eval()
    saved = {k: [] for k in ("p", "y", "m", "floor", "terminal", "known", "bands", "asc", "character",
                             "prediction", "hp", "hy", "hm", "near", "keys", "kl", "km")}
    started, last_log = time.monotonic(), time.monotonic()
    with torch.no_grad():
        for batch_index, batch in enumerate(loader, 1):
            base, context, keys = _split_batch(batch, use_map)
            seq, upgrades, counts, globals_, characters, bosses, floors, targets, masks, terminal = (
                t.to(device) for t in base)
            context = tuple(t.to(device) for t in context) if context is not None else None
            kwargs = {} if context is None else dict(keys_features=context[0], map_rooms=context[1],
                map_flags=context[2], history_rooms=context[3], history_dist=context[4], map_outgoing_mask=context[5])
            result = model(seq, upgrades, counts, globals_, characters, bosses, **kwargs, return_keys=use_map)
            if use_map:
                outputs, key_logits = result
                kl, km = (t.to(device) for t in keys)
                for key, value in (("keys", torch.sigmoid(key_logits)), ("kl", kl), ("km", km)):
                    saved[key].append(value.cpu().numpy())
            else:
                outputs, kl, km = result, None, None
            d = distribution(outputs, floors, resolution)
            p = torch.cat((d["milestones"], d["heart_probability"][:, None]), 1).cpu().numpy()
            fn, tn = floors.cpu().numpy(), terminal.cpu().numpy()
            known, heart = masks[:, -1].bool().cpu().numpy(), targets[:, -1].cpu().numpy()
            reached = tn[:, None] >= np.array((17,34,51,57))
            y = np.column_stack((reached, heart))
            m = np.column_stack(((np.array((17,34,51,57))[None] > fn[:, None]) & (known[:, None] | reached), known))
            bands = ascension_band_tensor(globals_).cpu().numpy()
            pred = d["expected_floor"].cpu().numpy()
            entries = dict(p=p, y=y, m=m, floor=fn, terminal=tn, known=known, bands=bands,
                asc=torch.round(globals_[:, GlobalFeatureEncoder.feature_names.index("ascension")]*20).long().cpu().numpy(),
                character=characters.cpu().numpy(), prediction=pred, hp=torch.sigmoid(outputs.hazards).cpu().numpy(),
                hy=targets[:, :-1].cpu().numpy(), hm=masks[:, :-1].bool().cpu().numpy(), near=d["near"].cpu().numpy())
            for key, value in entries.items():
                saved[key].append(value)
            if recorder is not None:
                identities = recorder.identities(len(p))
                eligible = torch.tensor([selected_run(rid, .01) for rid, _, _ in identities], device=device)
                pc, pv = mechanism_counts(model, outputs, seq, upgrades, counts, globals_, characters,
                    bosses, floors, context, kl, km, egg_token_ids, eligible)
                weights = np.ones(len(p)) if band_weights is None else np.asarray(band_weights)[bands]
                recorder.add(p, y, m, weights, pc, pv, floors=fn, predicted=pred, terminal=tn, terminal_known=known)
            now = time.monotonic()
            if progress and (batch_index == 1 or now-last_log >= Config.LOG_INTERVAL_SECONDS or batch_index == len(loader)):
                progress(f"{name}: {batch_index:,}/{len(loader):,} batches; rate={batch_index/max(now-started,1e-9):.1f} batches/s")
                last_log = now
    if not saved["p"]:
        raise ValueError("Evaluation split has no valid samples")
    a = {k: np.concatenate(v) for k, v in saved.items() if v}
    a["prediction"] = a["prediction"].astype(np.float64)
    a["terminal"] = a["terminal"].astype(np.float64)
    w = np.ones(len(a["p"])) if band_weights is None else np.asarray(band_weights)[a["bands"]]
    common = baseline_statistics["common"] if baseline_statistics is not None else None
    rates = common["global"]["rates"] if common else [.5]*5

    def summary(include):
        result = nll_metrics(a["p"][include], a["y"][include], a["m"][include], rates, w[include])
        known = include & a["known"]
        result["samples"] = int(include.sum())
        result["terminal_samples"] = int(known.sum())
        result["terminal_floor_mae"] = terminal_floor_mae(a["prediction"][known], a["terminal"][known])
        result["heart_win"] = _classification_metrics(a["y"][known, -1], a["p"][known, -1])
        result["heart_win"].update(samples=int(known.sum()), positive_rate=float(a["y"][known, -1].mean()) if known.any() else float("nan"))
        return result

    all_rows = np.ones(len(w), dtype=bool)
    metrics = summary(all_rows)
    metrics["common_targets"] = list(COMMON_TARGET_NAMES)
    metrics["resolution_mode"] = resolution
    hp = np.clip(a["hp"], 1e-7, 1-1e-7)
    hazard_elements = -(a["hy"]*np.log(hp)+(1-a["hy"])*np.log1p(-hp))
    for label, local in (("near", a["near"]), ("far", ~a["near"])):
        mask = a["hm"] * local * w[:, None]
        metrics[label+"_nll"] = float((hazard_elements*mask).sum()/mask.sum()) if mask.sum() else float("nan")
    metrics["key_bce"], metrics["keys"] = 0., {}
    if use_map:
        p = np.clip(a["keys"], 1e-7, 1-1e-7)
        elements = -(a["kl"]*np.log(p)+(1-a["kl"])*np.log1p(-p))
        mask = a["km"].astype(bool)
        metrics["key_bce"] = float(elements[mask].mean()) if mask.any() else 0.
        for i, key in enumerate(("ruby", "emerald", "sapphire")):
            rows = mask[:, i]
            item = _classification_metrics(a["kl"][rows, i], p[rows, i])
            item.update(samples=int(rows.sum()), positive_rate=float(a["kl"][rows, i].mean()) if rows.any() else float("nan"))
            metrics["keys"][key] = item
    for grouping, values in (("by_character", a["character"]), ("by_ascension", a["asc"]),
                             ("by_floor", a["floor"])):
        metrics[grouping] = {str(int(v)): summary(values == v) for v in np.unique(values)}
    try:
        from .data_contract import ASCENSION_BAND_NAMES
    except ImportError:
        from data_contract import ASCENSION_BAND_NAMES
    metrics["by_ascension_band"] = {ASCENSION_BAND_NAMES[int(v)]: summary(a["bands"] == v) for v in np.unique(a["bands"])}
    names = [f"fine_stop_before_{e:02}" for e in HAZARD_ENDPOINTS] + [f"coarse_stop_before_{e:02}" for e in COARSE_ENDPOINTS]
    metrics["hazards"], metrics["by_distance"], metrics["common_by_distance"] = {}, {}, {}
    ends = np.asarray(HAZARD_ENDPOINTS+COARSE_ENDPOINTS)
    for i, key in enumerate(names):
        rows = a["hm"][:, i]
        metrics["hazards"][key] = _bucket_metrics(a, rows, i, w, _classification_metrics)
    for label, low, high in (("1-3", 1, 3), ("4-8",4,8), ("9-16",9,16), ("17+",17,57)):
        common_distance = np.array((17,34,51,57,57))[None] - a["floor"][:, None]
        common_mask = a["m"] & (common_distance >= low) & (common_distance <= high)
        common_metrics = nll_metrics(a["p"], a["y"], common_mask, rates, w)
        heart_rows = common_mask[:, -1]
        common_metrics["heart_win"] = _classification_metrics(a["y"][heart_rows,-1], a["p"][heart_rows,-1])
        common_metrics["terminal_samples"] = int(heart_rows.sum())
        common_metrics["terminal_floor_mae"] = terminal_floor_mae(a["prediction"][heart_rows], a["terminal"][heart_rows])
        metrics["common_by_distance"][label] = common_metrics
        distance = ends[None] - a["floor"][:, None]
        metrics["by_distance"][label] = {}
        for i, key in enumerate(names):
            rows = a["hm"][:, i] & (distance[:, i] >= low) & (distance[:, i] <= high)
            metrics["by_distance"][label][key] = _bucket_metrics(a, rows, i, w, _classification_metrics)
    metrics["tail_coverage"] = {}
    for e in (37,57):
        metrics["tail_coverage"][str(e)] = {}
        for label, near in (("near", True), ("far", False)):
            rows = a["hm"] & ((ends == e)[None]) & (a["near"] if near else ~a["near"])
            metrics["tail_coverage"][str(e)][label] = dict(samples=int(rows.sum()), stops=int((a["hy"]*rows).sum()))
    for label, contextual in (("constant_baseline", False), ("contextual_baseline", True)):
        bp = np.broadcast_to(rates, a["p"].shape).copy()
        bf = np.full(len(w), common["terminal_mean"] if common and common["terminal_mean"] is not None else 28.5)
        if contextual and common:
            for i, (floor, band) in enumerate(zip(a["floor"], a["bands"])):
                entry = common["strata"].get(f"{int(floor)}:{int(band)}")
                if entry:
                    bp[i] = entry["rates"]
                    if entry.get("terminal_mean") is not None:
                        bf[i] = entry["terminal_mean"]
        result = nll_metrics(bp, a["y"], a["m"], rates, w)
        observed = a["known"]
        result["terminal_floor_mae"] = terminal_floor_mae(np.maximum(bf, a["floor"])[observed], a["terminal"][observed])
        metrics[label] = result
    metrics["skill"] = 1-metrics["common_nll"]/metrics["contextual_baseline"]["common_nll"]
    metrics["value"] = metrics["common_nll"]
    metrics["total"] = metrics["loss"] = metrics["value"] + Config.KEY_HEAD_LOSS_WEIGHT*metrics["key_bce"]
    return metrics


def _bucket_metrics(a, rows, index, weights, classify):
    result = classify(a["hy"][rows, index], a["hp"][rows, index])
    result.update(samples=int(rows.sum()), stops=int(a["hy"][rows, index].sum()))
    if rows.any():
        p = np.clip(a["hp"][rows, index], 1e-7, 1-1e-7)
        y = a["hy"][rows, index]
        result["nll"] = float(np.average(-(y*np.log(p)+(1-y)*np.log1p(-p)), weights=weights[rows]))
    else:
        result["nll"] = float("nan")
    return result
