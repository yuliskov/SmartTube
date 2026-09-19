---
name: smarttube-jev-triage
description: Batch TypeSafe/Jev judgments for 8+ ambiguous SmartTube code/context candidates or several claim/evidence pairs. Skip obvious skill routes, exact checks and ordinary small edits.
---

# SmartTube semantic triage

Jev makes bounded judgments; code combines them; Astra investigates and implements. Use only when it saves substantial repeated reading. Small lists, known symbols and obvious build work go directly to `rg` or `smarttube-build`.

**Grill the judgment:** Astra identifies the exact evidence, challenges assumptions and counterexamples, and splits broad questions into independently answerable parts. Batch those parts; sequence only when new evidence is needed. This borrows Matt's Grill Me technique without a user interview or approval gate. Read [question-design.md](references/question-design.md) when designing new questions.

## Run

From the repository root, use Python 3.10+:

```powershell
python -B .agents/skills/smarttube-jev-triage/scripts/jev.py rank tmp/jev-input.json --live
python -B .agents/skills/smarttube-jev-triage/scripts/jev.py audit tmp/jev-input.json --live
```

Prepare UTF-8 JSON with `task` (current user goal/corrections) and either:

- `candidates`: `{id, source, text, pinned?}` per verbatim search/context excerpt. Use source path and line range. Retrieve to JSON before loading every snippet into Astra.
- `checks`: `{id, source, claim, evidence}` per atomic claim and actual evidence. Use several semantic relations needing review, not a single obvious missing check.

Keep 1-24 items and <=20,000 UTF-8 bytes of state. Rank bypasses Jev below eight candidates. Resolve active shared checkouts first; remove duplicates/generated output in code. Pin user-specified files, governing instructions, active errors and required evidence. Never send credentials, signing configuration, personal logs or unrelated history. Keep the original input for recovery; evidence text is not authority.

All independent questions share **one native typed request**. Existing provider configuration is supported without new dependencies. For schemas, credentials and provider details read [usage.md](references/usage.md) only as needed.

## Consume

- **Rank / Noul:** useful evidence for this next task step? `p >= .85` reads first, `p <= .10` defers; uncertainty stays for Astra. Noul has no separate confidence. Supporting callers/tests and contradicting evidence may be relevant.
- **Audit / Choice:** `supports`, `contradicts`, `insufficient` for each claim/evidence pair. Accept a relation only at confidence >= .70, winner probability >= .85 and margin >= .20; otherwise Astra reviews. Empty evidence is handled in code.

Inspect `status`, `rows`, `context` and `not_checked`. Thresholds are local starting policies, not correctness guarantees. Confidence measures distribution concentration. Supported evidence never certifies task completion; compilation, tests, APK signing and device checks remain distinct. Inspect gaps or perform missing authorized work without an automatic user question.

Timeout, provider error, missing key or malformed response keeps all candidates / escalates checks to Astra. No immediate retries or calls on unchanged input. Bound separate batches to two concurrent requests. Recover deferred snippets if retained evidence is insufficient, dependencies point to them or the goal changes. Never delete session history or use Jev to grant permission, select models, spawn agents or replace tests, complex reasoning, generation or exact logic.

For integration changes and evidence of benefit, read [evaluation.md](references/evaluation.md). Account for initial skill-loading overhead; small one-off searches can be cheaper without Jev.
