import copy
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
import jev


CONFIG = ("test-secret", jev.ENDPOINT, jev.MODEL)


def rank_data(n=8):
    return {"task": "Investigate screensaver lifecycle", "candidates": [
        {"id": f"c{i}", "source": f"fixture:{i}", "text": "verbatim evidence"} for i in range(n)]}


def audit_data():
    return {"task": "Report verification", "checks": [
        {"id": "tests", "source": "fixture:log", "claim": "Tests passed", "evidence": "Tests ran"}]}


def reply(payload, ps=None, confidence=.95):
    answers = {}
    for key, q in payload["questions"].items():
        if q["type"] == "noul":
            answers[key] = {"type": "noul", "noul": .5}
        else:
            probs = ps or {"supports": .98, "contradicts": .01, "insufficient": .01}
            answers[key] = {"type": "choice", "choice": max(probs, key=probs.get),
                            "probabilities": probs, "confidence": confidence}
    return {"model": payload["model"], "answers": answers, "usage": {"input_tokens": 123}}


class PolicyTests(unittest.TestCase):
    def run_case(self, data, mode, fn):
        return jev.run(data, mode, live=True, config=CONFIG, transport=lambda p, k, e: fn(p))

    def test_small_lists_bypass_network(self):
        r = self.run_case(rank_data(7), "rank", lambda p: self.fail("network"))
        self.assertEqual(r["calls"], 0)
        self.assertEqual(len(r["context"]), 7)

    def test_offline_and_missing_key_retain_everything(self):
        for r in (jev.run(rank_data(), "rank"),
                  jev.run(rank_data(), "rank", live=True, config=("", jev.ENDPOINT, jev.MODEL))):
            self.assertEqual(r["calls"], 0)
            self.assertEqual(len(r["context"]), 8)

    def test_pins_are_not_sent_as_questions(self):
        data = rank_data()
        data["candidates"][0]["pinned"] = True
        def response(p):
            self.assertNotIn("c0", p["questions"])
            return reply(p)
        r = self.run_case(data, "rank", response)
        self.assertEqual(r["rows"][0]["action"], "pinned")

    def test_rank_boundaries_preserve_uncertain_and_index(self):
        data = rank_data()
        def response(p):
            result = reply(p)
            for k, value in zip(result["answers"], [.10, .101, .849, .85, 0, 1, .5, .6]):
                result["answers"][k]["noul"] = value
            return result
        r = self.run_case(data, "rank", response)
        actions = {x["id"]: x["action"] for x in r["rows"]}
        self.assertEqual(actions["c0"], "defer")
        self.assertEqual(actions["c1"], "astra_review")
        self.assertEqual(actions["c2"], "astra_review")
        self.assertEqual(actions["c3"], "read_first")
        self.assertEqual(len(r["rows"]), 8)
        self.assertEqual(len(r["context"]), 6)
        self.assertEqual(data, rank_data())

    def test_all_independent_questions_are_batched(self):
        seen = []
        def response(p):
            seen.append(p)
            self.assertEqual(len(p["questions"]), 8)
            self.assertIn("`candidates[7].text`", p["questions"]["c7"]["instructions"])
            return reply(p)
        self.run_case(rank_data(), "rank", response)
        self.assertEqual(len(seen), 1)

    def test_empty_evidence_is_deterministic(self):
        data = audit_data()
        data["checks"][0]["evidence"] = ""
        r = self.run_case(data, "audit", lambda p: self.fail("network"))
        self.assertEqual(r["calls"], 0)
        self.assertEqual(r["rows"][0]["reason"], "empty_evidence")

    def test_choice_confidence_is_not_winner_probability(self):
        for probs, conf in [({"supports": .98, "contradicts": .01, "insufficient": .01}, .2),
                            ({"supports": .6, "contradicts": .2, "insufficient": .2}, .95)]:
            r = self.run_case(audit_data(), "audit", lambda p: reply(p, probs, conf))
            self.assertEqual(r["rows"][0]["action"], "astra_review")

    def test_confident_relation_is_never_task_approval(self):
        for label in jev.RELATIONS:
            ps = {k: float(k == label) for k in jev.RELATIONS}
            r = self.run_case(audit_data(), "audit", lambda p: reply(p, ps))
            action = r["rows"][0]["action"]
            self.assertEqual(action, "supported_in_supplied_evidence" if label == "supports" else "inspect_evidence_gap")
            self.assertIn("overall task completion", r["not_checked"])

    def test_provider_errors_never_drop_evidence(self):
        errors = [TimeoutError(), urllib.error.HTTPError(jev.ENDPOINT, 429, "secret body", {}, None),
                  urllib.error.HTTPError(jev.ENDPOINT, 401, "secret body", {}, None)]
        for error in errors:
            def response(p):
                raise error
            r = self.run_case(rank_data(), "rank", response)
            self.assertEqual(r["status"], "fallback")
            self.assertEqual(len(r["context"]), 8)
            self.assertNotIn("secret", jev.encode(r))

    def test_malformed_or_partial_response_falls_back_whole_batch(self):
        for kind in ("missing", "extra", "nan", "range", "bool", "model", "type"):
            def response(p):
                r = reply(p)
                if kind == "missing": del r["answers"]["c0"]
                elif kind == "extra": r["answers"]["other"] = {}
                elif kind == "model": r["model"] = "different-model"
                elif kind == "type": r["answers"]["c0"]["type"] = "score"
                else: r["answers"]["c0"]["noul"] = {"nan": float("nan"), "range": 1.1, "bool": True}[kind]
                return r
            r = self.run_case(rank_data(), "rank", response)
            self.assertEqual(r["status"], "fallback", kind)
            self.assertEqual(len(r["context"]), 8)

    def test_invalid_choice_distributions_fall_back(self):
        for probs in ({"supports": .8}, {"supports": .8, "contradicts": .8, "insufficient": .1}):
            r = self.run_case(audit_data(), "audit", lambda p: reply(p, probs))
            self.assertEqual(r["status"], "fallback")

    def test_oversize_and_duplicate_inputs_rejected(self):
        for data in (rank_data(25), rank_data()):
            if len(data["candidates"]) == 8:
                data["candidates"][0]["text"] = "字" * 10000
            with self.assertRaises(ValueError):
                jev.run(data, "rank")
        data = rank_data()
        data["candidates"][1]["id"] = "c0"
        with self.assertRaises(ValueError): jev.run(data, "rank")
        with self.assertRaises(ValueError): jev.load_json('{"task":1,"task":2}')
        with self.assertRaises(ValueError): jev.load_json('{"task":NaN}')

    def test_configuration_never_changes_credential_provider(self):
        with tempfile.TemporaryDirectory() as tmp, patch.dict(os.environ, {}, clear=True):
            root = Path(tmp)
            (root / "local.properties").write_text("jev.api.key=local-secret\njev.api.baseUrl=https://openrouter.ai/api/v1", encoding="utf-8")
            self.assertEqual(jev.configuration(root), ("local-secret", jev.OPENROUTER_ENDPOINT, jev.OPENROUTER_MODEL))
            with patch.dict(os.environ, {"TYPESAFE_API_KEY": "direct-secret"}):
                self.assertEqual(jev.configuration(root), ("direct-secret", jev.ENDPOINT, jev.MODEL))
            (root / "local.properties").write_text("jev.api.key=local-secret\njev.api.baseUrl=https://example.com", encoding="utf-8")
            with self.assertRaises(ValueError): jev.configuration(root)

    def test_redirects_are_not_followed(self):
        self.assertIsNone(jev.NoRedirect().redirect_request(None, None, 302, "", {}, "https://example.com"))

    def test_changed_evidence_changes_identity(self):
        data = rank_data()
        old = jev.run(data, "rank")["input_sha256"]
        data["task"] += " and remote focus"
        self.assertNotEqual(old, jev.run(data, "rank")["input_sha256"])


if __name__ == "__main__":
    unittest.main()
