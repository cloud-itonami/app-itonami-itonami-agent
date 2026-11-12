(ns itonami.otehon.job-contract-test
 (:require [clojure.test :refer [deftest is run-tests]] [itonami.otehon.job-contract :as j]
           [itonami.otehon.occurrence :as o]))
(def grant {:schema "itonami.job-grant.v1" :owner-decision "AWAI-2795" :execution "deterministic"
 :job-kind "public-bots-registrar" :authority-source "operator-enrollment"
 :capabilities (j/capabilities "public-bots-registrar") :operations (j/operations "public-bots-registrar")
 :model-tools false :mail-send false :git-merge false})
(deftest exact-operations-only
 (is (j/valid-grant? grant))
 (is (j/permitted? grant (first (:operations grant))))
 (doseq [op [(assoc (first (:operations grant)) :method "POST")
             (assoc (first (:operations grant)) :path "/api/marketplace/public-bots/register")
             (assoc (first (:operations grant)) :origin "https://evil.test")
             (assoc (first (:operations grant)) :path "/api/marketplace/public-bots/metrics?token=x")]]
   (is (not (j/permitted? grant op))))
 (is (not (j/valid-grant? (assoc grant :mail-send true))))
 (is (not (j/valid-grant? (update grant :capabilities conj "terminal")))))
(deftest unavailable-catalog-and-missing-data-are-not-registration
 (let [obs [{:http-status 200 :json {:tenants {:registryEntries 9}}} {:http-status 502}]]
  (is (= 9 (:registry-entries (j/public-registry-result obs))))
  (is (= "unverified" (:registration-state (j/public-registry-result obs))))
  (is (false? (:mission-completed (j/public-registry-result obs))))
  (is (= 1 (count (:proposals (j/arb-proposals obs 1000))))))
 (is (empty? (:proposals (j/arb-proposals [{:http-status 503} {:http-status 502}] 1000))))
 (is (:mission-completed (j/public-registry-result [{:http-status 200} {:http-status 200 :json {:bots [{:id "awai-arb"}]}}]))))
(deftest bounded-job-enrollment-is-distinct-from-muse-and-pins-grant
 (let [e {:schema "itonami.occurrence-enrollment.v1" :bot-id "pbr" :job-id "job" :issue-id "AWAI-2795"
          :project-id "p" :initiative-id "i" :profile-cid "bafp" :schedule-cid "bafs"
          :provider "none" :model "deterministic" :capabilities (:capabilities grant) :grant grant
          :grant-cid (j/grant-cid grant) :executor "bounded-job-v1" :data-class "job-result-metadata-only"
          :nodes ["n"] :schedule {:type "interval" :every-ms 60000} :initial-slot 1000}]
  (is (o/enrollment-valid? e))
  (is (not (o/enrollment-valid? (dissoc e :executor))))
  (is (not (o/enrollment-valid? (assoc e :provider "meta"))))
  (is (not (o/enrollment-valid? (assoc-in e [:grant :operations 0 :method] "POST"))))
  (is (not (o/enrollment-valid? (assoc e :grant-cid "changed"))))
  (let [request (merge (select-keys e [:bot-id :job-id :issue-id :project-id :initiative-id :profile-cid :schedule-cid :provider :model :executor :grant-cid])
                       {:slot 1000 :attempt-id "a" :input-sha (apply str (repeat 64 "a")) :run-id "run"})
        plan (o/admit-plan e nil request "n" 1000)
        result {:state "completed" :provider "none" :model "deterministic" :text "public metadata" :binding request}]
    (is (nil? (:error (o/completion-plan e (:claim plan) (:position plan) result))))
    (is (= "outcome-unknown-no-retry" (:error (o/completion-plan e (:claim plan) (:position plan)
                                             (assoc-in result [:binding :grant-cid] "stale"))))))))
(let [r (run-tests)] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))
