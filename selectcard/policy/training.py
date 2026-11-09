"""BC and Monte Carlo AWR primitives. No shared critic/policy parameters."""
from collections import defaultdict
import random
import torch
from torch.nn import functional as F
from .contracts import digest
from .encoding import encode_batch


def completed(example):
    return example.get("outcome", {}).get("terminal_known") is True


def is_bc_example(example):
    return ordinary(example) and completed(example) and example["outcome"].get("victory") is True


def ordinary(example):
    mode = example.get("run_mode")
    flags = ("is_daily", "is_endless", "is_trial", "is_beta", "chose_seed", "special_seed")
    return mode is not None and all(k in mode for k in flags) and not any(mode[k] for k in flags)


def is_awr_example(example):
    return ordinary(example) and completed(example)


def heart_return(example):
    if not completed(example) or example["outcome"].get("heart_victory") not in (True, False):
        raise ValueError("censored/unknown Heart outcome cannot become a failure")
    return float(example["outcome"]["heart_victory"])


def pilot_member(run_id):
    return int(digest(run_id)[:16], 16) % 100 == 0


def frequency_key(task, candidate):
    return digest([task, candidate["kind"], candidate.get("entity_kind"), candidate.get("entity_id"), candidate.get("purpose")])


def balanced_indices(examples, count, rng):
    groups = defaultdict(lambda: defaultdict(list))
    for i, example in enumerate(examples):
        s = example["pre_state"]
        a = s["ascension"]
        band = 0 if a < 10 else 1 if a < 15 else 2 if a < 20 else 3
        groups[(s["character"], band, example["decision_type"])][example["run_id"]].append(i)
    groups = [list(runs.values()) for runs in groups.values()]
    return [rng.choice(rng.choice(rng.choice(groups))) for _ in range(count)]


def awr_weights(returns, values, beta=.1, cap=20.):
    if beta <= 0 or cap <= 0:
        raise ValueError("beta and cap must be positive")
    return torch.exp(((returns - values.detach()) / beta).clamp(max=__import__("math").log(cap))).detach()


def train_epoch(model, examples, vocabulary, optimizer, *, batch_size=512, seed=42,
                critic=None, beta=.1, cap=20., value_only=False, device="cpu"):
    if not examples:
        raise ValueError("no eligible training examples")
    rng = random.Random(seed)
    indices = examples.balanced_indices(len(examples), rng) if hasattr(examples, "balanced_indices") else balanced_indices(examples, len(examples), rng)
    model.train()
    if critic is not None:
        critic.eval()
    total, count = 0., 0
    import itertools
    indices = iter(indices)
    while True:
        chunk = list(itertools.islice(indices, batch_size))
        if not chunk:
            break
        rows = []
        for index in chunk:
            row = dict(examples[index])
            row["candidates"] = list(row["candidates"])
            rng.shuffle(row["candidates"])
            rows.append(row)
        batch = encode_batch(rows, vocabulary, device=device)
        optimizer.zero_grad(set_to_none=True)
        if value_only:
            returns = torch.tensor([heart_return(row) for row in rows], device=device)
            loss = F.mse_loss(model(batch), returns)
        else:
            losses = F.cross_entropy(model(batch), batch["labels"], reduction="none")
            if critic is not None:
                returns = torch.tensor([heart_return(row) for row in rows], device=device)
                with torch.no_grad():
                    weights = awr_weights(returns, critic(batch), beta, cap)
                losses = losses * weights
            loss = losses.mean()
        if not torch.isfinite(loss):
            raise ValueError("nonfinite policy loss")
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.)
        optimizer.step()
        total += loss.item() * len(rows)
        count += len(rows)
    return total / count


@torch.no_grad()
def evaluate(model, examples, vocabulary, *, batch_size=512, device="cpu", frequencies=None):
    model.eval()
    totals = defaultdict(lambda: defaultdict(float))
    for start in range(0, len(examples), batch_size):
        rows = examples[start:start + batch_size]
        batch = encode_batch(rows, vocabulary, device=device)
        logits = model(batch)
        losses = F.cross_entropy(logits, batch["labels"], reduction="none")
        predictions = logits.argmax(dim=1).tolist()
        for i, row in enumerate(rows):
            a = row["pre_state"]["ascension"]
            band = "0-9" if a < 10 else "10-14" if a < 15 else "15-19" if a < 20 else "20"
            for group in ("all", "task:" + row["decision_type"], "character:" + row["pre_state"]["character"], "ascension:" + str(a), "ascension_band:" + band):
                t = totals[group]
                legal = [j for j, c in enumerate(row["candidates"]) if c["legal"]]
                scores = {j: (frequencies or {}).get(frequency_key(row["decision_type"], row["candidates"][j]), 0) for j in legal}
                ties = [j for j in legal if scores[j] == max(scores.values())]
                t["count"] += 1
                t["nll"] += losses[i].item()
                t["accuracy"] += predictions[i] == batch["labels"][i].item()
                t["random_accuracy"] += 1 / len(legal)
                t["frequency_accuracy"] += float(batch["labels"][i].item() in ties) / len(ties)
                t["skip_rate"] += row["candidates"][predictions[i]]["kind"] in ("skip", "leave", "cancel")
                skips = {j for j in legal if row["candidates"][j]["kind"] in ("skip", "leave", "cancel")}
                label = batch["labels"][i].item()
                t["label_skip_rate"] += label in skips
                t["random_skip_rate"] += len(skips) / len(legal)
                t["random_nll"] += __import__("math").log(len(legal))
                t["frequency_skip_rate"] += len(set(ties) & skips) / len(ties)
                # Laplace smoothing yields a proper, finite categorical baseline.
                t["frequency_nll"] += -__import__("math").log((scores[label]+1) / (sum(scores.values())+len(legal)))
    return {group: {k: v if k == "count" else v / values["count"] for k, v in values.items()} for group, values in totals.items()}
