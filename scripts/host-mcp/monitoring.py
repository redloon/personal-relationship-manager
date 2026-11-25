"""Bounded client-side example. The adapter calls existing MCP tools, never submits."""
import json


def monitor(call, job_id, retain_chunk, *, state=None, on_change=None,
            max_polls=2, max_chunks=16, wait_seconds=30):
    for value, lower, upper in ((max_polls, 1, 4), (max_chunks, 1, 128), (wait_seconds, 0, 30)):
        if type(value) is not int or not lower <= value <= upper:
            raise ValueError('Invalid monitoring budget')
    checkpoint = dict(state) if state is not None else dict(job_id=job_id, offset=0)
    if checkpoint.get('job_id') != job_id or type(checkpoint.get('offset')) is not int or checkpoint['offset'] < 0:
        raise ValueError('Invalid continuation state')
    remaining = max_chunks
    for unused in range(max_polls):
        status = call('get_status', dict(job_id=job_id, wait_seconds=wait_seconds, detail='summary'))
        navigation = {key: status.get(key) for key in ('status', 'phase', 'test_count', 'failure_count', 'failures_omitted')
                      if type(status.get(key)) in (str, int, type(None))}
        navigation = {key: value[:128] if isinstance(value, str) else value for key, value in navigation.items()}
        navigation['failure_examples'] = [
            {key: str(entry[key])[:128] for key in ('category', 'message') if key in entry}
            for entry in status.get('failure_examples', [])[:3] if isinstance(entry, dict)]
        if 'error' in status:
            navigation['error'] = str(status['error'])[:128]
        signature = json.dumps(navigation, sort_keys=True)
        if signature != checkpoint.get('last_change'):
            if on_change is not None:
                on_change(navigation)
            checkpoint['last_change'] = signature
        drained = False
        while remaining:
            offset = checkpoint['offset']
            chunk = call('read_logs', dict(job_id=job_id, offset=offset, log='output'))
            next_offset = chunk.get('next_offset')
            if type(next_offset) is not int or next_offset < offset or not isinstance(chunk.get('text'), str):
                raise ValueError('Invalid log chunk or byte offset')
            if next_offset == offset:
                if chunk['text']:
                    raise ValueError('Log text without offset progress')
                drained = True
                break
            retain_chunk(dict(offset=offset, next_offset=next_offset, text=chunk['text']))
            checkpoint['offset'] = next_offset
            remaining -= 1
        complete = status['status'] != 'running' and drained
        if complete or not remaining:
            break
    return dict(complete=complete, status=navigation, continuation=checkpoint)
