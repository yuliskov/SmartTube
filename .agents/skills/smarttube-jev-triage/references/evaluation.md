# Evaluation and implementation rationale

Reviewed 2026-09-19, SmartTube HEAD `0dd1755e`. This is agent tooling only; no APK,
Android code, dependencies, submodule revisions, device or release settings changed.

## Project evidence and decisions

Inspected root instructions, README, settings/build Gradle files, CI/VirusTotal workflows,
issue templates, local skill and build reference, module inventory, recent commits,
presenter/playback/lifecycle code and ScreensaverManagerTest. No sibling SharedModules
or MediaServiceCore checkout exists on this machine, so the included submodules are
active. There was one project skill (`smarttube-build`) and no project agent runtime,
MCP configuration or script-based routing system. README's old JDK guidance conflicts
with CI's JDK 17; build routing already resolves this through current source files.

The useful semantic work is prioritizing code across TV views, common presenters,
service modules and bundled ExoPlayer, and comparing a long task's separate claims
against real verification evidence. Dependency resolution, paths, SDK versions,
exit codes, JUnit counts and signatures remain deterministic. Debugging root causes,
editing code, open research and interpreting architectural tradeoffs remain with Astra.

## Sources and reuse

Read [Introduction](https://docs.typesafe.ai/introduction) first. TypeSafe supplies
typed primitives over state; Jev is its System One model, not a prose generator.
Implementation follows [primitives](https://docs.typesafe.ai/primitives),
[confidence](https://docs.typesafe.ai/confidence), [HTTP API](https://docs.typesafe.ai/api),
[models](https://docs.typesafe.ai/models), [fan-out](https://docs.typesafe.ai/patterns/fan-out),
[reranking](https://docs.typesafe.ai/cookbooks/rerank_typesafe),
[RAG passages](https://docs.typesafe.ai/cookbooks/classifying_rag_passages),
[citation checks](https://docs.typesafe.ai/cookbooks/citation_check),
[skill suggestion](https://docs.typesafe.ai/cookbooks/skill_suggestion), the
[official skill](https://github.com/typesafe-ai/skills),
[eval methodology](https://evals.typesafe.ai/), and
[known limitations](https://docs.typesafe.ai/model-jaggedness/jev-1.13).
Official benchmark labels/results do not validate this repository's workflow.
OpenRouter transport uses its [official OpenAPI](https://openrouter.ai/openapi.json)
`/api/alpha/decisions`, with native typed questions and probabilities.

[Awesome Jev](https://awesomejev.com/) was used to find these patterns, then README
and implementation were inspected; no community package was executed or installed:

| Project / inspected source | Adopted or rejected |
| --- | --- |
| [jev-code](https://github.com/devagrawal09/jev-code), `src/workflows/find.ts`, `check-task.ts` at `fd092558` | Adopt bounded candidate evidence and explicit not-checked fields. No whole toolkit: README says npm release is a placeholder and Windows untested; its multi-stage 600-request budget is unnecessary here. |
| [jev-pref](https://github.com/doeixd/jev-pref), `src/suites/prefs.js`, `src/jev.js` at `9d77ea60` | Adopt independently judged rules and code-owned policy. Do not adopt aggregate approval or convert all AGENTS rules into fuzzy checks. |
| [fast-jev-compaction](https://github.com/tamaratran/fast-jev-compaction), `src/request.ts`, README at `e3f262a7` | Adopt verbatim retention and protected evidence. Reject automatic transcript rewrite/hook installation; current Codex workflow does not need it. |
| [jev-agent-skill-router](https://github.com/GodsBoy/jev-agent-skill-router), `router.py`, `policy.py` at `e481a06c`; [SkillRanker](https://github.com/Dicklesworthstone/skillranker) README | Adopt abstention and transparent distributions. No general skill-router: one obvious local build skill, large global catalog already visible, no demonstrated need to install another runtime or interfere with explicit routing. |
| [Foreman](https://github.com/thruwire/foreman), `foreman/jev.py`, `policy.py` at `209182da` | Considered supervision/completion checks; rejected autonomous finish/steer/worker orchestration. Whole-task correctness exceeds a reliable snap judgment. |

Existing Matt Grill Me skills interview humans. The official TypeSafe skill already
teaches decomposition. No inspected skill supplies this exact SmartTube workflow;
`question-design.md` adapts Grill Me's assumption/edge-case/dependency probing into
Astra-authored questions, without its human interview gates. Do not install a duplicate
general TypeSafe skill (one is already available globally).

## Actual results

Captured responses and counts: [evaluation-results.json](evaluation-results.json).
Fixtures are built from checked-out source slices plus explicitly synthetic claims.
Labels were specified before each live run; the second set adds a Chinese channel
task and new claim cases. It reuses candidate snippets, so it is not a statistically
independent production holdout or a calibration dataset.

| Measurement | Screensaver task | Chinese channel task |
| --- | ---: | ---: |
| Candidate excerpts retained | 4 / 12 | 2 / 12 |
| Labeled relevant excerpts retained | 3 / 3 | 1 / 1 |
| Original excerpt payload characters | 9,912 | 9,912 |
| Filtered excerpts characters | 3,114 | 1,682 |
| Entire rank report characters (including metadata) | 5,564 | 4,122 |
| Claim classifications matching labels | 11 / 12 | 8 / 8 |
| Wrongly accepted positive claims | 0 | 0 |
| Claims returned to Astra due to uncertainty | 3 | 1 |
| API requests (rank + audit) | 2 | 2 |
| Rank / audit elapsed milliseconds | 1183 / 1150 | 1228 / 1857 |
| API input tokens | 7,323 | 6,312 |

The four captured calls cost USD 0.00057267 according to API usage. Model returned
`typesafe/jev-1.13-20260917`. The initial audit had 12 model questions; the final policy
handles empty evidence mechanically, so the eight-case second audit uses seven.
Eleven candidate questions share one request in each run. These are measured small
batch results, not a claim of system-wide cost/latency savings or an Astra A/B comparison.

Rank reports reduced the candidate payload by 43.9% and 58.4%, including result metadata,
before instruction-loading overhead. First-use skill/reference loading can offset the
savings; use this for large/repeated ambiguous batches. Do not first read the entire
unfiltered input into Astra. No automatic pruning or blanket per-turn invocation.

One Chinese claim with absent device-test records was labeled `contradicts` rather
than `insufficient`; both map to inspect-evidence-gap, so there was no false completion.
Another Chinese case and the stale-evidence case correctly abstained under the unchanged
thresholds. Retained uncertain regression-test evidence was not discarded. This supports
advisory triage only, not calibrated accuracy, safe deletion or automatic approval.
The two hostile snippets did not override the rubric; this does not establish injection
resistance. Do not use this integration as a security boundary.

## Repeatable checks

```powershell
python -B -m unittest discover -s .agents/skills/smarttube-jev-triage/scripts -p test_jev.py -v
python -B .agents/skills/smarttube-jev-triage/scripts/evaluate.py --output tmp/jev-offline
python -B .agents/skills/smarttube-jev-triage/scripts/evaluate.py --live --holdout --output tmp/jev-live-new
```

15 offline tests cover batch construction, pinned material, threshold boundaries,
uncertainty, malformed distributions, model mismatch, key/provider isolation, no-key,
timeouts/429/401, redirects, duplicate/oversized inputs, small-list bypass and empty
evidence. Live tests use configured credentials and small bounded paid requests.
Recheck source ranges/labels if the repository changes. Direct TypeSafe transport follows
its documented contract but was not live-tested with a TypeSafe credential here.

Trigger review (manual application of the skill, not a claim of autonomous harness testing):

| Task | Expected route |
| --- | --- |
| 12 plausible lifecycle/player snippets after scoped retrieval | Jev rank |
| Several release report claims with mixed code/test/device evidence | Jev audit |
| Build stbeta / diagnose JDK setup | Existing smarttube-build directly |
| User explicitly names a skill | Load that skill directly |
| 3 known files / typo / exact version comparison / empty evidence | Ordinary code or direct reading |
| Explain a race, design playback architecture, write Java, research a feature | Astra |
| Missing key or uncertain model output | Continue with original evidence and Astra |

The new skill was also discovered in the current Codex skill catalog during this task.
No Android build is needed for these agent-only changes.
