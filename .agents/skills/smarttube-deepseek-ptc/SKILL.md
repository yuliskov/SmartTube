---
name: smarttube-deepseek-ptc
description: DeepSeek Harness compatibility layer for SmartTube-AI. Use when running DeepSeek Harness, DSH_TOOLS_MODE=ptc, or a DeepSeek V4.1 Flash deployment.
---

# SmartTube DeepSeek Harness adapter

This is a thin adapter over the shared project rules. It does not supersede the root `AGENTS.md`, the existing Astra workflow, or the project build skill.

## Harness contract

- DeepSeek Harness discovers this file from `.agents/skills/*/SKILL.md`; load it by its exact skill name before a matching task.
- Harness also injects the applicable `AGENTS.md` files. Treat those instructions as the shared engineering contract.
- With `DSH_TOOLS_MODE=ptc`, the model sees the reserved `run_code` transport and a generated SDK section. The program written in `run_code` calls ordinary async bindings; nested calls re-enter the guarded tool pipeline and are recorded as PTC dispatch events.
- PTC runs are session-scoped and should return structured, bounded output. Do not depend on conversational narration to preserve loop state.

## DeepSeek/PTC operating pattern

1. State the outcome, scope, constraints, and verification commands in a short task frame.
2. Load only the matching shared skill(s). Prefer the existing `smarttube-build` skill for Gradle/build work.
3. Use PTC code for deterministic work: enumerate files, filter candidates, extract bounded excerpts, run repeated checks, aggregate status, and keep a small state object.
4. Keep model reasoning for architecture, ambiguous evidence, implementation choices, and failure diagnosis. Return paths, line numbers, exit codes, and the smallest useful excerpts.
5. After writes, run the narrowest relevant validation, then widen only after a failure or unresolved risk. Never treat a successful PTC dispatch as proof that the Android build passed.
6. On a tool/runtime failure, preserve the error and inputs, retry only when the failure is transient and the retry changes the request, otherwise fall back to direct tool calls or ask Astra to resolve the ambiguity.

## Tool-call guidance

- Use explicit, small schemas and stable field names. Avoid giant free-form tool results and repeated repository dumps.
- Batch independent read/search operations in one PTC program when ordering is irrelevant; keep edits, approvals, and dependent commands sequential.
- Do not recursively invoke `run_code`, leak credentials, or use PTC to authorize destructive or external actions.
- For Android changes, preserve TV focus/remote behavior, shared checkout selection, JDK 17, and the checked-in Gradle wrapper from the shared project rules.

## Model note

DeepSeek's public API documentation documents tool calls, thinking/non-thinking requests, and beta strict JSON mode. It does not establish a public specification for a model named “DeepSeek V4.1 Flash” in this workspace. Therefore this skill avoids model-name branching and relies on the active deployment's actual tool/context behavior.

## Primary references

- DeepSeek API tool calls: https://api-docs.deepseek.com/guides/tool_calls
- DeepSeek API pricing/model identifiers: https://api-docs.deepseek.com/quick_start/pricing
- DeepSeek Harness source: https://github.com/deepseek-ai/deepseek-harness
- Harness PTC execution docs: `docs/tool-execution-pipeline.md`, `packages/ptc-runtime/README.md`, and `docs/tool-catalog.md` in the official repository.
