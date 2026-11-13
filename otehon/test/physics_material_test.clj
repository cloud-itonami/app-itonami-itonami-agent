(require '[clojure.test :refer [deftest is run-tests]]
 '[clojure.edn :as edn] '[itonami.otehon.physics.process.material :as material]
 '[itonami.otehon.physics.runner :as runner])
(deftest elastic-stiffness-and-no-yield-below-nominal-force
 (let [r (material/tensile-test {:max-force-n 10000.0})]
  (is (< (Math/abs (- 1.0 (/ (:elastic-stiffness-n-per-m r) (:theoretical-stiffness-n-per-m r)))) 0.02))
  (is (false? (:yielded? r))) (is (= 8000 (:steps r)))
  (is (< (Math/abs (:energy-residual-rel r)) 0.01))))
(deftest plastic-offset-yield-and-energy
 (let [r (material/tensile-test {:max-force-n 40000.0}) nominal (* (+ 250e6 (* 1e9 0.002)) 1e-4)]
  (is (:yielded? r)) (is (pos? (:plastic-energy-j r)))
  (is (< (Math/abs (- 1.0 (/ (:yield-force-n r) nominal))) 0.05))))
(deftest excessive-material-work-refused-before-solver
 (doseq [p [{:elements 5} {:elements 0} {:ramp-transits 100000} {:frames 0} {:hardening-pa -1}]]
  (is (thrown? Exception (runner/cost :material p))))
 (is (pos? (runner/cost :material {}))))
(let [r (run-tests)] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))
