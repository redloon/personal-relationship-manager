"""Parse AndroidJUnitRunner output, failing closed on incomplete/mixed results."""
import re
import time


def instrumentation_result(output):
    success = re.search(r'^OK \(([1-9][0-9]*) tests?\)\s*$', output, re.MULTILINE)
    codes = re.findall(r'^INSTRUMENTATION_CODE:\s*(-?\d+)\s*$', output, re.MULTILINE)
    failure = re.search(
        r'\bFAILURES\b|^Tests run:.*(?:Failures|Errors): [1-9]|\bERROR\b|'
        r'INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=|'
        r'Process crashed|INSTRUMENTATION_STATUS_CODE:\s*-(?:1|2)\b',
        output, re.MULTILINE,
    )
    if not success or codes != ['-1'] or failure:
        raise ValueError('Tests failed or successful completion with a nonzero test count missing')
    return int(success.group(1))


class InstrumentationResults:
    def __init__(self):
        self.tests = []
        self.pending = ''
        self.fields = {}
        self.started = {}

    def feed(self, text, now=None):
        now = time.monotonic() if now is None else now
        self.pending += text
        while '\n' in self.pending:
            line, self.pending = self.pending.split('\n', 1)
            field = re.match(r'INSTRUMENTATION_STATUS: (class|test)=(.*)', line)
            if field:
                self.fields[field[1]] = field[2].strip()
            code = re.match(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)', line)
            if code:
                identity = (self.fields.get('class'), self.fields.get('test'))
                value = int(code[1])
                if all(identity):
                    if value == 1:
                        self.started[identity] = now
                    elif value in (0, -1, -2, -3, -4):
                        start = self.started.pop(identity, None)
                        self.tests.append(dict(class_name=identity[0], test_name=identity[1],
                                               outcome={0: 'passed', -1: 'error', -2: 'failed', -3: 'ignored', -4: 'assumption_failed'}[value],
                                               duration_seconds=round(now-start, 3) if start is not None else None))
                self.fields = {}
