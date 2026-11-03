# Test-only source-unity generator. Does not invoke or install a runtime.
from pathlib import Path
import sys
source = Path(sys.argv[1]).read_text()
out = Path(sys.argv[2])
s = '(ns self-org-gate (:export [main]))\n' + source[source.index(';; ---------------------------------------------------------------------------'):] + '\n(defn main [] :i64 (if (and (test-wake) (test-next) (test-backoff) (test-staffing)) 0 1))\n'
(out / 'fixture.kotoba').write_text(s)
for name, old, new in [('wake','(< in-flight 0)','false'),('backoff','(> ms (quot cap 2)) cap','false cap'),('staff','(< takeable 0) (< workers 0)','false (< workers 0)')]:
    assert s.count(old) == 1
    (out / (name + '.kotoba')).write_text(s.replace(old, new))
