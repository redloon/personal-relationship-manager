import copy
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('evidence', ROOT/'scripts/delivery_evidence.py')
evidence = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evidence)


class DeliveryTests(unittest.TestCase):
    def test_evidence_association_preserves_history(self):
        original = dict(job_id='a'*32, status='passed', test_count=186, filter=None,
                        app_sha256='b'*64, test_sha256='c'*64,
                        provenance=dict(source_sha256='d'*64, git_revision='e'*40))
        saved = copy.deepcopy(original)
        target = copy.deepcopy(original)
        target['provenance']['git_revision'] = 'f'*40
        self.assertTrue(evidence.can_reuse(original, target))
        self.assertEqual(original, saved)
        for field in ('app_sha256', 'test_sha256', 'filter'):
            changed = copy.deepcopy(target)
            changed[field] = 'changed'
            self.assertFalse(evidence.can_reuse(original, changed))
        for source in (None, '', 'x'*64, '0'*64):
            changed = copy.deepcopy(target)
            changed['provenance']['source_sha256'] = source
            self.assertFalse(evidence.can_reuse(original, changed))
        for field, value in (('test_count', 0), ('test_count', True), ('status', 'running'), ('job_id', None)):
            changed = dict(original, **{field: value})
            self.assertFalse(evidence.can_reuse(changed, target))
        self.assertFalse(evidence.can_reuse(original, target, distinct_objective=True))
        for field in ('filter', 'provenance', 'test_sha256'):
            changed = copy.deepcopy(target)
            del changed[field]
            self.assertFalse(evidence.can_reuse(original, changed))

    def test_scope_examples_and_workflow_selection_remain_explicit(self):
        guidance = (ROOT/'docs/delivery.md').read_text()
        for phrase in ('Review-only approval', 'Narrower follow-up feedback',
                       'Implementation approval', 'Commit-only authorization',
                       'Read-only review', 'Localized change', 'Small data-integrity fix',
                       'New journey', 'not a cache', 'original job'):
            with self.subTest(phrase=phrase):
                self.assertIn(phrase, guidance)
        skill = (ROOT/'.agents/skills/prm-feature-handoff/SKILL.md').read_text()
        self.assertIn('Commit only if explicitly authorized', skill)
        self.assertIn('not ordinary localized completion or commit-only tasks', skill)
        rows = {parts[1].strip(): [part.strip() for part in parts[2:4]]
                for line in guidance.splitlines() if line.startswith('|')
                for parts in [line.split('|')] if len(parts) >= 4}
        for authorization, allowed, forbidden in (
            ('Review-only approval', 'Inspect and report', 'Implement findings'),
            ('Narrower follow-up feedback', 'only the named feedback', 'rest of a review'),
            ('Implementation approval', 'approved changes and validate', 'Commit'),
            ('Commit-only authorization', 'coherent staged inputs', 'implement recommendations'),
        ):
            self.assertIn(allowed, rows[authorization][0])
            self.assertIn(forbidden, rows[authorization][1])
        self.assertIn('No default recording', rows['Localized change'][0])
        self.assertIn('no polished visual pack', rows['Small data-integrity fix'][0])
        self.assertIn('Carry these distinctions through compaction and worker briefs', guidance)
        worker = (ROOT/'.codex/agents/android-feature-worker.toml').read_text()
        self.assertIn('authorized deliverable, exclusions, human preferences and unauthorized recommendations', worker)


if __name__ == '__main__':
    unittest.main()
