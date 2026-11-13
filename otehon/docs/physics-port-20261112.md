# Otehon physics port — 2026-11-12, AWAI-2809

The first deployed wave supported transport, thermal, manipulator, tank-drain and
pipe-flow. Its immutable a9a1b2f capsule rejects material/J2. This is an Otehon simulation port, separate
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

## Material/J2 follow-up — AWAI-2809 / AWAI-2820

The new source additionally ports robotics material at the same immutable
revision and six Kudaki modules at1948314013c4cdd93415de4f49fa3a0661086630.
`physics-material-port-provenance.json` pins every original/target digest.
Changes are namespaces only; no solver coefficients, steps, material inputs or
source App limits are changed. The old runtime capsule remains unchanged.
The bounded runner limits elements, frames, wave transits and weighted total
work before any simulation; no legacy runtime or unbounded numerical fallback.

Dated one-off developer oracle: Babashka1.12.218; `git show REV:path` extracted
the unchanged seven modules as `.cljc` under
`.agent-output/material-reference-20261112/src`. No new downloads. Command:

```sh
bb -cp .agent-output/material-reference-20261112/src -e '(require (quote [kotoba.robotics.process.material :as m])) (let [r (m/tensile-test {:max-force-n 10000.0})] (spit ".agent-output/material-reference-20261112/elastic-result.edn" (pr-str r)) (prn (dissoc r :curve)))'
```

Reference elastic result:8000 steps, stiffness199993019.82843584 N/m vs theoretical
200000000 N/m, no yield, relative energy residual0.00012498610888663734.
The new port's complete result, including curve frames, equals this reference.
Five full corroded-rebar sweep results from COFOG04.5 physics.edn
SHA256b2cd410a00ca80efd9175fe6038bc9cdf34f49820444f34f9a38297f85c4162b
also equal the unchanged reference; the full ported App probe measured11/11
in5259ms on this developer host. These are bounded equivalence measurements,
not independent physical/material qualification or Native test results.

`bb -cp otehon/src otehon/test/physics_material_test.clj`:3 tests/13 assertions
cover elastic stiffness, below-yield behavior, plastic offset yield/energy and
invalid work bounds. Existing physics4/17 and host2/9 remain passing. Data-only
planning over the same643 source profiles increases eligibility519→582, with63
newly qualified specs;61 still fail source/solver/work gates. Planning is not
enrollment or activation. New deployment and VM persistence/race/restart proofs
are separately tracked in AWAI-2820 and awai-os PR95.
