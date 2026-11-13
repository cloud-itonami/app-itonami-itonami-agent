(ns itonami.otehon.peer-talk-test
 (:require [clojure.test :refer [deftest is run-tests]] [itonami.otehon.peer-talk :as p] [itonami.otehon.io :as io]))
(def grant {:schema "itonami.peer-cycle-grant.v1" :cycle-id "00000000-0000-4000-8000-000000000001" :session-id "00000000-0000-4000-8000-000000000001"
 :cycle-source "operator-one-cycle" :issue-id "AWAI-2861" :provider "meta" :model "muse-spark-1.3" :max-turns 4 :tools [] :goal "Synthetic fleet review"
 :snapshot "Synthetic counts; no actual runs" :snapshot-sha (io/sha256-utf8 "Synthetic counts; no actual runs")
 :participants [{:bot-id "profile:a" :profile-sha (apply str (repeat 64 "a"))} {:bot-id "profile:b" :profile-sha (apply str (repeat 64 "b"))}]})
(deftest bounded-alternating-turns-and-goal-binding
 (is (p/valid-grant? grant))
 (loop [turns []]
  (let [plan (p/turn-plan grant turns)]
   (if (< (count turns) 4)
    (do (is (= "ready" (:state plan))) (is (= (:bot-id (nth (:participants grant) (mod (count turns) 2))) (:speaker plan)))
        (is (= (:input-sha plan) (io/sha256-utf8 (:prompt plan))))
        (recur (conj turns {:turn (count turns) :speaker (:speaker plan) :state "completed" :text "Synthetic reply"})))
    (is (= "completed" (:state plan)))))))
(deftest unknown-does-not-replay-or-advance
 (is (= "unknown" (:state (p/turn-plan grant [{:turn 0 :speaker "profile:a" :state "unknown" :text ""}]))))
 (is (= "unknown" (:state (p/turn-plan grant [{:turn 1 :speaker "profile:a" :state "completed" :text "wrong turn"}])))))
(deftest grant-cannot-add-tools-turns-or-change-source-unnoticed
 (is (not (p/valid-grant? (assoc grant :max-turns 5))))
 (is (not (p/valid-grant? (assoc grant :tools ["shell"]))))
 (is (not (p/valid-grant? (assoc grant :snapshot "changed"))))
 (is (not= (p/grant-cid grant) (p/grant-cid (assoc grant :goal "different")))))
(let [r (run-tests 'itonami.otehon.peer-talk-test)] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))
