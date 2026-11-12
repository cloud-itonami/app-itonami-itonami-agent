(ns itonami.otehon.recurring-test
 (:require [clojure.test :refer [deftest is run-tests]]
           [itonami.otehon.occurrence :as o] [itonami.otehon.recurring :as r]))
(def e {:schema "itonami.occurrence-enrollment.v1" :bot-id "b" :job-id "j" :issue-id "AWAI-2795"
 :project-id "p" :initiative-id "i" :profile-cid "pin" :schedule-cid "schedule" :provider "meta"
 :model "muse-spark-1.3" :capabilities ["inference_muse"] :nodes ["n"] :data-class "public-readiness-only"
 :schedule {:type "interval" :every-ms 60000} :initial-slot 1000 :missed-slot-policy "skip-completed-only"})
(defn req [slot] (merge (select-keys e [:bot-id :job-id :issue-id :project-id :initiative-id :profile-cid :schedule-cid :provider :model])
 {:slot slot :attempt-id "a" :input-sha (apply str (repeat 64 "a")) :run-id "r"}))
(deftest time-and-sleep-do-not-cause-catchup-bursts
 (is (= "waiting" (:state (r/plan e nil 999))))
 (is (= 1000 (:slot (r/plan e nil 1000))))
 (is (= "waiting" (:state (r/plan e {:state "completed" :slot 1000} 60000))))
 (is (= {:state "due" :slot 181000 :skipped-unstarted 2} (r/plan e {:state "completed" :slot 1000} 200000)))
 (is (nil? (:error (o/admit-plan e {:state "completed" :slot 1000} (req 181000) "n" 200000))))
 (is (= "slot-not-due-or-out-of-order" (:error (o/admit-plan (dissoc e :missed-slot-policy) {:state "completed" :slot 1000} (req 181000) "n" 200000))))
 (is (= "slot-not-due-or-out-of-order" (:error (o/admit-plan e nil (req 181001) "n" 200000)))))
(deftest unknown-never-skips-or-transfers
 (is (= "unknown" (:state (r/plan e {:state "admitted" :slot 1000} 99999999))))
 (is (= "previous-outcome-unknown-no-retry" (:error (o/admit-plan e {:state "admitted" :slot 1000} (req 181000) "n" 200000))))
 (is (= "node-not-enrolled" (:error (o/admit-plan e nil (req 1000) "other" 1000))))
 (is (= "enrollment-invalid" (:error (o/admit-plan (assoc e :capabilities ["shell"]) nil (req 1000) "n" 1000))))
 (is (= "held" (:state (r/plan (assoc e :data-class "public-test-only") nil 1000)))))
(let [x (run-tests)] (when (pos? (+ (:fail x) (:error x))) (System/exit 1)))
