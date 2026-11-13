(ns itonami.otehon.job-host-test
 (:require [clojure.test :refer [deftest is run-tests]] [clojure.edn :as edn] [cheshire.core :as json]
           [itonami.otehon.job-host :as host] [itonami.otehon.job-contract :as c]
           [itonami.otehon.occurrence :as o] [itonami.otehon.recurring-host :as d]))
(defn scratch [] (let [p (str ".agent-output/job-host-test-" (random-uuid))] (d/private-dir! p) p))
(defn fixture [kind]
 (let [grant (cond-> {:schema "itonami.job-grant.v1" :owner-decision "AWAI-2795" :execution "deterministic"
              :job-kind kind :authority-source "operator-enrollment" :capabilities (c/capabilities kind)
              :operations (c/operations kind) :model-tools false :mail-send false :git-merge false}
               (= kind "fleet-manifest-drift") (assoc :manifest-catalog-sha (apply str (repeat 64 "a"))))
       e {:schema "itonami.occurrence-enrollment.v1" :bot-id kind :job-id "original-job" :issue-id "AWAI-2795"
          :project-id "p" :initiative-id "i" :profile-cid "profile" :schedule-cid "schedule"
          :provider "none" :model "deterministic" :executor "bounded-job-v1" :data-class "job-result-metadata-only"
          :grant grant :grant-cid (c/grant-cid grant) :capabilities (:capabilities grant)
          :nodes ["n"] :schedule {:type "interval" :every-ms 60000} :initial-slot 1000}
       job {:enrollment e :workspace "must-be-operator-selected"}
       request (merge (select-keys e [:bot-id :job-id :issue-id :project-id :initiative-id :profile-cid :schedule-cid :provider :model :executor :grant-cid])
                      {:slot 1000 :attempt-id "a" :run-id "run" :input-sha (o/digest job)})
       db (atom {(:enrollment (o/keys-for kind "original-job" 1000)) (o/encode e)})
       client {:authenticated-node "n" :read #(some-> (get @db %) edn/read-string)
               :transact (fn [{:keys [compare put]}]
                 (locking db (if (every? (fn [[k v]] (= v (get @db k))) compare)
                   (do (swap! db merge put) {:succeeded true}) {:succeeded false})))}]
  {:job job :request request :client client :grant grant}))
(defn observed [counter]
 (fn [_ op] (swap! counter inc)
   {:operation op :http-status (if (clojure.string/ends-with? (:path op) "/metrics") 200 502)
    :json (when (clojure.string/ends-with? (:path op) "/metrics") {:tenants {:registryEntries 9}})}))
(deftest secret-results-bind-authenticated-encryption-to-original-request
 (let [root (scratch) key (host/load-storage-key! (str root "/key")) request {:run "one"}
       envelope (host/seal key request "private test payload")]
  (is (= "private test payload" (host/unseal key request envelope)))
  (is (thrown? Exception (host/unseal key {:run "other"} envelope)))
  (is (thrown? Exception (host/unseal (byte-array 32) request envelope)))
  (is (not (clojure.string/includes? (pr-str envelope) "private test payload")))))
(deftest unknown-publication-recovers-without-another-connector-call
 (let [{:keys [job request client]} (fixture "public-bots-registrar") root (scratch) reads (atom 0) writes (atom 0)]
  (with-redefs [host/get-public! (observed reads)
                host/publish-artifact! (fn [_] (if (= 1 (swap! writes inc)) (throw (ex-info "lost ack" {})) {:cid "artifact" :readback true}))]
   (is (= "unknown" (:state (host/execute! client job request root))))
   (is (= 2 @reads))
   (is (= "completed" (:state (host/recover! client job request root))))
   (is (= 2 @reads))
   (is (= "already-completed" (:state (host/execute! client job request (str root "-must-not-be-created")))))
   (is (= 2 @reads))
   (is (not (.exists (java.io.File. (str root "-must-not-be-created"))))))))
(deftest arb-ledger-is-real-and-pending-proposals-deduplicate
 (let [{:keys [job request grant]} (fixture "awai-arb") root (scratch) workspace (scratch) reads (atom 0)]
  (with-redefs [host/get-public! (observed reads) host/arb-workspace! (fn [_] workspace)]
   (let [a (host/record-public-job! (assoc job :grant grant) request root)
         b (host/record-public-job! (assoc job :grant grant) (assoc request :slot 61000) root)]
    (is (= 1 (count (:proposals a)))) (is (empty? (:proposals b))) (is (:already-pending b))
    (is (= 2 (count (clojure.string/split-lines (slurp (str workspace "/arb_ledger.jsonl"))))))
    (spit (str workspace "/arb_ledger.jsonl") "unrelated writer\n")
    (is (thrown? Exception (host/record-public-job! (assoc job :grant grant) (assoc request :slot 121000) root)))))))
(deftest unsafe-target-and-input-refuse-before-dispatch
 (let [{:keys [job request client grant]} (fixture "public-bots-registrar")]
  (is (thrown? Exception (host/get-public! grant (assoc (first (:operations grant)) :method "POST"))))
  (is (thrown? Exception (host/execute! client job (assoc request :input-sha "changed") (scratch))))
  (is (thrown? Exception (host/arb-workspace! {:workspace "/tmp/not-owned"})))))
(deftest owner-state-link-is-supported-without-following-workspace-links
 (let [root (.getCanonicalPath (java.io.File. (scratch))) real (str root "/state")
       home (str root "/home") previous (System/getProperty "user.home")]
  (d/private-dir! real) (d/private-dir! home)
  (java.nio.file.Files/createSymbolicLink (java.nio.file.Paths/get (str home "/.itonami") (make-array String 0))
    (java.nio.file.Paths/get real (make-array String 0)) (make-array java.nio.file.attribute.FileAttribute 0))
  (try
   (System/setProperty "user.home" home)
   (let [workspace (str home "/.itonami/profiles/awai-arb/workspace")]
    (is (= workspace (host/arb-workspace! {:workspace workspace})))
    (java.nio.file.Files/delete (java.nio.file.Paths/get workspace (make-array String 0)))
    (java.nio.file.Files/createSymbolicLink (java.nio.file.Paths/get workspace (make-array String 0))
      (java.nio.file.Paths/get real (make-array String 0)) (make-array java.nio.file.attribute.FileAttribute 0))
    (is (thrown? Exception (host/arb-workspace! {:workspace workspace}))))
   (finally (System/setProperty "user.home" previous)))))
(deftest manifest-observation-publication-loss-never-repeats-the-read
 (let [{:keys [job request client grant]} (fixture "fleet-manifest-drift")
       reads (atom 0) publishes (atom 0) root (scratch)]
  (with-redefs [host/get-public! (fn [_ op] (swap! reads inc) {:operation op :http-status 200 :json {:schema "awai.fleet-health-summary.v1" :fresh true :catalogSha256 (:manifest-catalog-sha grant)}})
                host/publish-artifact! (fn [_] (if (= 1 (swap! publishes inc)) (throw (ex-info "lost ack" {})) {:cid "public" :readback true}))]
   (is (= "unknown" (:state (host/execute! client job request root))))
   (is (= 1 @reads))
   (is (= "completed" (:state (host/recover! client job request root))))
   (is (= 1 @reads)))))
(let [r (run-tests)] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))
