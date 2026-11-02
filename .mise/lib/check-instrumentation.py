"""Check legacy adb output: adb exit zero alone does not prove tests ran."""
from pathlib import Path
import re
import sys


def main():
    output = Path(sys.argv[1]).read_text(errors='replace')
    success = re.search(r'^OK \(([1-9][0-9]*) tests?\)\s*$', output, re.MULTILINE)
    failure = re.search(
        r'^FAILURES|^Tests run:.*Failures: [1-9]|ERROR|INSTRUMENTATION_FAILED|'
        r'INSTRUMENTATION_ABORTED|shortMsg=', output, re.MULTILINE,
    )
    if not success or failure:
        print('No successful instrumentation result with a nonzero test count, or failure markers present.', file=sys.stderr)
        return 1
    print('Verified instrumentation result: ' + success.group(1) + ' tests.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
