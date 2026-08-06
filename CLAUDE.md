具体说明请见 `CLAUDE.md`。

## Working Preferences

- Prefer small, reviewable changes.
- Ask before adding dependencies.
- Always explain what changed and how it was verified.
- If tests cannot run, state why and provide the exact command to run.
- Do not modify secrets, credentials, lockfiles, or generated files unless explicitly requested.

## Slay the Spire Mods

目前部署 4 个 mod，每次对应代码改动后必须同步更新其版本号，并保持描述与实际功能一致：

| Mod | 目录 | Mod ID | 版本/描述位置 |
|---|---|---|---|
| STS Save State Mod | `STSStateSaver/` | `SaveStateMod` | `pom.xml` 的 `<version>` / `<description>`（过滤注入 `ModTheSpire.json`） |
| Ludicrous Speed | `LudicrousSpeed/` | `LudicrousSpeed` | 同上 |
| Battle Ai Mod | `scumthespire/` | `BattleAiMod` | 同上 |
| Communication Mod | `StSCommunicationMod/` | `CommunicationMod` | `src/main/resources/ModTheSpire.json`（硬编码） |

版本更新规则（semver）：破坏性变更 → major；新功能（feat）→ minor；修复/性能（fix/perf）→ patch。`pom.xml` 中 `ModTheSpire.json` 的 `description` 使用 `${project.description}` 占位符，故描述也写在 `pom.xml`（注意 XML 转义）；Communication Mod 直接改 JSON。

更新后验证：`mvn package` 并确认 `target/classes/ModTheSpire.json` 中的 `version` 和 `description` 已正确生成，再部署到 `_ModTheSpire/mods/`。

## Set Transformer v2

- The v2 processed dataset lives in `selectcard/processed_data_v2/`; it is generated and must not be committed.
- Rebuild it from `selectcard/` with `python src/data_pipeline.py`.
- Run model tests from `selectcard/src/` with `python -m unittest test_value_network_v2.py`.
- Train from `selectcard/` with `python src/train.py`; use a CUDA-enabled PyTorch environment for the full dataset.
- The default architecture is one early Global token, Pre-LN attention blocks, and final CLS pooling. Ablations are controlled by `GLOBAL_CONDITIONING` and `NORM_POSITION` in `selectcard/src/config.py`.
- v2 checkpoints embed model configuration, the frozen vocabulary, normalization statistics, and preprocessing version. Legacy bare `state_dict` checkpoints are intentionally incompatible and require retraining.
- Do not commit `selectcard/checkpoints/`, `selectcard/processed_data/`, or `selectcard/processed_data_v2/` artifacts.
