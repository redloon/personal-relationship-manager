"""Conservative example predicate; callers must verify target inputs separately."""
import re


def can_reuse(original, target, *, distinct_objective=False):
    if distinct_objective or not isinstance(original, dict) or not isinstance(target, dict):
        return False
    if original.get('status') != 'passed' or type(original.get('test_count')) is not int or original['test_count'] <= 0:
        return False
    if not isinstance(original.get('job_id'), str) or not re.fullmatch('[0-9a-f]{32}', original['job_id']):
        return False
    for field in ('app_sha256', 'test_sha256'):
        value = original.get(field)
        if not isinstance(value, str) or not re.fullmatch('[0-9a-f]{64}', value) or target.get(field) != value:
            return False
    source = original.get('provenance')
    current = target.get('provenance')
    if not isinstance(source, dict) or not isinstance(current, dict):
        return False
    digest = source.get('source_sha256')
    if not isinstance(digest, str) or not re.fullmatch('[0-9a-f]{64}', digest) or current.get('source_sha256') != digest:
        return False
    return 'filter' in original and 'filter' in target and original['filter'] == target['filter']
