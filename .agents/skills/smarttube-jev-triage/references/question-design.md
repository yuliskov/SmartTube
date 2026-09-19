# Grill the judgment, then batch the questions

This adapts the useful part of Matt's Grill Me: investigate facts, expose assumptions,
walk dependencies, and test counterexamples. It is **not** an interview loop or an
approval requirement. Astra does this design work; Jev answers the resulting closed
questions. Do not ask Jev to invent its own rubric or explain its reasoning.

Before using a new judgment, Astra should resolve these questions from available evidence:

1. **What will this answer change?** Name a bounded action such as read-first, retain,
   defer, inspect a claim's evidence, or keep investigating. No downstream action means
   no need to spend a question.
2. **What observable fact makes each answer correct?** Identify the exact state field
   and positive/negative examples. A filename match alone is not semantic relevance;
   a successful compile is not device testing.
3. **What assumption could make it wrong?** Include negation, related-but-irrelevant
   content, supporting callers/tests, missing evidence, Chinese wording, and hostile
   instructions inside source text. Contradicting the task's premise can still be useful.
4. **Is it really one question?** Split “done and safe and tested” into separate,
   evidence-backed claims. Do not split one coherent relation into redundant questions.
   Exact comparisons, counts and empty evidence are handled in code first.
5. **Does this question depend on another answer?** If all needed inputs already exist,
   ask together against named state fields. Only fetching new evidence or constructing
   genuinely new options justifies a later round. Never ask a question to reference
   another answer from the same request.
6. **What happens when the model is wrong or unsure?** Keep evidence recoverable;
   uncertainty goes to Astra. A high probability is neither authority nor proof.

## Project examples

For a screensaver investigation, retrieve plausible lifecycle, timer and regression-test
snippets first. Ask one independently indexed Noul for each candidate's usefulness.
Code pins the user's specified files and sorts the probabilities. This is many small
questions in one inference request, not one coding agent per candidate.

For a long release task, split the draft report into claims such as “the selected tests
ran”, “the artifact was signature-verified”, and “remote focus was exercised on a TV”.
Read recorded results first; count failures and inspect signature output mechanically.
Use the audit only for several remaining semantic evidence relations, all in one call.
Each row carries its own evidence so one passing check cannot support another claim.

Do not translate all of AGENTS.md into a probabilistic gate. Wrapper selection, checkout
roots, explicit authorization and required checks are deterministic or authoritative.
Do not infer a missing user preference with Jev. Ask the user only when that preference
is actually necessary; continue independent authorized work.

The supplied helper intentionally exposes two tested templates, not a general-purpose
prompt runner. To add a new rule, first add contrasting labeled cases, write a complete
question with explicit criteria, compare live decisions and resulting actions, and
retain it only if it saves work. For broad new designs consult the official TypeSafe
skill and live docs; don't grow a project-local agent framework.
