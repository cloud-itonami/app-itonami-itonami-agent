(ns itonami.otehon.physics-test
 (:require [clojure.test :refer [deftest is run-tests]] [itonami.otehon.physics.runner :as r]
           [itonami.otehon.physics.process.transport :as transport] [itonami.otehon.physics.process.thermal :as thermal]
           [itonami.otehon.physics.process.fluid :as fluid] [itonami.otehon.physics.process.manipulator :as arm]))
(def spec {:physics/schema "itonami.physical-ai.spec.v1" :repo "owner/test"
 :cases [{:id :move :kind :transport :procedure "synthetic test" :params {:distance-m 20 :crr 0 :v-max-mps 1 :a-max-mps2 1 :brake-decel-mps2 1}
          :sweep {:param :payload-kg :values [0 5]} :quantity :cycle-time-s :limit {:max 100 :basis "synthetic fixture"}}]})
(deftest bounded-physics-measures-without-claiming-native-or-growth
 (let [v (r/simulate spec "owner/test")]
  (is (= "measured" (:status v))) (is (= 2 (get-in v [:probe :count])))
  (is (= "unmeasured" (:native-tests v))) (is (= "held" (:growth v)))
  (is (< (Math/abs (- 21.0 (get-in v [:probe :runs 0 :quantities :cycle-time-s]))) 0.1))))
(deftest unsupported-and-unbounded-input-fail-before-running
 (is (thrown? Exception (r/validate! spec "other/repo")))
 (is (thrown? Exception (r/validate! (assoc-in spec [:cases 0 :kind] :arbitrary-eval) "owner/test")))
 (is (thrown? Exception (r/validate! (assoc-in spec [:cases 0 :params :dt-s] 0) "owner/test")))
 (is (thrown? Exception (r/validate! (assoc-in spec [:cases 0 :sweep :values] (vec (repeat 129 1))) "owner/test")))
 (is (thrown? Exception (r/validate! (assoc-in spec [:cases 0 :params :mass-kg] ##NaN) "owner/test"))))
(deftest uniform-temperature-remains-uniform
 (let [v (thermal/simulate {:t0-c 20 :t-hot-c 20 :t-back-ambient-c 20 :duration-s 10})]
  (is (= 20.0 (:final-back-c v))) (is (= 20.0 (:mean-c v)))))
(deftest fluid-and-arm-conservation-controls
 (let [pipe (fluid/pipe-flow {:q-m3s 1.0e-6})
       params (merge fluid/tank-defaults {:h0-m 0.2 :h-target-m 0.1 :dt-s 0.01})
       tank (fluid/tank-drain params) a (arm/pick-place {})]
  (is (:laminar? pipe))
  (is (< (Math/abs (- 64.0 (* (:friction-factor pipe) (:reynolds pipe)))) 1e-8))
  (is (< (Math/abs (- (:time-to-target-s tank) (fluid/drain-time-closed-form params))) 0.02))
  (is (:reachable? a))
  (is (< (Math/abs (- (:joint-work-j a) (:delta-potential-j a))) 0.001))))
(let [r (run-tests)] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))
