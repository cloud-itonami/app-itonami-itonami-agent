(ns itonami.otehon.original-host-test
 (:require [clojure.test :refer [deftest is run-tests]]
           [itonami.otehon.original-host :as h] [itonami.otehon.recurring-host :as d]
           [itonami.otehon.job-host :as jobs] [itonami.otehon.job-contract :as c]
           [itonami.otehon.occurrence :as o]))
(defn fixture []
 (let [g {:schema "itonami.job-grant.v1" :owner-decision "AWAI-2795" :execution "deterministic"
          :job-kind "public-bots-registrar" :authority-source "operator-enrollment"
          :capabilities (c/capabilities "public-bots-registrar") :operations (c/operations "public-bots-registrar")
          :model-tools false :mail-send false :git-merge false}
       en {:schema "itonami.occurrence-enrollment.v1" :bot-id "bot" :job-id "job" :issue-id "AWAI-2795"
           :project-id "p" :initiative-id "i" :profile-cid "p" :schedule-cid "s" :provider "none" :model "deterministic"
           :executor "bounded-job-v1" :grant g :grant-cid (c/grant-cid g) :capabilities (:capabilities g)
           :data-class "job-result-metadata-only" :nodes ["n"] :schedule {:type "interval" :every-ms 60000}
           :initial-slot 1000 :missed-slot-policy "skip-completed-only"}
       root (str ".agent-output/original-host-test-" (random-uuid))
       _ (d/private-dir! root)
       cfg {:state-root root} job {:enrollment en :catalog-bot-id "profile:bot" :binding {:revision (apply str (repeat 40 "a"))}}
       client {:read #(when (clojure.string/includes? % "/enrollment/") en)}]
  [cfg client job]))
(deftest issue-or-forecast-failure-cannot-start-business-executor
 (let [[cfg client job] (fixture) calls (atom 0)]
  (with-redefs [d/issue-context! (fn [& _] true) d/publish-comment! (fn [& _] false)
                jobs/execute! (fn [& _] (swap! calls inc))]
    (is (thrown? Exception (h/tick-job! cfg client job 1000)))
    (is (zero? @calls)))
  (with-redefs [d/issue-context! (fn [& _] (throw (ex-info "closed issue" {})))
                jobs/execute! (fn [& _] (swap! calls inc))]
    (is (thrown? Exception (h/tick-job! cfg client job 1000)))
    (is (zero? @calls)))))
(deftest deterministic-completion-requires-grant-binding-and-is-not-muse
 (let [[_ _ job] (fixture) en (:enrollment job)
       req (merge (select-keys en [:bot-id :job-id :issue-id :project-id :initiative-id :profile-cid :schedule-cid :provider :model :executor :grant-cid])
                  {:slot 1000 :attempt-id "a" :run-id "run" :input-sha (o/digest job)})
       p (o/admit-plan en nil req "n" 1000)
       result {:state "completed" :provider "none" :model "deterministic" :binding req :text "measured"}]
  (is (nil? (:error (o/completion-plan en (:claim p) (:position p) result))))
  (is (:error (o/completion-plan en (:claim p) (:position p) (assoc result :provider "meta"))))
  (is (:error (o/completion-plan en (:claim p) (:position p) (assoc-in result [:binding :grant-cid] "stale"))))))
(let [r (run-tests)] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))
