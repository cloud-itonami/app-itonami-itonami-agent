(ns itonami.otehon.physics-host-test
 (:require [clojure.test :refer [deftest is run-tests]] [clojure.edn :as edn]
  [itonami.otehon.physics-host :as h] [itonami.otehon.job-contract :as c]
  [itonami.otehon.job-host :as artifacts] [itonami.otehon.occurrence :as o] [itonami.otehon.recurring-host :as d]))
(defn fixture []
 (let [g {:schema "itonami.job-grant.v1" :owner-decision "AWAI-2795" :execution "deterministic" :authority-source "operator-enrollment"
          :job-kind "physical-simulation" :capabilities (c/capabilities "physical-simulation") :operations [] :model-tools false :mail-send false :git-merge false}
       en {:schema "itonami.occurrence-enrollment.v1" :bot-id "phys-fixture" :job-id "physical" :issue-id "AWAI-2809" :project-id "p" :initiative-id "i"
           :profile-cid "profile" :schedule-cid "schedule" :provider "none" :model "deterministic" :executor "bounded-job-v1"
           :grant g :grant-cid (c/grant-cid g) :capabilities (:capabilities g) :data-class "job-result-metadata-only"
           :nodes ["n"] :schedule {:type "interval" :every-ms 60000} :initial-slot 1000}
       job {:enrollment en :physics-spec-sha (apply str (repeat 64 "a")) :physics-repository "owner/app"}
       request (merge (select-keys en [:bot-id :job-id :issue-id :project-id :initiative-id :profile-cid :schedule-cid :provider :model :executor :grant-cid])
                      {:slot 1000 :attempt-id "a" :run-id "run" :input-sha (o/digest job)})
       db (atom {(:enrollment (o/keys-for (:bot-id en) (:job-id en) 1000)) (o/encode en)})
       client {:authenticated-node "n" :read #(some-> (get @db %) edn/read-string)
               :transact (fn [{:keys [compare put]}] (locking db (if (every? (fn [[k v]] (= v (get @db k))) compare)
                          (do (swap! db merge put) {:succeeded true}) {:succeeded false})))}]
  [client job request]))
(deftest uncertain-publication-recovers-one-simulation-without-another-child
 (let [[client job request] (fixture) old (System/getProperty "user.home")
       root (.getCanonicalPath (java.io.File. (str ".agent-output/physics-host-" (random-uuid))))
       directory (str root "/run") calls (atom 0) writes (atom 0)]
  (d/private-dir! directory)
  (try (System/setProperty "user.home" root)
   (with-redefs [h/spec! (fn [_] "fixture") h/runtime! (fn [_] true)
                 h/simulate! (fn [_ r dir]
                   (swap! calls inc)
                   (let [v {:schema "itonami.otehon.physics-measurement.v1" :request-sha (o/digest r)
                            :spec-sha (:physics-spec-sha job) :status "measured" :probe {:repo "owner/app" :count 1 :expected 1}}]
                    (d/durable! (str dir "/simulation.edn") (pr-str v)) v))
                 artifacts/publish-artifact! (fn [_] (if (= 1 (swap! writes inc)) (throw (ex-info "lost publication ack" {})) {:cid "artifact" :readback true}))]
    (is (= "unknown" (:state (h/execute! client job request directory))))
    (is (= 1 @calls))
    (is (= "completed" (:state (h/recover! client job request directory))))
    (is (= 1 @calls))
    (is (= "already-completed" (:state (h/execute! client job request directory))))
    (is (= 1 @calls)))
   (finally (System/setProperty "user.home" old)))))
(deftest changed-input-and-foreign-result-fail-before-effects
 (let [[client job request] (fixture)]
  (is (thrown? Exception (h/execute! client job (assoc request :input-sha "changed") "unreachable")))
  (is (thrown? Exception (h/publish! job request "unreachable" {:schema "itonami.otehon.physics-measurement.v1" :request-sha "other"})))
  (is (thrown? Exception (h/spec! (assoc-in job [:enrollment :bot-id] "../outside"))))))
(let [r (run-tests)] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))
