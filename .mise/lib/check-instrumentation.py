"""Check legacy adb output: adb exit zero alone does not prove tests ran."""
from pathlib import Path
import sys


def main():
    output = Path(sys.argv[1]).read_text(errors='replace')
    sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'scripts/host-mcp'))
    from instrumentation import instrumentation_result
    try:
        count = instrumentation_result(output)
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1
    print(f'Verified instrumentation result: {count} tests.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
