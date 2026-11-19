(ns itonami.app.devbots-test
  "Developer-bot task directories into the Autonomy view's existing files.

  Fixtures are synthetic task directories (file-name -> string contents),
  built by `task` below. Real directory layout is covered through the
  injected fake filesystem in `finding`; the bb script wires the real one."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [itonami.app.devbots :as d]
            [itonami.app.autonomy :as a]
            #?(:clj [cheshire.core :as json])))

(def parse-json
  #?(:clj (fn [s] (json/parse-string s))
     :cljs (fn [s] (try (js->clj (.parse js/JSON s)) (catch :default _ nil)))))

(def now 1795052409000) ;; 2026-11-19T01:00:09.000Z
(def h 3600000)
(defn iso [ms] (d/format-instant-ms ms))
(defn ago [ms-ago] (iso (- now ms-ago)))

(defn task [issue repo files]
  {:dir (str "/r/" issue)
   :files (merge {"task.json" (str "{\"issue\":\"" issue "\",\"repo\":\"" repo
                                   "\",\"branch\":\"muse/" issue "\",\"title\":\"t\","
                                   "\"merge\":\"auto\",\"tests\":[\"npm test\"]}")} files)})

(defn usage-line [cmd calls]
  (str "{\"payload_type\":\"run.usage\",\"payload\":{\"command_id\":\"" cmd
       "\",\"models\":[{\"model\":\"muse-spark-1.3\",\"calls\":" calls
       ",\"input_tokens\":100,\"cached_tokens\":10,\"output_tokens\":20,\"reasoning_tokens\":5}]}}"))

(defn verdict [issue decision]
  (str "{\"schema\":\"itonami.dev.review-verdict.v1\",\"reviewer\":\"shinsa\",\"issue\":\""
       issue "\",\"decision\":\"" decision "\",\"acceptance\":[],\"findings\":[],\"checks\":[]}"))

(defn deliver [issue action pr merged?]
  (str "{\"task\":\"" issue "\",\"at\":\"" (ago h) "\",\"decision\":{\"action\":\""
       action "\",\"reasons\":[]},\"tests\":[],\"files\":9"
       (when pr (str ",\"pr\":\"" pr "\""))
       (if merged?
         (str ",\"merged\":{\"commit\":\"abc1234\",\"at\":\"" (ago h) "\"}")
         ",\"merged\":null")
       "}"))

(defn build [tasks issue->circle]
  (d/build-fleet {:now-ms now :issue->circle (or issue->circle {})
                  :tasks tasks :parse-json parse-json}))

(defn bot [snapshot id] (get-in snapshot ["bots" id]))

(def finished
  (task "AWAI-100" "cloud-itonami/itonami-agent"
        {"run1.start" (ago (* 3 h)) "run1.end" (ago (* 2 h)) "run1.rc" "0"
         "run1.jsonl" (str (usage-line "c1" 70) "\n" (usage-line "c1" 60) "\n"
                           (usage-line "c2" 22) "\nnot json\n"
                           "AWAI-9999 reached only in log text")
         "run2.start" (ago (* 2 h)) "run2.end" (ago h) "run2.rc" "0"
         "run2.jsonl" (usage-line "c9" 5)
         "verdict.json" (verdict "AWAI-100" "approve")
         "deliver.json" (deliver "AWAI-100" "merge" "https://github.com/x/y/pull/1" true)}))

(deftest time-round-trip
  (is (= "2026-11-19T01:40:09.326Z"
         (d/format-instant-ms (d/parse-instant-ms "2026-11-19T01:40:09.326Z"))))
  (is (= "2024-02-29T12:00:00.000Z"
         (d/format-instant-ms (d/parse-instant-ms "2024-02-29T12:00:00Z")))
      "leap day parses")
  (doseq [bad ["nope" "" nil "2026-13-01T00:00:00Z" "2023-02-29T00:00:00Z"
               "2026-11-19 01:40:09" "2026-11-19T25:00:00Z" "1969-12-31T23:59:59Z"]]
    (is (nil? (d/parse-instant-ms bad)) (str "rejected, not guessed: " (pr-str bad)))))

(deftest finding
  (let [tree {"/r1" ["t1" "t2" ".git" "node_modules"]
              "/r1/t1" [".agent-output"] "/r1/t1/.agent-output" ["task.json"]
              "/r1/t2" [".agent-output" "sub"] "/r1/t2/.agent-output" ["task.json"]
              "/r1/t2/sub" [".agent-output"] "/r1/t2/sub/.agent-output" ["task.json"]
              "/r1/.git" ["x"] "/r1/node_modules" ["y"]
              "/outside" ["oot"] "/outside/oot" [".agent-output"]}
        fs {:is-dir? #(contains? tree %)
            :list #(get tree %)
            :read-file (fn [p] (when (= p "/r1/t1/.agent-output/task.json") "{}"))}]
    (is (= ["/r1/t1" "/r1/t2"] (d/find-task-dirs fs ["/r1"]))
        "roots only: no descent into a found task dir, pruned names skipped")
    (is (= [] (d/find-task-dirs fs ["/nothing"]))
        "an unknown root finds nothing")
    (let [got (d/find-task-dirs fs ["/r1" "/outside"])]
      (is (not (some #(str/starts-with? % "/outside") got))
          "covered by the roots-only walk: only listed roots are scanned")
      (is (= ["/r1/t1"] (d/find-task-dirs fs ["/r1/t1"]))
          "a root may itself be a task directory"))
    (is (= {:dir "/r1/t1" :files {"task.json" "{}"}}
           (d/read-task-files fs "/r1/t1"))
        "only the known file names are read")))

(deftest finished-and-merged
  (let [{:keys [catalog snapshot problems]} (build [finished] nil)
        impl "profile:dev-cloud-itonami-itonami-agent"]
    (is (empty? problems))
    (testing "catalog: one circle per repo, implementer plus the single shinsa"
      (is (= "awai.bot-fleet.v1" (get catalog "schema")))
      (is (= "repo:cloud-itonami/itonami-agent"
             (get-in catalog ["organization" "circles" 1 "id"])))
      (is (= [impl "profile:shinsa"]
             (mapv #(get % "id") (get-in catalog ["organization" "bots"]))))
      (is (= ["repo:cloud-itonami/itonami-agent"]
             (get-in catalog ["organization" "bots" 1 "circle-ids"]))))
    (testing "snapshot: the unchanged view imports it"
      (is (= "itonami.autonomy-snapshot.v1" (get snapshot "schema")))
      (is (= :activity (a/kind-of snapshot)))
      (is (= :org (a/kind-of catalog)))
      (is (not (:error (a/org-from catalog))) "catalog validates")
      (is (not (:error (a/activity-from snapshot))) "snapshot validates"))
    (testing "implementer run: usage keeps the last record per command"
      (let [b (bot snapshot impl)]
        (is (= 1 (get b "runs")))
        (is (= 1 (get b "measured")) "rc 0 is measured")
        (is (= ["AWAI-100"] (get b "issues")))
        (is (= {"model" "muse-spark-1.3" "calls" 82 "inputTokens" 200
                "cachedTokens" 20 "outputTokens" 40 "reasoningTokens" 10}
               (get b "usage"))
            "c1's last record (60) wins over its earlier one (70), plus c2 (22)")
        (is (nil? (get b "inProgressAt")) "nothing in progress")))
    (testing "review run belongs to shinsa, delivery carries the merge"
      (let [b (bot snapshot "profile:shinsa")]
        (is (= 1 (get b "runs")))
        (is (= 5 (get-in b ["usage" "calls"])))
        (is (= [{"issue" "AWAI-100" "verdict" "approve" "action" "merge"
                 "pr" "https://github.com/x/y/pull/1"
                 "mergedCommit" "abc1234" "mergedAt" (ago h)}]
               (get b "deliveries")))
        (is (= "merge" (get-in b ["jobs" 0 "state"])))
        (is (true? (get-in b ["jobs" 0 "linearConfirmed"])))
        (is (= (- now h) (get-in b ["jobs" 0 "completedAt"])))
        (is (= (ago h) (d/format-instant-ms (get-in b ["jobs" 0 "completedAt"]))))))
    (testing "log text never binds an issue"
      (is (every? #(= ["AWAI-100"] %) (map #(get % "issues") (vals (get snapshot "bots"))))
          "AWAI-9999 from run1.jsonl appears nowhere"))))

(deftest rejected-review-holds
  (let [t (task "AWAI-101" "cloud-itonami/itonami-agent"
                {"run1.start" (ago (* 3 h)) "run1.end" (ago (* 2 h)) "run1.rc" "0"
                 "run2.start" (ago (* 2 h)) "run2.end" (ago h) "run2.rc" "0"
                 "verdict.json" (verdict "AWAI-101" "reject")
                 "deliver.json" (deliver "AWAI-101" "hold" "https://github.com/x/y/pull/2" false)})
        {:keys [snapshot]} (build [t] nil)
        impl "profile:dev-cloud-itonami-itonami-agent"]
    (is (= "reject" (get-in (bot snapshot impl) ["deliveries" 0 "verdict"])))
    (is (= "hold" (get-in (bot snapshot impl) ["deliveries" 0 "action"])))
    (is (nil? (get-in (bot snapshot impl) ["deliveries" 0 "mergedCommit"]))
        "a rejected review never fabricates a merge")
    (is (false? (get-in (bot snapshot impl) ["jobs" 0 "linearConfirmed"])))))

(deftest verdict-schema-mismatch-holds
  (testing "a verdict that is not the shinsa schema is ignored, never success"
    (let [t (task "AWAI-102" "cloud-itonami/itonami-agent"
                  {"run1.start" (ago (* 3 h)) "run1.end" (ago (* 2 h)) "run1.rc" "0"
                   "verdict.json" "{\"schema\":\"other\",\"decision\":\"approve\"}"
                   "deliver.json" (deliver "AWAI-102" "hold"
                                           "https://github.com/x/y/pull/3" false)})
          {:keys [snapshot]} (build [t] nil)
          b (bot snapshot "profile:dev-cloud-itonami-itonami-agent")]
      (is (nil? (get-in b ["deliveries" 0 "verdict"])))
      (is (= "hold" (get-in b ["deliveries" 0 "action"])))
      (is (= 1 (get b "measured")) "the run itself still counts; only the verdict is unknown"))))

(deftest draft-stays-draft
  (let [t (task "AWAI-103" "cloud-itonami/itonami-agent"
                {"run1.start" (ago (* 3 h)) "run1.end" (ago (* 2 h)) "run1.rc" "0"
                 "verdict.json" (verdict "AWAI-103" "owner-decision")
                 "deliver.json" (deliver "AWAI-103" "draft" nil false)})
        {:keys [snapshot]} (build [t] nil)
        b (bot snapshot "profile:dev-cloud-itonami-itonami-agent")]
    (is (= "owner-decision" (get-in b ["deliveries" 0 "verdict"])))
    (is (= "draft" (get-in b ["deliveries" 0 "action"])))
    (is (= "draft" (get-in b ["jobs" 0 "state"])))))

(deftest in-progress-running-only-while-recent
  (testing "a recent in-progress run is active and running"
    (let [recent (task "AWAI-104" "r/repo" {"run1.start" (ago (* 2 h))})
          {:keys [catalog snapshot]} (build [recent] nil)
          o (a/org-from catalog)
          act (a/activity-from snapshot)
          st (a/bot-status (first (:bots o)) act {:issues []} now)
          b (bot snapshot "profile:dev-r-repo")]
      (is (= :active (:state st)) "the run files under its start")
      (is (true? (:running st)))
      (is (= 1 (get b "runs")))
      (is (= 0 (get b "measured")) "no finished run, nothing measured")
      (is (= (ago (* 2 h)) (d/format-instant-ms (get b "lastAt"))))))
  (testing "an old in-progress run is stale, never running"
    (let [old (task "AWAI-105" "r/oldrepo" {"run1.start" (ago (* 50 h))})
          {:keys [catalog snapshot]} (build [old] nil)
          o (a/org-from catalog)
          act (a/activity-from snapshot)
          st (a/bot-status (first (:bots o)) act {:issues []} now)]
      (is (= :stale (:state st)))
      (is (false? (:running st))))))

(deftest crashed-run-never-success
  (let [t (task "AWAI-106" "r/repo"
                {"run1.start" (ago (* 3 h)) "run1.end" (ago (* 2 h)) "run1.rc" "1"})
        {:keys [snapshot problems]} (build [t] nil)
        impl "profile:dev-r-repo"
        b (bot snapshot impl)]
    (is (= 1 (get b "runs")))
    (is (= 0 (get b "measured")) "a non-zero rc is never measured")
    (is (empty? problems) "a placed crashed run is not unread")))

(deftest missing-and-malformed-fail-closed
  (testing "a task with no runs is held, not active"
    (let [{:keys [catalog snapshot]} (build [(task "AWAI-107" "r/repo" {})] nil)
          o (a/org-from catalog)
          act (a/activity-from snapshot)]
      (is (= :held (:state (a/bot-status (first (:bots o)) act {:issues []} now))))
      (is (= 0 (get (bot snapshot "profile:dev-r-repo") "runs" 0)))))
  (testing "missing task.json is unread, never a bot"
    (let [{:keys [catalog snapshot problems]} (build [{:dir "/r/ghost" :files {}}] nil)]
      (is (= 1 (get snapshot "unreadTasks")))
      (is (= [] (get-in catalog ["organization" "bots"])) "no bot is invented")
      (is (= 1 (count problems)))))
  (testing "malformed task.json and malformed times are unread, not guessed"
    (let [{:keys [snapshot problems]}
          (build [{:dir "/r/bad" :files {"task.json" "{oops"
                                          "run1.start" "yesterday"
                                          "run1.rc" "zero"}}
                   (task "AWAI-108" "r/repo"
                         {"run1.start" (ago (* 3 h)) "run1.end" (ago (* 2 h))
                          "run1.rc" "0"})]
                 nil)]
      (is (= 1 (get snapshot "unreadTasks")))
      (is (= 1 (count problems)) "the bad dir is reported")
      (is (= ["AWAI-108"] (get (bot snapshot "profile:dev-r-repo") "issues"))
          "the good task is unaffected")))
  (testing "a run without any readable time is counted, not placed"
    (let [{:keys [snapshot]}
          (build [(task "AWAI-109" "r/repo" {"run1.rc" "0" "run1.jsonl" (usage-line "c1" 3)})] nil)]
      (is (= 1 (get snapshot "unreadReceipts")))
      (is (= 0 (get (bot snapshot "profile:dev-r-repo") "runs" 0)))))
  (testing "a verdict or delivery for another issue is ignored"
    (let [{:keys [snapshot]}
          (build [(task "AWAI-110" "r/repo"
                        {"run1.start" (ago (* 3 h)) "run1.end" (ago (* 2 h)) "run1.rc" "0"
                         "verdict.json" (verdict "AWAI-111" "approve")
                         "deliver.json" (deliver "AWAI-111" "merge" "https://x/y/9" true)})]
                 nil)
          b (bot snapshot "profile:dev-r-repo")]
      (is (= "unknown" (get-in b ["deliveries" 0 "action"]))
          "another issue's merge never attaches")
      (is (nil? (get-in b ["deliveries" 0 "verdict"])))
      (is (= "measured" (get-in b ["jobs" 0 "state"]))
          "the job falls back to the run outcome, not the foreign gate"))))

(deftest issue-project-map
  (let [{:keys [catalog snapshot]}
        (build [finished] {"AWAI-100" "Nagare"})
        bots (get-in catalog ["organization" "bots"])]
    (is (= "project:Nagare" (get-in catalog ["organization" "circles" 1 "id"])))
    (is (= ["project:Nagare"] (get-in catalog ["organization" "bots" 0 "circle-ids"])))
    (is (= "profile:shinsa" (get-in catalog ["organization" "bots" 1 "id"])))
    (is (= ["project:Nagare"] (get-in catalog ["organization" "bots" 1 "circle-ids"]))
        "the single shinsa bot spans every circle")
    (is (= 2 (count bots)) "one implementer bot per circle plus shinsa")
    (is (= :org (a/kind-of catalog))))
  (testing "an explicit circle shape is honoured; garbage falls back to the repo"
    (let [{:keys [catalog]} (build [finished (task "AWAI-111" "r/b" {})]
                                  {"AWAI-100" {"id" "proj-1" "name" "One"}
                                   "AWAI-111" ""})]
      (is (= #{"proj-1" "repo:r/b"}
             (set (map #(get % "id") (drop 1 (get-in catalog ["organization" "circles"])))))))))

(deftest reader-round-trip
  (testing "old snapshots without the new keys still import"
    (let [act (a/activity-from {"schema" "itonami.autonomy-snapshot.v1"
                                "observedAt" nil "generatedAt" nil "hourly" []
                                "bots" {"profile:old" {"runs" 1 "measured" 1
                                                       "lastAt" now "issues" [] "jobs" []}}})
          st (a/bot-status {:id "profile:old" :name "old" :schedule-state "verified" :jobs 0}
                           act {:issues []} now)]
      (is (nil? (get-in act [:bots "profile:old" :usage])))
      (is (= [] (get-in act [:bots "profile:old" :deliveries])))
      (is (false? (:running st)))
      (is (= :active (:state st)) "existing states are unchanged")))
  (testing "the new keys reach the bot card facts"
    (let [{:keys [catalog snapshot]} (build [finished] nil)
          o (a/org-from catalog)
          act (a/activity-from snapshot)
          by-id (into {} (map (juxt :id identity) (:bots o)))
          st (a/bot-status (get by-id "profile:dev-cloud-itonami-itonami-agent")
                           act {:issues []} now)]
      (is (= 82 (get-in st [:usage "calls"])))
      (is (= "merge" (get-in st [:deliveries 0 "action"])))
      (is (= :active (:state st)))
      (is (= ["AWAI-100"] (:issues st)) "cards still carry state, last run and issue"))))
