(ns itonami.app.autonomy-test
  (:require [clojure.test :refer [deftest is testing]]
            [itonami.app.autonomy :as a]))

(def h a/hour)
(def now (* 1000 h))

(def catalog
  {"schema" "awai.bot-fleet.v1"
   "organization"
   {"circles" [{"id" "fleet" "name" "Fleet" "parent-id" nil}
               {"id" "repo:x" "name" "x" "parent-id" "fleet"}
               {"id" "repo:y" "name" "y" "parent-id" "fleet"}]
    "bots" [{"id" "profile:runner" "name" "runner" "circle-ids" ["repo:x"]
             "schedule" {"state" "verified" "jobs" [{"enabled" false}]} "runtime" {"state" "not-started"}}
            {"id" "profile:old" "name" "old" "circle-ids" ["repo:x"]
             "schedule" {"state" "verified" "jobs" [{"enabled" false}]}}
            {"id" "profile:planned" "name" "planned" "circle-ids" ["repo:y"]
             "schedule" {"state" "verified" "jobs" [{"enabled" true}]}}
            {"id" "profile:off" "name" "off" "circle-ids" ["repo:y"]
             "schedule" {"state" "no-source-cron" "jobs" []}}]}})

(def receipts
  [{"request" {"bot-id" "runner" "slot" (- now (* 2 h)) "job-id" "j1" "issue-id" "AWAI-2"} "domain-result" {"status" "measured"}}
   {"request" {"bot-id" "runner" "slot" (- now (* 3 h)) "job-id" "j1" "issue-id" "AWAI-2"} "domain-result" {"status" "measured"}}
   {"request" {"bot-id" "old" "slot" (- now (* 50 h)) "job-id" "j2" "issue-id" "AWAI-3"} "domain-result" {"status" "PENDING_POST"}}
   {"request" {"bot-id" "nobody"}}
   {"weird" true}])

(def observation
  {"schema" "awai.original-job-observation.v1" "observedAt" "2026-11-13T12:15:16Z"
   "jobs" [{"catalog-bot-id" "profile:runner" "job-id" "j1" "state" "waiting" "completed-slot" (- now (* 2 h))
            "last-result" {"linear-result-confirmed" true}}]})

(def board
  {"data" {"issues" {"nodes" [{"identifier" "AWAI-2" "title" "two" "state" {"name" "In Progress" "type" "started"} "priority" 3}
                              {"identifier" "AWAI-3" "title" "three" "state" {"name" "Done" "type" "completed"}}
                              {"identifier" "AWAI-9" "title" "nine" "state" {"name" "Todo" "type" "unstarted"} "assignee" {"name" "planned"} "priority" 1}
                              {"identifier" "AWAI-8" "title" "eight" "state" {"name" "Todo" "type" "unstarted"} "assignee" {"name" "planned"} "priority" 1}
                              {"identifier" "AWAI-7" "title" "seven" "state" {"name" "Odd" "type" "nonsense"}}]}}})

(def snap (a/activity-from-records receipts [observation {"schema" "other"}] "2026-11-18T00:00:00Z"))
(def org (a/org-from catalog))
(def act (a/activity-from snap))
(def issues (:issues (a/board-from board)))

(deftest reading
  (is (= 4 (count (:bots org))))
  (is (= "repo:x" (:circle (first (:bots org)))))
  (testing "Soshiki state (no envelope) reads the same"
    (is (= (:bots org) (:bots (a/org-from (get catalog "organization"))))))
  (is (:error (a/org-from {"circles" "nope"})))
  (is (= 4 (count issues)) "an unknown state type is dropped")
  (is (:error (a/board-from {"issues" []})))
  (is (= {:org :org :board :board :activity :activity}
         {:org (a/kind-of catalog) :board (a/kind-of board) :activity (a/kind-of snap)}))
  (is (nil? (a/kind-of {"hello" 1}))))

(deftest activity
  (is (= 2 (get snap "unreadReceipts")) "receipts without a bot or slot are counted, not guessed")
  (is (= 2 (get-in snap ["bots" "profile:runner" "runs"])))
  (is (= ["AWAI-2"] (get-in snap ["bots" "profile:runner" "issues"])))
  (is (= 0 (get-in snap ["bots" "profile:old" "measured"])))
  (is (true? (get-in snap ["bots" "profile:runner" "jobs" 0 "linearConfirmed"])))
  (is (= "2026-11-13T12:15:16Z" (get snap "observedAt")))
  (is (:error (a/activity-from {"schema" "nope"}))))

(deftest statuses
  (let [st (fn [n] (a/bot-status (first (filter #(= n (:name %)) (:bots org))) act issues now))]
    (is (= :active (:state (st "runner"))))
    (is (= :stale (:state (st "old"))) "a receipt older than 24 h is stale, not active")
    (is (= :scheduled (:state (st "planned"))) "a schedule is not a run")
    (is (= :held (:state (st "off"))))
    (is (true? (:in-progress (st "runner"))) "bound through its receipt")
    (is (= {"completed" 1} (:board (st "old"))))
    (testing "the same bot without the receipt has no board work (negative control)"
      (is (false? (:in-progress (a/bot-status {:id "profile:runner" :name "runner" :schedule-state "verified" :jobs 1}
                                              {:bots {}} issues now)))))))

(deftest dandori-rules
  (is (= "take/resume" (a/take-reason 3 2 1 0 false)))
  (is (= "take/orphan" (a/take-reason 3 2 2 0 false)))
  (is (= "take/new" (a/take-reason 2 2 0 0 false)))
  (is (= "take/assigned-other" (a/take-reason 2 2 2 0 false)))
  (is (= "take/plan" (a/take-reason 1 1 0 0 false)))
  (is (= "take/not-role" (a/take-reason 1 2 0 0 false)))
  (is (= "take/closed" (a/take-reason 4 2 1 0 false)))
  (is (= "take/held" (a/take-reason 3 2 1 0 true)))
  (is (= "take/blocked" (a/take-reason 2 2 0 1 false)))
  (is (= "take/invalid" (a/take-reason 6 2 0 0 false)))
  (testing "next: same priority, the older (lower number) issue first"
    (is (= "AWAI-8" (:id (a/next-take (a/bound-issues "planned" [] issues))))))
  (is (= "take/resume" (:reason (a/next-take (a/bound-issues "runner" ["AWAI-2"] issues)))))
  (is (nil? (a/next-take (a/bound-issues "old" ["AWAI-3"] issues))) "a completed issue is not takeable"))

(deftest fleet
  (let [sm (a/summary org act issues now)]
    (is (= [4 3 2 1] [(:registered sm) (:verified sm) (:ran sm) (:active sm)]))
    (is (false? (:stale? sm))))
  (is (true? (:stale? (a/summary org act issues (+ now (* 30 h))))))
  (is (= ["x" "y"] (map :name (a/circle-rows org act issues now nil))))
  (is (= ["y"] (map :name (a/circle-rows org act issues now "plan"))) "search matches bot names")
  (let [w (a/hourly-window act 4)]
    (is (= 4 (count w)))
    (is (= [0 0 1 1] (map second (a/hourly-window act 4))) "zero-filled, ending at the newest receipt hour")))
