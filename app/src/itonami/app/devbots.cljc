(ns itonami.app.devbots
  "Developer-bot task directories into the Autonomy view's existing files.

  Each task is an isolated clone holding `.agent-output/` with `task.json`
  (issue, repo, branch, title, merge, tests), `runN.start` / `runN.end`
  (UTC ISO 8601) / `runN.rc` / `runN.jsonl` (run 1 is the implementer on
  Muse, runs 2+ are the single `shinsa` review profile), `verdict.json`
  (`itonami.dev.review-verdict.v1`) and `deliver.json` (gate decision,
  PR URL, merge commit).

  `build-fleet` takes already-listed task file contents and returns an
  `awai.bot-fleet.v1` catalog plus an `itonami.autonomy-snapshot.v1`
  snapshot, so the unchanged Autonomy view imports both. Mapping:

  - circle: the Linear project when `issue->circle` names the issue's
    project, else one circle named for the task repository.
  - bots: one implementer bot per circle, plus the single `shinsa` bot
    (its `circle-ids` span every circle; the view shows it under the first).
  - runs: each placed run is one run record bound to its task's issue.
    Finished runs file under their end time, in-progress runs under their
    start time, so the view's existing 24 h stale rule applies unchanged:
    an in-progress run reads as running only while its start is recent.

  Fail-closed rules: binding comes only from `task.json`'s issue key, never
  from scanning logs; a verdict or delivery for another issue is ignored;
  missing or malformed files are counted as unknown, never as success.
  Only `rc 0` counts as `measured`. No network, no credentials."
  (:require [clojure.string :as str]))

(def verdict-schema "itonami.dev.review-verdict.v1")
(def issue-re #"^[A-Z]+-\d+$")
(def prune-names #{".git" "node_modules" "target" ".cljs-out" "dist" "gen"})

;; ------------------------------------------------------------ time (portable)

(def ^:private instant-re
  #"^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?Z$")

(defn- leap-year? [y]
  (or (zero? (mod y 400)) (and (zero? (mod y 4)) (not (zero? (mod y 100))))))

(defn- days-in-month [y m]
  (case m 1 31 2 (if (leap-year? y) 29 28) 3 31 4 30 5 31 6 30
           7 31 8 31 9 30 10 31 11 30 12 31 0))

(defn parse-instant-ms
  "Strict UTC ISO 8601 (`YYYY-MM-DDTHH:MM:SS[.frac]Z`, years 1970-9999) to
  epoch milliseconds. Anything else is nil: never guess a time."
  [s]
  (when (string? s)
    (when-let [[_ ys mos ds hs mis ss frac] (re-matches instant-re (str/trim s))]
      (let [y (parse-long ys) mo (parse-long mos) d (parse-long ds)
            h (parse-long hs) mi (parse-long mis) sec (parse-long ss)]
        (when (and (<= 1970 y 9999) (<= 1 mo 12) (<= 1 d (days-in-month y mo))
                   (<= 0 h 23) (<= 0 mi 59) (<= 0 sec 59))
          (let [yp (if (<= mo 2) (dec y) y)
                era (quot yp 400)
                yoe (- yp (* era 400))
                mp (if (<= mo 2) (+ mo 9) (- mo 3))
                doy (+ (quot (+ (* 153 mp) 2) 5) (dec d))
                doe (+ (* yoe 365) (quot yoe 4) (- (quot yoe 100)) doy)
                days (+ (* era 146097) doe (- 719468))
                ms (+ (* (+ (* (+ (* days 24) h) 60) mi) 60000) (* sec 1000))]
            (+ ms (if frac (parse-long (subs (str frac "000") 0 3)) 0))))))))

(defn- civil-from-days [z]
  (let [z (+ z 719468)
        era (quot z 146097)
        doe (- z (* era 146097))
        yoe (quot (+ (- doe (quot doe 1460)) (quot doe 36524) (- (quot doe 146096))) 365)
        y (+ (* era 400) yoe)
        doy (- doe (+ (* 365 yoe) (quot yoe 4) (- (quot yoe 100))))
        mp (quot (+ (* 5 doy) 2) 153)
        d (+ (- doy (quot (+ (* 153 mp) 2) 5)) 1)
        m (+ mp (if (< mp 10) 3 -9))]
    [(+ y (if (<= m 2) 1 0)) m d]))

(defn- pad [n w]
  (let [s (str n)] (str (apply str (repeat (- w (count s)) "0")) s)))

(defn format-instant-ms
  "Epoch milliseconds to UTC ISO 8601. Nil in, nil out."
  [ms]
  (when (number? ms)
    (let [day (long (Math/floor (/ ms 86400000)))
          leftover (- ms (* day 86400000))
          [y mo d] (civil-from-days day)
          h (quot leftover 3600000)
          mi (quot (rem leftover 3600000) 60000)
          sec (quot (rem leftover 60000) 1000)
          milli (rem leftover 1000)]
      (str (pad y 4) "-" (pad mo 2) "-" (pad d 2) "T"
           (pad h 2) ":" (pad mi 2) ":" (pad sec 2) "." (pad milli 3) "Z"))))

;; ------------------------------------------------------------ file walk (injected fs)

(defn find-task-dirs
  "Task directories under `roots`: directories (a root itself included) that
  directly contain an `.agent-output` subdirectory. Never leaves `roots`;
  `prune-names` are not descended into and a found task dir is not descended
  into either. `fs` is `{:is-dir? :list}` so tests can use a fake."
  [{:keys [is-dir? list]} roots]
  (letfn [(walk [dir]
            (let [names (try (or (list dir) []) (catch #?(:clj Exception :cljs :default) _ []))]
              (if (some #(= ".agent-output" %) names)
                (if (try (is-dir? (str dir "/.agent-output"))
                         (catch #?(:clj Exception :cljs :default) _ false))
                  [dir]
                  [])
                (mapcat walk (for [n names
                                   :let [p (str dir "/" n)]
                                   :when (and (not (contains? prune-names n))
                                              (not (str/starts-with? n "."))
                                              (try (is-dir? p)
                                                   (catch #?(:clj Exception :cljs :default) _ false)))]
                               p)))))]
    (vec (distinct (mapcat walk roots)))))

(defn read-task-files
  "Read one task dir's `.agent-output/` files into
  `{:dir :files {name -> string}}`. `fs` adds `:read-file`."
  [{:keys [list read-file] :as fs} dir]
  (let [out (str dir "/.agent-output")
        names (try (or (list out) []) (catch #?(:clj Exception :cljs :default) _ []))
        wanted (filter #(or (= "task.json" %) (= "verdict.json" %) (= "deliver.json" %)
                            (re-matches #"^run\d+\.(start|end|rc|jsonl)$" %))
                       names)]
    {:dir dir
     :files (into {} (keep (fn [n]
                             (let [s (try (read-file (str out "/" n))
                                           (catch #?(:clj Exception :cljs :default) _ nil))]
                               (when (string? s) [n s])))
                           wanted))}))

;; ------------------------------------------------------------ usage

(defn- safe-parse [parse-json s]
  (when (string? s)
    (try (parse-json s) (catch #?(:clj Exception :cljs :default) _ nil))))

(defn- usage-totals [payloads]
  (let [ms (for [p payloads m (get p "models") :when (map? m)] m)
        num (fn [m k] (let [v (get m k)] (if (number? v) v 0)))]
    {"model" (or (last (keep #(when (string? (get % "model")) (get % "model")) ms)) "unknown")
     "calls" (reduce + 0 (map #(num % "calls") ms))
     "inputTokens" (reduce + 0 (map #(num % "input_tokens") ms))
     "cachedTokens" (reduce + 0 (map #(num % "cached_tokens") ms))
     "outputTokens" (reduce + 0 (map #(num % "output_tokens") ms))
     "reasoningTokens" (reduce + 0 (map #(num % "reasoning_tokens") ms))}))

(defn- run-usage [parse-json jsonl]
  (when (string? jsonl)
    (let [by-id (reduce (fn [m line]
                          (let [e (safe-parse parse-json line)]
                            (if (and (map? e)
                                     (= "run.usage" (get e "payload_type"))
                                     (string? (get-in e ["payload" "command_id"])))
                              (assoc m (get-in e ["payload" "command_id"]) (get e "payload"))
                              m)))
                        {} (str/split-lines jsonl))]
      (when (seq by-id)
        (usage-totals (vals by-id))))))

(defn- merge-usage [a b]
  (cond (nil? a) b
        (nil? b) a
        :else {"model" (let [m (get b "model" "unknown")]
                         (if (= "unknown" m) (get a "model" "unknown") m))
               "calls" (+ (get a "calls" 0) (get b "calls" 0))
               "inputTokens" (+ (get a "inputTokens" 0) (get b "inputTokens" 0))
               "cachedTokens" (+ (get a "cachedTokens" 0) (get b "cachedTokens" 0))
               "outputTokens" (+ (get a "outputTokens" 0) (get b "outputTokens" 0))
               "reasoningTokens" (+ (get a "reasoningTokens" 0) (get b "reasoningTokens" 0))}))

;; ------------------------------------------------------------ tasks

(defn- valid-task [parse-json files]
  (let [t (safe-parse parse-json (get files "task.json"))]
    (when (and (map? t)
               (string? (get t "issue")) (re-matches issue-re (get t "issue"))
               (string? (get t "repo")) (not (str/blank? (get t "repo"))))
      t)))

(defn- circle-for [repo issue issue->circle]
  (let [v (get issue->circle issue)]
    (cond (and (map? v) (string? (get v "id")) (not (str/blank? (get v "id"))))
          {:id (get v "id") :name (let [n (get v "name")] (if (string? n) n (get v "id")))}
          (and (string? v) (not (str/blank? v)))
          {:id (str "project:" v) :name v}
          :else {:id (str "repo:" repo) :name repo})))

(defn- slug [s]
  (-> (str s) str/lower-case (str/replace #"^(repo|project):" "")
      (str/replace #"[^a-z0-9]+" "-") (str/replace #"^-+|-+$" "")))

(defn- run-numbers [files]
  (->> (keys files)
       (keep #(when-let [[_ n] (re-matches #"^run(\d+)\.(?:start|end|rc|jsonl)$" %)] (parse-long n)))
       distinct sort))

(defn- parse-rc [s]
  (when (string? s)
    (when (re-matches #"-?\d+" (str/trim s))
      (parse-long (str/trim s)))))

(defn- valid-verdict [parse-json files issue]
  (let [v (safe-parse parse-json (get files "verdict.json"))]
    (when (and (map? v)
               (= verdict-schema (get v "schema"))
               (= "shinsa" (get v "reviewer"))
               (= issue (get v "issue"))
               (contains? #{"approve" "reject" "owner-decision"} (get v "decision")))
      v)))

(defn- valid-deliver [parse-json files issue]
  (let [d (safe-parse parse-json (get files "deliver.json"))
        action (get-in d ["decision" "action"])]
    (when (and (map? d)
               (= issue (get d "task"))
               (contains? #{"merge" "draft" "hold"} action))
      {:action action
       :pr (let [p (get d "pr")] (when (string? p) p))
       :merged-commit (get-in d ["merged" "commit"])
       :merged-at (get-in d ["merged" "at"])
       :at-ms (parse-instant-ms (get d "at"))})))

(defn- task-runs [parse-json files]
  (vec (keep (fn [n]
               (let [p (str "run" n)
                     start-ms (parse-instant-ms (get files (str p ".start")))
                     end-ms (parse-instant-ms (get files (str p ".end")))
                     rc (parse-rc (get files (str p ".rc")))
                     finished? (some? end-ms)
                     slot (if finished? end-ms start-ms)]
                 (when (some? slot)
                   {:n n :reviewer? (not= 1 n)
                    :start-ms start-ms :end-ms end-ms :rc rc
                    :finished? finished? :slot slot
                    :status (if finished?
                              (cond (= 0 rc) "measured"
                                    (integer? rc) "failed"
                                    :else "unknown")
                              "running")
                    :usage (run-usage parse-json (get files (str p ".jsonl")))})))
             (run-numbers files))))

;; ------------------------------------------------------------ build

(defn build-fleet
  "Pure: task file contents to `{:catalog :snapshot :problems}`.

  `tasks` is `[{:dir :files {name -> string}}]` (see `read-task-files`);
  `parse-json` turns a string into parsed JSON or throws/nils on malformed
  input; `issue->circle` maps issue keys to a project name or
  `{\"id\" \"name\"}`; `now-ms` is only used for `generatedAt`."
  [{:keys [now-ms issue->circle tasks parse-json]}]
  (let [issue->circle (or issue->circle {})
        parsed (for [{:keys [dir files]} tasks
                     :let [t (valid-task parse-json files)]]
                 (if-not t
                   {:dir dir :unread? true}
                   (let [issue (get t "issue")
                         runs (task-runs parse-json files)
                         verdict (valid-verdict parse-json files issue)
                         deliver (valid-deliver parse-json files issue)
                         unplaced (- (count (run-numbers files)) (count runs))]
                     {:dir dir :task t :issue issue :repo (get t "repo")
                      :circle (circle-for (get t "repo") issue issue->circle)
                      :runs runs :verdict verdict :deliver deliver
                      :unplaced unplaced})))
        good (vec (remove :unread? parsed))
        unread-tasks (count (filter :unread? parsed))
        circles (->> good (map :circle) distinct (sort-by :id) vec)
        slugs (reduce (fn [m c]
                        (let [base (let [s (slug (:id c))] (if (str/blank? s) "bot" s))]
                          (if (contains? (set (vals m)) base)
                            (assoc m (:id c) (str base "-" (count m)))
                            (assoc m (:id c) base))))
                      {} circles)
        bot-id (fn [c] (str "profile:dev-" (get slugs (:id c))))
        bot-name (fn [c] (str "dev-" (get slugs (:id c))))
        delivery-of (fn [{:keys [issue verdict deliver runs]}]
                      {"issue" issue
                       "verdict" (when verdict (get verdict "decision"))
                       "action" (or (:action deliver) "unknown")
                       "pr" (:pr deliver)
                       "mergedCommit" (:merged-commit deliver)
                       "mergedAt" (:merged-at deliver)})
        job-of (fn [{:keys [issue deliver runs]}]
                 (let [latest (last (sort-by :slot runs))]
                   {"id" issue
                    "state" (or (:action deliver) (:status latest) "unknown")
                    "completedAt" (or (:at-ms deliver) (:end-ms latest))
                    "linearConfirmed" (boolean (:merged-commit deliver))}))
        impl-runs (vec (for [g good r (:runs g) :when (not (:reviewer? r))] (assoc r :issue (:issue g))))
        rev-runs (vec (for [g good r (:runs g) :when (:reviewer? r)] (assoc r :issue (:issue g))))
        per-bot (fn [runs]
                  {"runs" (count runs)
                   "measured" (count (filter #(= "measured" (:status %)) runs))
                   "lastAt" (when (seq runs) (apply max (map :slot runs)))
                   "issues" (vec (sort (distinct (map :issue runs))))
                   "inProgressAt" (let [ip (keep #(when (and (not (:finished? %)) (:start-ms %)) (:start-ms %)) runs)]
                                    (when (seq ip) (apply max ip)))
                   "usage" (reduce merge-usage nil (keep :usage runs))})
        catalog
        {"schema" "awai.bot-fleet.v1"
         "organization"
         {"circles" (into [{"id" "fleet" "name" "Fleet" "parent-id" nil}]
                          (map (fn [c] {"id" (:id c) "name" (:name c) "parent-id" "fleet"}) circles))
          "bots" (vec (concat
                        (for [c circles]
                          {"id" (bot-id c) "name" (bot-name c)
                           "purpose" (str "Developer-bot implementer (Muse) for " (:name c))
                           "circle-ids" [(:id c)]
                           "schedule" {"state" "no-source-cron" "jobs" []}})
                        (when (seq circles)
                          [{"id" "profile:shinsa" "name" "shinsa"
                            "purpose" "Single reviewer (shinsa profile) for developer-bot changes"
                            "circle-ids" (vec (map :id circles))
                            "schedule" {"state" "no-source-cron" "jobs" []}}])))}}
        shinsa? (seq (filter :reviewer? (mapcat :runs good)))
        snapshot-bots
        (into (sorted-map)
              (concat
               (for [c circles
                     :let [rs (vec (for [g good :when (= (:circle g) c)
                                          r (:runs g) :when (not (:reviewer? r))]
                                      (assoc r :issue (:issue g))))
                           ts (filter #(= (:circle %) c) good)]]
                 [(bot-id c) (merge (per-bot rs)
                                    {"jobs" (mapv job-of ts)
                                     "deliveries" (mapv delivery-of ts)})])
               (when shinsa?
                 (let [ts (filter #(seq (filter :reviewer? (:runs %))) good)]
                   [["profile:shinsa"
                     (merge (per-bot rev-runs)
                            {"jobs" (mapv job-of ts)
                             "deliveries" (mapv delivery-of ts)})]]))))
        slots (map :slot (concat impl-runs rev-runs))
        hourly (vec (sort-by first (map (fn [[h n]] [h n])
                                        (frequencies (map #(* 3600000 (quot % 3600000)) slots)))))
        problems (vec (concat
                       (for [p parsed :when (:unread? p)]
                         (str (:dir p) ": no task.json with an issue key and repo; counted as unknown"))
                       (for [g good :when (pos? (:unplaced g))]
                         (str (:dir g) ": " (:unplaced g) " run(s) without a readable start or end; counted, not placed"))))]
    {:catalog catalog
     :snapshot {"schema" "itonami.autonomy-snapshot.v1"
                "generatedAt" (format-instant-ms now-ms)
                "observedAt" (when (seq slots) (format-instant-ms (apply max slots)))
                "unreadReceipts" (reduce + 0 (map :unplaced good))
                "unreadTasks" unread-tasks
                "hourly" hourly
                "bots" snapshot-bots}
     :problems problems}))
