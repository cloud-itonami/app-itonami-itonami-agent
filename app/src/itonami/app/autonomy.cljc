(ns itonami.app.autonomy
  "How far the bots are getting on their own, from three files the person
  brings to the app:

    org       the organization: an `awai.bot-fleet.v1` catalog (what Soshiki
              imports) or Soshiki's own saved state — circles, roles, bots and
              each bot's schedule.
    board     the work: a Dandori (or Linear) GraphQL issues export.
    activity  what actually ran: an `itonami.autonomy-snapshot.v1` built by
              scripts/autonomy-snapshot.clj from the job receipts and job
              observations.

  Pure. A schedule is not evidence that anything ran; only receipts and
  observations are. An observation older than the stale window is shown as
  stale, never as current."
  (:require [clojure.string :as str]))

(def hour 3600000)
(def stale-after (* 24 hour))

;; ------------------------------------------------------------ org

(defn- bot-key [s]
  (when (and (string? s) (not (str/blank? s)))
    (if (str/starts-with? s "profile:") s (str "profile:" s))))

(defn- org-shape [o]
  (when (and (map? o) (sequential? (get o "circles")) (sequential? (get o "bots")))
    o))

(defn org-from
  "Normalize a catalog ({\"schema\" \"awai.bot-fleet.v1\" \"organization\" …})
  or a Soshiki state ({\"circles\" \"roles\" \"bots\" …}). {:error} otherwise."
  [v]
  (let [o (or (when (= "awai.bot-fleet.v1" (get v "schema")) (org-shape (get v "organization")))
              (org-shape v))]
    (if-not o
      {:error "not an organization (awai.bot-fleet.v1 catalog or Soshiki state)"}
      {:circles (vec (for [c (get o "circles") :when (string? (get c "id"))]
                       {:id (get c "id") :name (or (get c "name") (get c "id"))
                        :parent (get c "parent-id") :purpose (get c "purpose")}))
       :bots (vec (for [b (get o "bots")
                        :let [id (bot-key (or (get b "id") (get b "name")))
                              sched (get b "schedule")
                              jobs (get sched "jobs")]
                        :when id]
                    {:id id
                     :name (or (get b "name") (subs id 8))
                     :purpose (get b "purpose")
                     :circle (first (get b "circle-ids"))
                     :schedule-state (get sched "state")
                     :jobs (count jobs)
                     :enabled-jobs (count (filter #(true? (get % "enabled")) jobs))
                     :runtime-state (get-in b ["runtime" "state"])
                     :linear (get-in b ["linear" "state"])}))})))

;; ------------------------------------------------------------ board

(def state-types ["triage" "backlog" "unstarted" "started" "completed" "canceled"])
(def state-index (zipmap state-types (range)))

(defn board-from
  "Issues from a Dandori / Linear GraphQL export ({\"data\" {\"issues\" {\"nodes\" […]}}})."
  [v]
  (let [nodes (get-in v ["data" "issues" "nodes"])]
    (if-not (sequential? nodes)
      {:error "not a Dandori/Linear GraphQL issues export"}
      {:issues (vec (for [n nodes
                          :let [t (get-in n ["state" "type"])]
                          :when (and (string? (get n "identifier")) (contains? state-index t))]
                      {:id (get n "identifier")
                       :title (get n "title")
                       :state (get-in n ["state" "name"])
                       :state-type t
                       :assignee (get-in n ["assignee" "name"])
                       :priority (get n "priority")
                       :estimate (get n "estimate")}))})))

;; ------------------------------------------------------------ dandori work rules
;; Ported from cloud-itonami/app-itonami-dandori kotoba/dandori/work.kotoba
;; (take-reason, take-ok?, before?). role 1 steward, 2 worker; assignee
;; 0 nobody, 1 the asker, 2 someone else.

(defn take-reason [state role assignee open-blockers claim-live]
  (cond
    (or (< state 0) (> state 5) (< role 1) (> role 2) (< assignee 0) (> assignee 2)) "take/invalid"
    (or (= state 4) (= state 5)) "take/closed"
    claim-live "take/held"
    (> open-blockers 0) "take/blocked"
    (= state 3) (cond (not= role 2) "take/not-role" (= assignee 1) "take/resume" :else "take/orphan")
    (= state 2) (cond (not= role 2) "take/not-role" (= assignee 2) "take/assigned-other" :else "take/new")
    (not= role 1) "take/not-role"
    :else "take/plan"))

(defn take-ok? [reason] (contains? #{"take/resume" "take/orphan" "take/new" "take/plan"} reason))

(def ^:private take-rank {"take/resume" 0 "take/orphan" 1 "take/new" 2 "take/plan" 2})
(defn- priority-rank [p] (if (and (number? p) (<= 1 p 4)) p 5))
(defn- issue-number [id] (or (some-> (re-find #"(\d+)$" (str id)) second parse-long) 0))

(defn bound-issues
  "The board issues that belong to a bot: assigned to it by name, or named
  by its own job receipts (bots record work as Linear issues while the
  assignee is often a person)."
  [bot-name receipt-issues issues]
  (let [ids (set receipt-issues)]
    (filter #(or (= bot-name (:assignee %)) (contains? ids (:id %))) issues)))

(defn next-take
  "The issue this bot (as a worker) would take next from its bound issues,
  with the dandori reason; nil when nothing is takeable. Claims and blockers
  are not in an export, so none are assumed."
  [bound]
  (->> bound
       (map (fn [i] (assoc i :reason (take-reason (state-index (:state-type i)) 2 1 0 false))))
       (filter #(take-ok? (:reason %)))
       (sort-by (juxt #(take-rank (:reason %)) #(priority-rank (:priority %)) #(issue-number (:id %))))
       first))

;; ------------------------------------------------------------ activity

(defn activity-from-records
  "Aggregate job receipts and job observations (parsed JSON maps) into an
  `itonami.autonomy-snapshot.v1` value. Receipts: {\"request\" {\"bot-id\"
  \"slot\" \"job-id\" \"issue-id\"} \"domain-result\" {\"status\"}}.
  Observations: {\"schema\" \"awai.original-job-observation.v1\"
  \"observedAt\" \"jobs\" […]}. Unknown shapes are counted, not guessed."
  [receipts observations generated-at]
  (let [rs (for [r receipts
                 :let [q (get r "request")
                       bot (bot-key (get q "bot-id"))
                       slot (get q "slot")]
                 :when (and bot (number? slot))]
             {:bot bot :at slot :job (get q "job-id") :issue (get q "issue-id")
              :status (get-in r ["domain-result" "status"])})
        obs (filter #(= "awai.original-job-observation.v1" (get % "schema")) observations)
        jobs (for [o obs j (get o "jobs") :let [bot (bot-key (get j "catalog-bot-id"))] :when bot]
               {:bot bot :job (get j "job-id") :state (get j "state") :completed (get j "completed-slot")
                :linear (get-in j ["last-result" "linear-result-confirmed"])})
        by-bot (group-by :bot rs)
        jobs-by-bot (group-by :bot jobs)
        hours (frequencies (map #(* hour (quot (:at %) hour)) rs))]
    {"schema" "itonami.autonomy-snapshot.v1"
     "generatedAt" generated-at
     "observedAt" (last (sort (keep #(get % "observedAt") obs)))
     "unreadReceipts" (- (count receipts) (count rs))
     "hourly" (vec (sort-by first (map (fn [[h n]] [h n]) hours)))
     "bots" (into (sorted-map)
                  (for [bot (distinct (concat (keys by-bot) (keys jobs-by-bot)))
                        :let [runs (get by-bot bot)
                              js (get jobs-by-bot bot)]]
                    [bot {"runs" (count runs)
                          "measured" (count (filter #(= "measured" (:status %)) runs))
                          "lastAt" (when (seq runs) (apply max (map :at runs)))
                          "issues" (vec (sort (distinct (keep :issue runs))))
                          "jobs" (vec (for [j js] {"id" (:job j) "state" (:state j)
                                                   "completedAt" (:completed j)
                                                   "linearConfirmed" (true? (:linear j))}))}]))}))

(defn activity-from
  "Validate an `itonami.autonomy-snapshot.v1` value."
  [v]
  (if (and (= "itonami.autonomy-snapshot.v1" (get v "schema")) (map? (get v "bots"))
           (sequential? (get v "hourly")))
    {:observed-at (get v "observedAt")
     :generated-at (get v "generatedAt")
     :hourly (vec (get v "hourly"))
     :bots (into {} (for [[k b] (get v "bots") :when (bot-key k)]
                      [(bot-key k) {:runs (get b "runs" 0) :measured (get b "measured" 0)
                                    :last-at (get b "lastAt") :issues (vec (get b "issues"))
                                    :jobs (vec (get b "jobs"))
                                    :in-progress-at (get b "inProgressAt")
                                    :usage (get b "usage")
                                    :deliveries (vec (get b "deliveries"))}]))}
    {:error "not an itonami.autonomy-snapshot.v1"}))

(defn kind-of
  "Which of the three inputs a parsed JSON file is, or nil."
  [v]
  (cond (= "itonami.autonomy-snapshot.v1" (get v "schema")) :activity
        (sequential? (get-in v ["data" "issues" "nodes"])) :board
        (not (:error (org-from v))) :org))

;; ------------------------------------------------------------ status

(defn bot-status
  "One bot's state at `now` (ms):
    :active     a receipt within the stale window
    :stale      receipts, none within the window
    :scheduled  a verified schedule with jobs, no receipt
    :held       no source schedule, failed verification, or no jobs
    :unknown    not in the organization file
  plus :in-progress when one of its bound board issues is started, :running
  when an in-progress run started within the stale window, and the optional
  :usage and :deliveries the developer-bot snapshot carries (nil/[] when
  the file predates them)."
  [bot activity issues now]
  (let [a (get-in activity [:bots (:id bot)])
        last-at (:last-at a)
        mine (bound-issues (:name bot) (:issues a) issues)
        state (cond
                (and last-at (<= (- now last-at) stale-after)) :active
                last-at :stale
                (and (= "verified" (:schedule-state bot)) (pos? (:jobs bot 0))) :scheduled
                (:schedule-state bot) :held
                :else :unknown)]
    {:state state
     :last-at last-at
     :runs (:runs a 0)
     :issues (:issues a [])
     :board (frequencies (map :state-type mine))
     :in-progress (boolean (some #(= "started" (:state-type %)) mine))
     :running (boolean (and (:in-progress-at a)
                            (<= (- now (:in-progress-at a)) stale-after)))
     :usage (:usage a)
     :deliveries (:deliveries a [])
     :next (next-take mine)}))

(def state-order [:active :stale :scheduled :held :unknown])

(defn summary
  "Fleet funnel at `now`: registered → schedule verified → ever ran →
  active in the window, plus the board."
  [org activity issues now]
  (let [bots (:bots org)
        st (map #(:state (bot-status % activity issues now)) bots)
        f (frequencies st)]
    {:registered (count bots)
     :verified (count (filter #(= "verified" (:schedule-state %)) bots))
     :ran (+ (f :active 0) (f :stale 0))
     :active (f :active 0)
     :by-state f
     :observed-at (:observed-at activity)
     :stale? (boolean (and (:observed-at activity) (seq (:hourly activity))
                           (> (- now (first (last (:hourly activity)))) stale-after)))
     :board (frequencies (map :state-type issues))}))

(defn circle-rows
  "Circles with their bots' state counts, most active first; `q` filters by
  circle or bot name."
  [org activity issues now q]
  (let [q (some-> q str/trim str/lower-case not-empty)
        statuses (into {} (for [b (:bots org)] [(:id b) (:state (bot-status b activity issues now))]))
        bots-by-circle (group-by :circle (:bots org))]
    (->> (:circles org)
         (keep (fn [c]
                 (let [bs (get bots-by-circle (:id c))
                       hit? (or (nil? q) (str/includes? (str/lower-case (:name c)) q)
                                (some #(str/includes? (str/lower-case (:name %)) q) bs))]
                   (when (and (seq bs) hit?)
                     (assoc c :bots (count bs) :states (frequencies (map #(statuses (:id %)) bs)))))))
         (sort-by (juxt #(- (get-in % [:states :active] 0)) #(- (get-in % [:states :stale] 0)) :name))
         vec)))

(defn hourly-window
  "The last `hours` hourly counts ending at the newest hour with a receipt."
  [activity hours]
  (let [hs (:hourly activity)]
    (when (seq hs)
      (let [end (first (last hs))
            m (into {} (map vec hs))]
        (vec (for [i (range (dec hours) -1 -1) :let [h (- end (* i hour))]] [h (get m h 0)]))))))
