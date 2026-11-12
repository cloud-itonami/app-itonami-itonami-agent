# Otehon physics port — 2026-11-12, AWAI-2809

The five supported kinds are transport, thermal, manipulator, tank-drain and
pipe-flow. Material/J2 is rejected. This is an Otehon simulation port, separate
from Native, source app tests, code growth, merge and physical actuation.
The provenance JSON records exact source/target digests and robotics revision.

Developer oracle (one-off only): Babashka1.12.218, unchanged transport/thermal
source extracted from robotics commit a1af59c971e37d6c62a313ca52b662c92ba34c25
with `git show COMMIT:src/kotoba/robotics/process/NAME.cljk`, written as `.cljc`
in the task's `.agent-output/robotics-reference-20261112/`. No downloads beyond
the four public pinned profile files and the public pinned `physics.edn`.
Input: cloud-itonami/cloud-itonami-cofog-03.2 at profile commit
e1156a6d349cbd6dc8c403afd4fafe4565702cd1; physics.edn2149bytes,
SHA25676ad5be17f78c678bd8901a58bb0d0934a37b2e9dfa4f19e115301ffb69986ab.

Executed comparison command (paths relative to the task worktree):

```sh
bb -cp otehon/src -e '(require (quote [clojure.edn :as e]) (quote [itonami.otehon.physics.process.transport :as t]) (quote [itonami.otehon.physics.process.thermal :as h])) (load-file ".agent-output/robotics-reference-20261112/transport.cljc") (load-file ".agent-output/robotics-reference-20261112/thermal.cljc") (let [s (e/read-string (slurp ".agent-output/physics-fire-risk-spec-20261112.edn")) rows (vec (for [c (:cases s) x (get-in c [:sweep :values]) :let [p (assoc (:params c) (get-in c [:sweep :param]) x) source (case (:kind c) :transport kotoba.robotics.process.transport/move :thermal kotoba.robotics.process.thermal/simulate) port (case (:kind c) :transport t/move :thermal h/simulate)]] {:case (:id c) :value x :equal (= (source p) (port p))})) r {:date "2026-11-12" :tool "babashka v1.12.218" :scope "one-off developer source oracle; not Native qualification" :source "robotics@a1af59c971e37d6c62a313ca52b662c92ba34c25" :cases rows}] (spit ".agent-output/physics-oracle-comparison-20261112.edn" (pr-str r)) (prn {:compared (count rows) :equal (count (filter :equal rows))}))'
```

Observed:12/12 full solver results equal, six rolling-resistance and six wall
thickness inputs. This is port equivalence for this bounded corpus, not an
independent physics validation. The new port's process probe also measured12/12
declared runs and retained the source's estimate-labelled limits. Its boundary
outputs are not independently qualified by the12 solver comparisons.

Otehon tests: `bb -cp otehon/src otehon/test/physics_test.clj`, four tests,
17 assertions, no failures/errors. Controls include invalid repo/spec/kind,
nonfinite inputs and oversized sweeps before evaluation, uniform-temperature
invariance, unconstrained travel time, laminar friction, tank-drain time and arm
work/potential-energy balance. These port tests may run in the Otehon oracle job;
the old source oracle above is not a CI/runtime dependency or fallback.
