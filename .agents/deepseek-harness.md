# DeepSeek Harness project adapter

This file is a human-readable map for operators and Harness presets. The executable discovery points are the root `AGENTS.md` and `.agents/skills/smarttube-deepseek-ptc/SKILL.md`.

Use the shared project rules and existing skills first. Enable `DSH_TOOLS_MODE=ptc` for deterministic repository inspection, bounded extraction, repeated validation, and aggregation; keep implementation and architectural judgment in the model. The adapter must never change the Astra-first priority.

Expected PTC flow:

`task frame → load matching skill → run_code with SDK bindings → bounded evidence/state → sequential edit → narrow validation → concise result`

The repository has no DeepSeek model-name switch. This keeps Codex/Astra behavior unchanged and lets the active DeepSeek deployment advertise its real capabilities.
