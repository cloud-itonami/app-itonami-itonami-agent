(ns itonami.otehon.occurrence-test
 (:require [clojure.test :refer [deftest is run-tests]] [clojure.edn :as edn]
           [itonami.otehon.occurrence :as o]))
(def enrollment {:schema "itonami.occurrence-enrollment.v1" :bot-id "bot" :job-id "job"
 :issue-id "AWAI-2795" :project-id "p" :initiative-id "i" :profile-cid "bafprofile" :schedule-cid "bafschedule"
 :provider "meta" :model "muse-spark-1.3" :capabilities ["inference_muse"] :nodes ["node-a" "node-b"]
 :data-class "public-test-only" :schedule {:type "interval" :every-ms 60000} :initial-slot 1000})
(def req (merge (select-keys enrollment [:bot-id :job-id :issue-id :project-id :initiative-id :profile-cid :schedule-cid :provider :model])
 {:slot 1000 :attempt-id "attempt-a" :input-sha (apply str (repeat 64 "a")) :run-id "run-a"}))
(defn completed [request]
 {:state "completed" :provider "meta" :model "muse-spark-1.3" :text "PUBLIC_OK" :muse-run-id "actual-or-fixture-id"
 :binding (select-keys request [:attempt-id :input-sha :run-id :slot :bot-id :job-id :profile-cid :schedule-cid :issue-id :project-id :initiative-id])})
(defn fixture []
 (let [db (atom {(get (o/keys-for "bot" "job" 1000) :enrollment) (o/encode enrollment)})]
  {:db db :authenticated-node "node-a"
   :read (fn [k] (when-let [v (get @db k)] (edn/read-string v)))
   :transact (fn [{:keys [compare put]}]
               (locking db (if (every? (fn [[k v]] (= v (get @db k))) compare)
                             (do (swap! db merge put) {:succeeded true}) {:succeeded false})))}))
(deftest concurrent-workers-admit-one-call
 (let [f (fixture) calls (atom 0) entered (promise) release (promise)
       c (assoc f :invoke-once #(do (swap! calls inc) (deliver entered true) @release (completed req)))
       a (future (o/dispatch! c req "node-a" 1000))]
   @entered
   (let [b (o/dispatch! c (assoc req :attempt-id "attempt-b") "node-b" 1000)]
     (is (= "unknown" (:state b))) (is (false? (:invoked b))))
   (deliver release true)
   (is (= "completed" (:state @a))) (is (= 1 @calls))
   (is (= "already-completed" (:state (o/dispatch! c req "node-a" 1000))))
   (is (= 1 @calls))))
(deftest crash-after-model-before-publish-recovers-without-model
 (let [f (fixture) calls (atom 0) writes (atom 0) cached (atom nil)
       c (assoc f :transact #(if (= 2 (swap! writes inc)) (throw (ex-info "network loss" {})) ((:transact f) %))
                  :invoke-once #(do (swap! calls inc) (reset! cached (completed req))))
       first-run (o/dispatch! c req "node-a" 1000)
       claim ((:read f) (:occurrence (o/keys-for "bot" "job" 1000)))]
  (is (= "unknown" (:state first-run)))
  (is (= "unknown" (:state (o/dispatch! c req "node-a" 1000)))) (is (= 1 @calls))
  (is (= "completed" (:state (o/reconcile! f req claim @cached))))
  (is (= "already-completed" (:state (o/dispatch! c req "node-a" 1000)))) (is (= 1 @calls))))
(deftest publication-response-loss-is-reconciled-by-read
 (let [f (fixture) n (atom 0) calls (atom 0)
       c (assoc f :transact #(let [r ((:transact f) %)] (if (= 2 (swap! n inc)) (throw (ex-info "lost ack" {})) r))
                  :invoke-once #(do (swap! calls inc) (completed req)))]
  (is (= "unknown" (:state (o/dispatch! c req "node-a" 1000))))
  (is (= "already-completed" (:state (o/dispatch! c req "node-a" 1000)))) (is (= 1 @calls))))
(deftest interrupted-admission-stays-unknown-and-next-slot-is-frozen
 (let [f (fixture) c (assoc f :invoke-once #(throw (ex-info "crash" {})))]
  (is (= "unknown" (:state (o/dispatch! c req "node-a" 1000))))
  (is (= "unknown" (:state (o/dispatch! c req "node-a" 1000))))
  (is (= "previous-outcome-unknown-no-retry" (:reason (o/dispatch! c (assoc req :slot 61000) "node-a" 61000))))))
(deftest identity-input-source-and-fences-are-explicit
 (let [f (fixture) plan (o/admit-plan enrollment nil req "node-a" 1000)]
  (is (= "node-not-enrolled" (:error (o/admit-plan enrollment nil req "stranger" 1000))))
  (is (= "binding-mismatch" (:error (o/admit-plan enrollment nil (assoc req :profile-cid "changed") "node-a" 1000))))
  (is (= "slot-not-due-or-out-of-order" (:error (o/admit-plan enrollment nil req "node-a" 999))))
  ((:transact f) (:transaction plan))
  (is (= "occurrence-input-conflict" (:reason (o/dispatch! f (assoc req :input-sha (apply str (repeat 64 "b"))) "node-a" 1000))))
  (is (= "outcome-unknown-no-retry" (:error (o/completion-plan enrollment (:claim plan) (:position plan)
                          (assoc-in (completed req) [:binding :attempt-id] "stale")))))
  (is (= "outcome-unknown-no-retry" (:error (o/completion-plan enrollment (:claim plan) (:position plan)
                          (assoc-in (completed req) [:binding :slot] 61000)))))
  (is (= "fence-mismatch" (:error (o/completion-plan (assoc enrollment :model "new") (:claim plan) (:position plan) (completed req)))))
  (is (= "unknown" (:state (o/reconcile! (assoc f :authenticated-node "node-b") req (:claim plan) (completed req)))))
  (is (= (o/keys-for "bot" "job" 1000) (o/keys-for "bot" "job" (:slot req))))))
(let [r (run-tests)] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))
