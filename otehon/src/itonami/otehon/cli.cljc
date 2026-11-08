(ns itonami.otehon.cli
  "itonami-otehon: check a profile, dry-run one cron job, plan a real run,
  gate one turn, plan a store move, run the shared cases.

    check      <profile-dir>
    dry-run    <profile-dir> <job-id-or-name> [--capability <name>]...
    tick       <profile-dir>
    turn-gate  <profile-dir> <prompt> [--capability <name>]...
    run-plan   <profile-dir> <job-id-or-name> [--capability <name>]...
               [--script-file <path> | --script-error <text>] [--script-no-wake]
    store-plan --direction import|export --from <dir> --to <dir> [--force]
    pause      [reason words]...   (the store is ITONAMI_DIR, else ~/.itonami)
    resume
    status
    model      <dir> [NAME] [--provider P] [--model M]
    fallback   <dir> [-m M] [--provider P]
    run-exec   <dir> <job> [--capability c]...   (agent turns stop pending: S2b)
    cas-put    <store> <file>...
    cas-get    <store> <sha> <out>
    cas-verify <store>
    cas-gc     <store> [--apply] [--retain-days N]
    cas-reconcile <store> <peerdir>... [--as <node> --registry <reg.edn>]
    fleet-status <peerdir>... [--as <node> --registry <reg.edn>] [--copies N]
    peer-push  <store> <peerdir> [--as <node> --registry <reg.edn>]
    peer-pull  <store> <peerdir> [--as <node> --registry <reg.edn>]
    peer-repair <store> <peerdir>... [--as <node> --registry <reg.edn>]
    market-match <asks.edn> <bids.edn> [--today YYYY-MM-DD] [--secrets <secrets.edn>]
    market-sign <att.edn> --issuer-secret <secret>
    market-verify <att.edn> --suite-secret <secret> [--today YYYY-MM-DD]
    test       <cases.edn>

  tick, turn-gate and run-plan decide without doing: no script runs, no
  inference is called, nothing is saved (side-effects :none). store-plan
  prints the copy plan; the host copies the bytes. pause, resume and
  status touch and read the store exactly like the legacy CLI (the pause
  marker text and every status line are verbatim), so their shared cases
  assert the same strings.
  Output is one canonical EDN value (sorted map keys) so the three runners
  can be compared byte for byte. Exit 0 ok, 1 refused / errors / failures,
  2 held for the owner, 64 usage."
  (:require [clojure.string :as str]
            [itonami.otehon.io :as hio]
            [itonami.otehon.pause :as pause]
            [itonami.otehon.status :as status]
            [itonami.otehon.model :as model]
            [itonami.otehon.exec :as exec]
            [itonami.otehon.cron :as cron]
            [itonami.otehon.profile :as profile]
            [itonami.otehon.yakuwari :as yakuwari]
            [itonami.otehon.yaml :as yaml]
            [itonami.otehon.text :as text]
            [itonami.otehon.tick :as tick]
            [itonami.otehon.store :as store]
            [itonami.otehon.turn :as turn]
            [itonami.otehon.run :as run]
            [itonami.otehon.doctor :as doctor]
            [itonami.otehon.sessions :as sessions]
            [itonami.otehon.cas :as cas]
            [itonami.otehon.peer :as peer]
            [itonami.otehon.market :as market]))

;; ---------------------------------------------------------------- canonical print

(defn- esc [s]
  (-> s (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")
      (str/replace "\n" "\\n") (str/replace "\t" "\\t")))

(defn canon
  "A deterministic EDN rendering: map entries sorted by their printed key,
  strings escaped the same way on every runner."
  [x]
  (cond
    (nil? x) "nil"
    (string? x) (str "\"" (esc x) "\"")
    (keyword? x) (str x)
    (boolean? x) (if x "true" "false")
    (map? x) (str "{" (str/join ", " (sort (map (fn [[k v]] (str (canon k) " " (canon v))) x))) "}")
    (set? x) (str "#{" (str/join " " (sort (map canon x))) "}")
    (sequential? x) (str "[" (str/join " " (map canon x)) "]")
    :else (str x)))

;; ---------------------------------------------------------------- profile access

(defn- index-of [s sub] (str/index-of s sub))

(defn- replace-first [s from to]
  (if-let [i (index-of s from)]
    (str (subs s 0 i) to (subs s (+ i (count from))))
    (throw (ex-info (str "edit anchor not found: " from) {:anchor from}))))

(defn- apply-edits
  "Wrap read-text with the case's text edits:
  [file from to] replaces the first `from`; [file :delete] removes the file;
  [file :set text] replaces it whole."
  [read-text edits]
  (reduce (fn [rt [f a b]]
            (fn [rel]
              (let [t (rt rel)]
                (if (not= rel f)
                  t
                  (cond (= a :delete) nil
                        (= a :set) b
                        (nil? t) nil
                        :else (replace-first t a b))))))
          read-text edits))

(defn- load-dir [dir read-text]
  (assoc (profile/load-profile {:name (hio/basename dir) :read-text read-text
                                :parse-edn hio/parse-edn :parse-json hio/parse-json})
         :home dir))

(defn check-result [prof]
  (let [ps (profile/check prof)]
    (merge {:profile (:name prof) :runtime (:runtime prof)} (profile/summary ps))))

;; ---------------------------------------------------------------- shared cases

;; Forward: the store-backed case ops live below with the other store
;; code (gather lives next to its CLI commands), while observe dispatches
;; every op from here. `declare` is the same idiom yaml.cljc uses.
(declare ops-pause ops-status ops-model ops-fallback ops-run ops-doctor ops-sessions)

(defn- subset?
  "Every key of `want` holds in `got` (nested maps compared the same way)."
  [want got]
  (every? (fn [[k v]]
            (let [g (get got k)]
              (if (and (map? v) (map? g)) (subset? v g) (= v g))))
          want))

(defn- includes-all? [msgs needles]
  (every? (fn [n] (some #(str/includes? % n) msgs)) needles))

(defn- observe
  "What one case's operation returns for the given read-text. The :pause
  and :status ops touch a real store instead: they seed a fresh scratch
  directory and run the same gather code the CLI commands use."
  [c dir read-text scratch]
  (case (:op c)
    :pause (ops-pause c scratch)
    :status (ops-status c scratch)
    :model (ops-model c scratch)
    :fallback (ops-fallback c scratch)
    :run (ops-run c scratch)
    :doctor (ops-doctor c scratch)
    :sessions (ops-sessions c scratch)
    :check (check-result (load-dir dir read-text))
    :dry-run (profile/dry-run (load-dir dir read-text) (:job c) {:capabilities (:capabilities c [])})
    :tick (let [prof (load-dir dir read-text)
                prof (if (contains? c :jobs) (assoc prof :jobs (:jobs c)) prof)]
            (tick/tick-plan (:jobs prof) (:now c)))
    :turn-gate (turn/turn-gate (load-dir dir read-text) (:prompt c "")
                               {:capabilities (:capabilities c [])
                                :toolsets (:toolsets c)
                                :tool-names (:tool-names c)
                                :known-catalog (when (contains? c :known-catalog)
                                                 (set (:known-catalog c)))
                                :platform (:platform c)})
    :run-plan (let [prof (load-dir dir read-text)
                    prof (if (contains? c :jobs) (assoc prof :jobs (:jobs c)) prof)]
                (run/run-plan prof (:job c)
                              {:capabilities (:capabilities c [])
                               :script (:script c)
                               :now (:now c {:iso "2026-11-08T00:00:00+09:00"
                                             :parts {:year 2026 :month 11 :day 8 :hour 0 :minute 0}})
                               :stamp (:stamp c "2026-11-08_00-00-00")
                               :run-at (:run-at c "2026-11-08 00:00:00")}))
    :store (store/plan-copy {:from (:source c {}) :to (:target c {})
                             :force? (:force? c) :direction (:direction c :export)
                             :from-name (:from-name c) :to-name (:to-name c)})
    :apply-order (run/apply-order {:job-id (:job-id c) :stamp (:stamp c)
                                  :status (:result-status c)
                                  :output-path (:output-path c)
                                  :record (:record c) :deliver (:deliver c)})
    :cas-sha {:sha (cas/sha256-hex (:bytes c []))}
    :cas-gc-plan (cas/gc-plan (:manifest c {}) (:inventory c {}))
    :manifest-rebuild (cas/manifest-rebuild (:local c {}) (:objects c []) (:remotes c []))
    :fleet-copies (peer/fleet-copies (:remotes c []))
    :fleet-plan (let [fc (peer/fleet-copies (:remotes c []))
                      plan (peer/replication-plan (:copies fc) (:desired c 2))]
                  (assoc plan :peers (:peers fc) :objects (:objects fc)))
    :retention-plan (cas/retention-plan (:manifest c {}) (:inventory c {})
                                      (:tombs c {}) (:today c "") (:days c 0))
    :cas-parse-int {:value (cas/parse-int (:text c ""))}
    :cas-manifest (let [files (:files c {})
                        tamper (:tamper c {})
                        absent (set (:absent c []))
                        m (reduce (fn [mm [rel bs]] (cas/manifest-add mm rel bs)) {} files)]
                    (cas/verify-manifest m (fn [rel] (if (contains? absent rel) nil (get (merge files tamper) rel)))))
    :peer-plan (peer/sync-plan (:local c {}) (:remote c {}))
    :peer-repair-plan (peer/repair-plan {:mismatch (:mismatch c [])
                                         :missing (:missing c [])}
                                        (:remotes c []))
    :market-match (market/match-orders (:asks c []) (:bids c []))
    :market-attest {:valid (market/valid-attestation? (:att c) (:today c ""))}
    :market-sign {:sig (:sig (market/sign-attestation (:att c {}) (:secret c "")))}
    :market-verify {:valid (market/valid-signature? (:att c {}) (:secrets c {}))}
    :market-match-signed (market/match-signed (:asks c []) (:bids c []) (:secrets c))
    :market-verify-asym (let [called (atom nil)
                              record (fn [p s k] (reset! called {:payload p :sig s :pub k}) true)
                              stub (case (:via c "deny")
                                     "allow" (fn [p s k] (record p s k) true)
                                     "echo" (fn [p s k] (record p s k) {:echoed [p s k]})
                                     "hmac" (fn [p s k] (record p s k)
                                              (market/verify-hmac-sig p s k))
                                     (fn [p s k] (record p s k) false))]
                          (assoc (market/verify-asymmetric (:att c {}) (:pubkeys c {})
                                                           stub (:today c ""))
                                 :called-with @called))
    :decide (let [p (load-dir dir read-text)]
              (yakuwari/decide (:yakuwari p) (get-in p [:manifest :authority]) (:capability c)))
    :additive (let [full (load-dir dir read-text)
                    bare (load-dir dir (profile/hermes-view read-text))]
                {:hermes-files-same (= (select-keys full [:config :profile-yaml :jobs])
                                       (select-keys bare [:config :profile-yaml :jobs]))
                 :runtime-full (:runtime full)
                 :runtime-bare (:runtime bare)})
    :yaml (try {:value (yaml/parse (:text c))}
               (catch #?(:lpy python/Exception :clj Exception :cljs :default) e
                 {:refused (or (:yaml/refused (ex-data e)) :error)}))
    :text (let [[f & args] (:call c)]
            {:value (case f
                      "trim" (text/trim (first args))
                      "triml" (text/triml (first args))
                      "trimr" (text/trimr (first args))
                      "blank?" (text/blank? (first args))
                      "starts-with?" (text/starts-with? (first args) (second args))
                      "ends-with?" (text/ends-with? (first args) (second args))
                      "includes?" (text/includes? (first args) (second args))
                      "index-of" (text/index-of (first args) (second args))
                      "lower-case" (text/lower-case (first args))
                      "join" (text/join (first args) (second args))
                      "split" (text/split (first args) (second args))
                      "split-lines" (text/split-lines (first args))
                      "replace" (text/replace (first args) (second args) (nth args 2))
                      (throw (ex-info (str "text: unknown fn " f) {:fn f})))})))

(defn- satisfied? [c got]
  (let [want (:expect c)]
    (and (subset? (dissoc want :messages-include :reason-in :needles) got)
         (includes-all? (:messages got) (:messages-include want []))
         (or (nil? (:reason-in want)) (contains? (:reason-in want) (:reason got))))))

;; ---------------------------------------------------------------- store ops
;; pause / resume / status touch a real store (unlike the fixture-text
;; cases above): each case gets a fresh directory under test/scratch and
;; seeds it with :seed {relative-path text}. The legacy commands never
;; refuse these paths (a missing file reads as absent, garbage as
;; not-live), so there are no negative controls here — the adversarial
;; seeds (garbage pid, unparsable peers, unnamable profile dirs) are the
;; coverage, asserted as positive cases.

(defn- scratch-case-dir [scratch c]
  (str scratch "/" (namespace (:id c)) "-" (name (:id c))))

(defn- seed-files! [dir seeds]
  (doseq [[rel text] seeds]
    (let [p (str dir "/" rel)]
      (hio/mkdirs! (hio/dirname p))
      (hio/write-text! p (str text)))))

(defn- read-trim [path]
  (let [t (when (hio/file? path) (hio/slurp-utf8 path))]
    (when (some? t) (str/trim t))))

(defn- read-edn-or [path dflt]
  (try (if (hio/file? path)
         (let [v (hio/parse-edn (hio/slurp-utf8 path))]
           (if (nil? v) dflt v))
         dflt)
       (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ dflt)))

(defn- read-json-or [path dflt]
  (try (if (hio/file? path)
         (let [v (hio/parse-json (hio/slurp-utf8 path))]
           (if (nil? v) dflt v))
         dflt)
       (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ dflt)))

(defn- listing-names [root]
  "Profile names under a store root: valid names whose directory exists,
  sorted so every runner lists the same order. Mirrors profile.cljk
  names-in (the legacy order is filesystem order, which differs per
  host readdir)."
  (let [d (str root "/profiles")]
    (vec (sort (filter #(and (status/valid-name? %)
                             (hio/dir? (str d "/" %)))
                       (hio/read-dir-names d))))))

(defn- gather-status
  "Read a store root into status/render-status data. Missing files read
  as absent (no profiles, not running, zero peers); an unparsable
  peers.edn reads nil (the legacy `?`)."
  [root]
  (let [marker (str root "/paused")
        paused (when (hio/file? marker) (pause/since-of (hio/slurp-utf8 marker)))
        names (listing-names root)
        rows (mapv (fn [n]
                     (let [home (str root "/profiles/" n)
                           m (read-edn-or (str home "/itonami.edn") nil)
                           jobs (read-json-or (str home "/cron/jobs.json") nil)
                           jobs (if (map? jobs) (get jobs "jobs") jobs)]
                       (status/profile-row n m (if (sequential? jobs) jobs []))))
                   names)
        tick (status/pid-state (read-trim (str root "/gateway.pid"))
                               (hio/pid-alive? (read-trim (str root "/gateway.pid"))))
        serve (status/pid-state (read-trim (str root "/gateway-serve.pid"))
                                (hio/pid-alive? (read-trim (str root "/gateway-serve.pid"))))
        peers (if (hio/file? (str root "/peers.edn"))
                  (try (count (:trusted (hio/parse-edn (hio/slurp-utf8 (str root "/peers.edn")))))
                       (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ nil))
                  0)]
    {:paused paused :profiles rows
     :gateway-tick tick :gateway-serve serve :peers-trusted peers}))

(defn- ops-pause [c scratch]
  (let [dir (scratch-case-dir scratch c)]
    (hio/mkdirs! dir)
    (seed-files! dir (:seed c {}))
    {:steps (mapv (fn [st]
                    (case (:action st)
                      "set" (do (hio/mkdirs! dir)
                                (hio/write-text! (pause/marker-path dir)
                                                 (pause/format-record (:at st "2026-11-08T00:00:00+09:00")
                                                                      (:reason st)))
                                {:action "set"
                                 :message (pause/pause-message (:reason st))
                                 :active true
                                 :record (pause/since-of (hio/slurp-utf8 (pause/marker-path dir)))})
                      "show" (let [m (pause/marker-path dir)
                                   on? (hio/file? m)]
                               {:action "show"
                                :active on?
                                :record (when on? (pause/since-of (hio/slurp-utf8 m)))})
                      "lift" (let [m (pause/marker-path dir)
                                   was (hio/file? m)]
                               (when was (hio/delete-file! m))
                               {:action "lift"
                                :message (pause/resume-message was)
                                :was-active was
                                :active (boolean (hio/file? m))})
                      {:action (:action st) :status :refused :reason "pause/unknown-action"}))
                  (:steps c []))}))

(defn- ops-status [c scratch]
  (let [dir (scratch-case-dir scratch c)]
    (hio/mkdirs! dir)
    (seed-files! dir (:seed c {}))
    (let [r (gather-status dir)]
      (assoc r :lines (status/render-status r)))))

;; ---------------------------------------------------------------- model ops

(defn- model-flags
  "Split model/fallback tail args into positionals, the -m/--model run
  override and --provider. Value flags consume the next token verbatim
  (even a dash-prefixed one, even when missing — the legacy parse-args
  rule, so a bare --provider refuses the same way). `--model=` /
  `--provider=` spellings also work."
  [args]
  (loop [as (seq args) pos [] run-model nil provider {:present false :value nil}]
    (cond (nil? as) {:pos pos :run-model run-model :provider provider}
          (or (= "-m" (first as)) (= "--model" (first as)))
          (if (second as)
            (recur (nnext as) pos (second as) provider)
            (recur (next as) pos run-model provider))
          (text/starts-with? (first as) "--model=")
          (recur (next as) pos (subs (first as) 8) provider)
          (= "--provider" (first as))
          (if (second as)
            (recur (nnext as) pos run-model {:present true :value (second as)})
            (recur (next as) pos run-model {:present true :value nil}))
          (text/starts-with? (first as) "--provider=")
          (recur (next as) pos run-model {:present true :value (subs (first as) 11)})
          :else (recur (next as) (conj pos (first as)) run-model provider))))

(defn- resolve-in [dir flags]
  (let [prof (load-dir dir (hio/read-text-fn dir))]
    (model/resolution (:config prof)
                      {:model (:run-model flags) :provider (get-in flags [:provider :value])}
                      (hio/env "ITONAMI_PEER_PORT"))))

(defn- model-input
  "Case input for the :model / :fallback ops: raw CLI tail tokens in
  :args (exercising the flag parser), or a structured :request (which can
  also carry values tokens cannot, like a non-string provider)."
  [c]
  (if (contains? c :args)
    (model-flags (:args c))
    {:pos (get-in c [:request :pos] [])
     :run-model (get-in c [:request :run-model])
     :provider (get-in c [:request :provider] {:present false})}))

(defn- ops-model [c scratch]
  (let [dir (scratch-case-dir scratch c)]
    (hio/mkdirs! dir)
    (seed-files! dir (:seed c {}))
    (let [{:keys [pos run-model provider]} (model-input c)
          req (model/model-request pos run-model provider)]
      (if (:error req)
        req
        (if-let [s (:set req)]
          (let [p (str dir "/config.yaml")
                cur (when (hio/file? p) (hio/slurp-utf8 p))]
            (try
              (let [written (model/set-model-text (or cur "") (:model s) (:provider s))]
                (hio/mkdirs! dir)
                (hio/replace-file! p written)
                (let [r (resolve-in dir {:run-model run-model :provider provider})]
                  {:written written :lines (model/model-lines r true)}))
              (catch #?(:lpy python/Exception :clj Exception :cljs :default) e
                {:status :refused :reason (ex-message e)})))
          (let [r (resolve-in dir {:run-model run-model :provider provider})]
            {:lines (model/model-lines r false)}))))))

(defn- ops-fallback [c scratch]
  (let [dir (scratch-case-dir scratch c)]
    (hio/mkdirs! dir)
    (seed-files! dir (:seed c {}))
    (let [{:keys [pos run-model provider]} (model-input c)]
      (if (seq pos)
        {:error "usage: itonami-agent fallback [--profile P] [-m model]"}
        (let [r (resolve-in dir {:run-model run-model :provider provider})]
          {:lines (model/fallback-lines r)})))))

;; ---------------------------------------------------------------- run execution

(defn- safe-parse [s]
  (try (hio/parse-json (str s))
       (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ nil)))

(defn- jobs-file [home] (str home "/cron/jobs.json"))

(defn- read-jobs-vec [home]
  (try (let [p (jobs-file home)]
         (if (hio/file? p)
           (vec (cron/jobs-of (hio/parse-json (hio/slurp-utf8 p))))
           []))
       (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ [])))

(defn- update-jobs-doc!
  "Read/modify/write cron/jobs.json preserving top-level extension fields
  and stamping updated_at — the legacy write-jobs-unlocked! contract
  (formatting stays compact; parsed content is what both sides read).
  `f` takes the doc and its jobs vector and returns the updated doc
  (with \"jobs\" replaced)."
  [home micros f]
  (let [doc (try (let [v (when (hio/file? (jobs-file home))
                              (hio/parse-json (hio/slurp-utf8 (jobs-file home))))]
                      (if (map? v) v {}))
                    (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ {}))
        jobs (vec (cron/jobs-of doc))]
    (hio/mkdirs! (str home "/cron"))
    (hio/replace-file! (jobs-file home)
                       (hio/json-str (assoc (f doc jobs) "updated_at" micros)))))

(defn- hy-deps
  "Resolve Hy's site-packages for script runs. `env-map` is the merged
  profile+host env. Verbatim from the cron.cljk resolution, minus the
  memo (one CLI run resolves once); POSIX separators only — Otehon has
  no Windows runner."
  [prof env-map]
  (let [configured (or (get env-map "KOTOBA_HY_DEPS") (hio/env "KOTOBA_HY_DEPS"))
        paths (when (string? configured) (remove str/blank? (str/split configured #":")))
        valid? (and (seq paths)
                    (some #(hio/file? (str % "/hy/__init__.py")) paths)
                    (some #(hio/file? (str % "/funcparserlib/__init__.py")) paths))
        home (:home prof)
        hermes-root (or (some-> (hio/env "ITONAMI_HERMES_ROOT") (exec/expand-home (hio/env "HOME")))
                        (when (and (string? home) (str/includes? home "/.hermes/profiles/"))
                          (hio/dirname (hio/dirname home))))]
    (or (when valid? (str/join ":" paths))
        (when hermes-root
          (let [installs (str hermes-root "/installs")
                cands (for [install (hio/read-dir-names installs)
                            :let [envs (str installs "/" install "/environments")]
                            environment (hio/read-dir-names envs)
                            :let [libs (str envs "/" environment "/venv/lib")]
                            py-version (hio/read-dir-names libs)
                            :let [sp (str libs "/" py-version "/site-packages")]
                            :when (and (hio/file? (str sp "/hy/__init__.py"))
                                       (hio/file? (str sp "/funcparserlib/__init__.py")))]
                        sp)]
            (when (seq cands) (str/join ":" cands)))))))

(defn- script-env
  [prof home script-ref]
  (let [base (merge (:env prof {}) {"HERMES_HOME" home "ITONAMI_PROFILE_HOME" home})]
    (if (boolean (re-find #"\.hy$" (str/lower-case (str script-ref))))
      (if-let [deps (hy-deps prof base)]
        (assoc base "KOTOBA_HY_DEPS" deps)
        base)
      base)))

(defn- spawn-script!
  "Run one job script ref; always returns {:ok? :output :wake?}. The wake
  flag is true unless the last output line parses as wakeAgent false —
  the run-plan contract (a failed gate still skips the turn). Paths the
  child sees are absolute: it resolves relatives against its own cwd."
  [prof job script-ref]
  (let [home (hio/abs-path (:home prof))
        scripts (str home "/scripts")
        check (exec/check-script scripts script-ref {:symlink? hio/symlink? :file? hio/file?})]
    (if (not= :ok (:status check))
      {:ok? false :output (:reason check) :wake? true}
      (let [env (script-env prof home script-ref)
            workdir (exec/expand-home (or (get job "workdir") scripts) (hio/env "HOME"))
            argv (exec/script-argv script-ref (:path check)
                                   [(get job "interpreter")
                                    (get-in prof [:config "cron" "script_python"])
                                    (get env "ITONAMI_PYTHON")
                                    (get env "HERMES_PYTHON")])
            res (hio/spawn-sync! argv {:cwd workdir
                                       :timeout-ms (* 1000 (exec/script-timeout-s (:config prof) env))
                                       :env env})
            [ok? out] (exec/script-result res)]
        {:ok? ok? :output out :wake? (not (exec/wake-false? out safe-parse))}))))

(defn- current-row [home job-id]
  (first (filter #(= job-id (get % "id")) (read-jobs-vec home))))

(defn- append-error-section
  "A failed run's doc appends the raw error under `## Error`, and the job
  row records the full text as last_error. Verbatim from run-job-core."
  [doc err-text]
  (str doc "\n## Error\n\n```\n" err-text "\n```\n"))

(defn- apply-and-save!
  "Write the output doc, prune past 50, and apply the finished-run record
  to the CURRENT jobs.json row. Returns the output path."
  [home job-id doc failed? error delivery-error stamp micros]
  (let [outdir (str home "/cron/output/" job-id)
        f (str outdir "/" stamp ".md")]
    (hio/mkdirs! outdir)
    (hio/replace-file! f doc)
    (doseq [old (exec/prune-outputs (hio/read-dir-names outdir))]
      (hio/delete-file! (str outdir "/" old)))
    (let [jobs (read-jobs-vec home)
          cur (first (filter #(= job-id (get % "id")) jobs))]
      (when cur
        (update-jobs-doc! home micros
          (fn [doc js] (assoc doc "jobs" (exec/jobs-with js (exec/apply-record cur micros failed? error delivery-error)))))))
    f))

(defn- run-monitor!
  "Evaluate a job's monitor_script (monitor_url refuses: fetching needs
  the network). Applies the snapshot + monitor_state writes on change. Returns nil
  (no monitor), {:refused ...}, or the exec/monitor-decide verdict with
  the script result attached."
  [prof job micros]
  (let [ms (some-> (get job "monitor_script") str/trim not-empty)
        url (some-> (get job "monitor_url") str/trim not-empty)]
    (cond
      (some? ms)
      (let [home (:home prof)
            id (get job "id")
            s (spawn-script! prof job ms)
            digest (hio/sha256-utf8 (:output s))
            snap (str home "/cron/output/" id "/monitor_last_output.txt")
            old (when (hio/file? snap) (hio/slurp-utf8 snap))
            state (get (current-row home id) "monitor_state")
            d (exec/monitor-decide {:ok? (:ok? s) :output (:output s)}
                                   {:digest digest :old-output old :old-hash (get state "last_output_hash")}
                                   micros)]
        (when (:changed? d)
          (hio/mkdirs! (str home "/cron/output/" id))
          (hio/replace-file! snap (:snapshot d))
          (let [jobs (read-jobs-vec home)
                cur (first (filter #(= id (get % "id")) jobs))]
            (when cur
              (update-jobs-doc! home micros
                (fn [doc js] (assoc doc "jobs" (exec/jobs-with js (assoc cur "monitor_state" (:state d)))))))))
        (assoc d :script s))
      (some? url) {:refused "monitor/monitor-url-unavailable"}
      :else nil)))

(defn- exec-plan [prof job-ref capabilities now stamp run-at script monitor-context]
  (run/run-plan prof job-ref {:capabilities capabilities :script script :now now
                              :stamp stamp :run-at run-at
                              :notepad nil :linked nil :monitor-context monitor-context}))

(defn- apply-turn!
  "Apply a finished agent turn: doc, jobs.json record and audit rows.
  Verbatim from run-job-core. `turn` is the executor result; `deliver`
  emulates the delivery adapter (\"ok\" or an error string; nil means no
  adapter is configured). The executions ledger and the delivery queue
  stay slice S5, so only their observable inputs (status, messages, rows)
  are produced here."
  [home job prompt turn deliver micros stamp run-at]
  (let [started (hio/now-ms)
        {:keys [final failed? silent? err-text]} (exec/turn-outcome turn)
        response (if failed?
                   (str final (when err-text (str "\n\n## Error\n\n```\n" err-text "\n```")))
                   final)
        doc (run/output-doc job {:prompt prompt :response response :failed? failed?
                                 :status-line (exec/turn-status-line turn)} run-at)
        target (exec/deliver-target job failed?)
        delivery-error (when-not silent? (exec/turn-delivery-error target deliver))
        f (apply-and-save! home (get job "id") doc failed? err-text delivery-error stamp micros)
        status (exec/turn-status failed? delivery-error silent?)
        execution-id (hio/random-hex 16)
        usage (exec/usage-entry {:ts micros :job-id (get job "id") :fire-id (hio/random-hex 8)
                                 :prompt-tokens (get-in turn [:usage "prompt_tokens"])
                                 :completion-tokens (get-in turn [:usage "completion_tokens"])
                                 :total-tokens (get-in turn [:usage "total_tokens"])
                                 :silent? silent? :deliver-target target
                                 :delivery-error delivery-error :model (:model turn)
                                 :duration-ms (- (hio/now-ms) started) :error err-text})
        run-row (exec/run-entry {:ts micros :job-id (get job "id") :execution-id execution-id
                                 :manual? true :status status :exit-reason (:exit-reason turn)
                                 :turns (:turns turn) :provider (:provider turn) :model (:model turn)
                                 :peer (:peer turn) :receipt (:receipt turn) :placement (:placement turn)
                                 :attempts (:attempts turn) :output-file f})]
    (hio/append-text! (str home "/cron/usage_audit.jsonl") (str (hio/json-str usage) "\n"))
    (hio/append-text! (str home "/cron/itonami-runs.jsonl") (str (hio/json-str run-row) "\n"))
    {:exit (if (= "error" status) 1 0)
     :lines [(str "ran " (get job "id") ": " status)]
     :detail {:status status :output-file f :output-doc doc
              :job-after (current-row home (get job "id"))
              :usage-row usage :run-row run-row}}))

(defn- execute-run
  "Run one job's host side now. `now-map` = {:iso :parts :ms}; `opts`
  carries the test-only turn injection {:turn :deliver} (the product
  command has no executor yet, so a planned turn without one stays
  pending). Returns {:exit :lines :detail}: detail carries :status plus
  the saved artifacts (output-doc, job-after, snapshot, monitor-state,
  audit rows) for the shared cases."
  [prof job-ref capabilities now-map opts]
  (let [home (:home prof)
        iso (:iso now-map)
        now {:iso iso :parts (:parts now-map)}
        stamp (exec/stamp-of iso)
        run-at (exec/human-of iso)
        micros (exec/micros-of iso (:ms now-map))
        plan (exec-plan prof job-ref capabilities now stamp run-at nil nil)]
    (cond
      (= :refused (:status plan))
      {:exit 1 :lines [(str "refused: " (:reason plan))] :detail {:status "refused" :reason (:reason plan)}}

      (= :held (:status plan))
      {:exit 2 :lines [(str "held: " (:reason plan))] :detail {:status "held" :reason (:reason plan)}}

      (contains? #{:silent :ok} (:status plan))
      (let [f (apply-and-save! home (get-in plan [:job :id]) (:output-doc plan) false nil nil stamp micros)
            id (get-in plan [:job :id])]
        {:exit 0
         :lines [(str "ran " id ": " (name (:status plan)))]
         :detail {:status (name (:status plan)) :output-file f
                 :output-doc (:output-doc plan) :job-after (current-row home id)}})

      :else
      (let [job (cron/find-job (:jobs prof) job-ref)
            has-mon (or (some-> (get job "monitor_script") str/trim not-empty)
                        (some-> (get job "monitor_url") str/trim not-empty))
            mon (when (and (some? job) (some? has-mon)) (run-monitor! prof job micros))]
        (cond
          (some? (:refused mon))
          {:exit 1 :lines [(:refused mon)] :detail {:status "refused" :reason (:refused mon)}}

          (and (some? mon) (false? (:ok? mon)))
          (let [msg (str "Monitor source failed: " (:error mon))
                doc (append-error-section
                     (run/output-doc job {:prompt (or (get job "prompt") "")
                                          :response (str "[CRON_FAILURE] " msg)
                                          :failed? true} run-at)
                     msg)
                f (apply-and-save! home (get job "id") doc true msg nil stamp micros)]
            {:exit 1
             :lines [(str "ran " (get job "id") ": error")]
             :detail {:status "error" :output-file f :output-doc doc
                      :job-after (current-row home (get job "id"))}})

          (and (some? mon) (false? (:changed? mon)))
          (let [doc (run/output-doc job {:prompt "" :response "[SILENT]"
                                         :status-line "**Status:** monitor output unchanged"} run-at)
                f (apply-and-save! home (get job "id") doc false nil nil stamp micros)]
            {:exit 0
             :lines [(str "ran " (get job "id") ": silent")]
             :detail {:status "silent" :output-file f :output-doc doc
                      :job-after (current-row home (get job "id"))}})

          :else
          (let [plan2 (if (and (some? mon) (:changed? mon))
                        (exec-plan prof job-ref capabilities now stamp run-at nil (:context mon))
                        plan)
                req (first (:exec plan2))]
            (cond
              (contains? #{:silent :ok} (:status plan2))
              (let [failed? (= "script_error" (:exit-reason plan2))
                    err-text (when failed?
                               (str/replace-first (str (:final plan2))
                                                  (str run/failure-token " ") ""))
                    doc (if failed? (append-error-section (:output-doc plan2) err-text)
                            (:output-doc plan2))
                    f (apply-and-save! home (get-in plan2 [:job :id]) doc
                                       failed? err-text nil stamp micros)
                    id (get-in plan2 [:job :id])]
                {:exit (if failed? 1 0)
                 :lines [(str "ran " id ": " (if failed? "error" (name (:status plan2))))]
                 :detail {:status (if failed? "error" (name (:status plan2)))
                          :output-file f :output-doc doc
                          :job-after (current-row home id)
                          :snapshot (:snapshot mon) :monitor-state (:state mon)}})

              (and (= :planned (:status plan2)) (= "script" (:id req)))
              (let [script (spawn-script! prof (cron/find-job (:jobs prof) job-ref)
                                          (get-in plan2 [:job :script]))
                    plan3 (exec-plan prof job-ref capabilities now stamp run-at script
                                     (when (and (some? mon) (:changed? mon)) (:context mon)))]
                (cond
                  (contains? #{:silent :ok} (:status plan3))
                  (let [failed? (= "script_error" (:exit-reason plan3))
                        err-text (when failed?
                                   (str/replace-first (str (:final plan3))
                                                      (str run/failure-token " ") ""))
                        doc (if failed? (append-error-section (:output-doc plan3) err-text)
                                (:output-doc plan3))
                        f (apply-and-save! home (get-in plan3 [:job :id]) doc
                                           failed? err-text nil stamp micros)
                        id (get-in plan3 [:job :id])]
                    {:exit (if failed? 1 0)
                     :lines [(str "ran " id ": " (if failed? "error" (name (:status plan3))))]
                     :detail {:status (if failed? "error" (name (:status plan3)))
                              :output-file f :output-doc doc
                              :job-after (current-row home id)
                              :snapshot (:snapshot mon) :monitor-state (:state mon)}})

                  (= :planned (:status plan3))
                  (if-let [turn (:turn opts)]
                    (let [r (apply-turn! home job (:prompt plan3)
                                         turn (:deliver opts) micros stamp run-at)]
                      (update r :detail assoc :snapshot (:snapshot mon) :monitor-state (:state mon)))
                    {:exit 3
                     :lines [(str "inference pending for " (get-in plan3 [:job :id])) (canon plan3)]
                     :detail {:status "pending" :plan (canon plan3)
                              :snapshot (:snapshot mon) :monitor-state (:state mon)}})

                  :else
                  {:exit (if (= :held (:status plan3)) 2 1)
                   :lines [(str (name (:status plan3)) ": " (:reason plan3))]
                   :detail {:status (name (:status plan3)) :reason (:reason plan3)}}))

              (= :planned (:status plan2))
              (if-let [turn (:turn opts)]
                (let [r (apply-turn! home job (:prompt plan2)
                                     turn (:deliver opts) micros stamp run-at)]
                  (update r :detail assoc :snapshot (:snapshot mon) :monitor-state (:state mon)))
                {:exit 3
                 :lines [(str "inference pending for " (get-in plan2 [:job :id])) (canon plan2)]
                 :detail {:status "pending" :plan (canon plan2)
                          :snapshot (:snapshot mon) :monitor-state (:state mon)}})

              :else
              {:exit (if (= :held (:status plan2)) 2 1)
               :lines [(str (name (:status plan2)) ": " (:reason plan2))]
               :detail {:status (name (:status plan2)) :reason (:reason plan2)}})))))))

(defn- host-now []
  (let [np (hio/now-parts)]
    {:iso (:iso np) :parts (:parts np) :ms (hio/now-ms)}))

(defn- ops-run [c scratch]
  (let [dir (scratch-case-dir scratch c)]
    (hio/mkdirs! dir)
    (seed-files! dir (:seed c {}))
    (let [req (:request c {})
          prof (load-dir dir (hio/read-text-fn dir))
          r (execute-run prof (:job req) (vec (:capabilities req []))
                         (merge {:iso "2026-11-08T00:00:00+09:00"
                                 :ms 0
                                 :parts {:year 2026 :month 11 :day 8 :hour 0 :minute 0}}
                                (:now req))
                         {:turn (:turn req) :deliver (:deliver req)})
          doc (or (:output-doc (:detail r)) "")
          jobs-doc (try (when (hio/file? (jobs-file dir))
                          (hio/parse-json (hio/slurp-utf8 (jobs-file dir))))
                        (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ nil))
          snap-path (str dir "/cron/output/" (:job req) "/monitor_last_output.txt")]
      (merge {:exit (:exit r) :lines (:lines r) :first-line (first (:lines r))
              :messages (:lines r)}
             (:detail r)
             {:jobs-doc jobs-doc
              :snapshot-file (when (hio/file? snap-path) (hio/slurp-utf8 snap-path))
              :doc-has (into {} (map (fn [n] [n (boolean (str/includes? doc (str n)))])
                                     (:needles (:expect c []))))}))))

(defn- doctor-for
  "Doctor findings for one profile: reads, checks, renders. `store` is
  the store root (home = store/profiles/<name>); `env` is the host env
  map for token checks. Shared by the :doctor op and the product command."
  [store name env model-override]
  (let [home (str store "/profiles/" name)
        prof (assoc (profile/load-profile {:name name :read-text (hio/read-text-fn home)
                                            :parse-edn hio/parse-edn :parse-json hio/parse-json})
                    :home home)
        jobs-path (str home "/cron/jobs.json")
        raw (when (hio/file? jobs-path) (hio/slurp-utf8 jobs-path))
        parsed (when (some? raw)
                 (try (hio/parse-json raw)
                      (catch #?(:lpy python/Exception :clj Exception :cljs :default) e
                        {:error (ex-message e)})))]
    (if (:error parsed)
      (let [msg (str (:error parsed))]
        {:exit 1
         :lines [(str "profile-load-failed: " (subs msg 0 (min 200 (count msg))))
                 "1 finding(s)"]})
      (let [marker (str store "/paused")
            paused (when (hio/file? marker) (pause/since-of (hio/slurp-utf8 marker)))
            findings (doctor/profile-findings
                      {:config (:config prof) :home home :jobs (:jobs prof)}
                      {:env env :model-override model-override
                       :paused-record paused
                       :state-db? (hio/file? (str home "/state.db"))
                       :hy-deps (hy-deps prof env)
                       :probes {:file? hio/file? :symlink? hio/symlink?}})]
        (doctor/render findings)))))

(defn- ops-doctor [c scratch]
  (let [dir (scratch-case-dir scratch c)]
    (hio/mkdirs! dir)
    (seed-files! dir (:seed c {}))
    (let [req (:request c {})
          r (doctor-for dir (or (:profile req) "default")
                        (or (:env req) (hio/all-env))
                        (:model req))]
      (assoc r :messages (:lines r)))))

(defn- sessions-for
  "One `sessions` invocation against a store. `sub`/`id`/`limit-raw`
  mirror the legacy CLI args. Shared by the :sessions op and the product
  command."
  [store name sub id limit-raw]
  (let [home (str store "/profiles/" name)
        db-path (str home "/state.db")
        limit (sessions/bounded-limit limit-raw 200)]
    (if (and (some? limit-raw) (nil? limit))
      {:exit 2 :lines ["sessions: --limit must be a positive integer"]}
      (let [limit (or limit 20)]
        (case (str sub)
          ("" "list")
          (let [res (sessions/browse db-path limit)]
            (if (:error res)
              {:exit 1 :lines [(str "sessions: " (:error res))]}
              {:exit 0 :lines (sessions/browse-lines (:rows res))}))
          "show"
          (if (nil? id)
            {:exit 2 :lines ["sessions show: a session id is required"]}
            (let [res (sessions/latest-messages db-path id (min limit 50))]
              (if (:error res)
                {:exit 1 :lines [(str "sessions show: " (:error res))]}
                (if (seq (:rows res))
                  {:exit 0 :lines (sessions/show-lines (:rows res))}
                  {:exit 1 :lines [(str "session not found: " id)]}))))
          "resume"
          {:exit 2 :lines ["sessions resume: not implemented yet (lands with history re-entry)"]}
          {:exit 2 :lines ["usage: itonami-agent sessions [list] [--limit N] | show <id> | resume <id>"]})))))

(defn- ops-sessions [c scratch]
  (let [dir (scratch-case-dir scratch c)]
    (hio/mkdirs! dir)
    (seed-files! dir (:seed c {}))
    (let [req (:request c {})
          home (str dir "/profiles/" (or (:profile req) "default"))]
      (when (seq (:setup-sql req))
        (hio/sqlite-exec! (str home "/state.db")
                          (str/join ";\n" (concat ["DROP TABLE IF EXISTS messages"
                                                   "DROP TABLE IF EXISTS sessions"]
                                                  (:setup-sql req)))))
      (let [r (sessions-for dir (or (:profile req) "default")
                            (:sub req) (:id req) (:limit req))]
        (assoc r :messages (:lines r))))))

(defn run-case [c fixtures scratch]
  (let [dir (when (:profile c) (str fixtures "/" (:profile c)))
        base (if dir (hio/read-text-fn dir) (constantly nil))
        edited (apply-edits base (:edit c []))
        got (try (observe c dir edited scratch)
                 (catch #?(:lpy python/Exception :clj Exception :cljs :default) e
                   {:crash (str (ex-message e))}))
        ok? (satisfied? c got)
        ;; negative control: the edit, not the fixture, must cause the rejection
        control-ok? (if (and (:negative c) (seq (:edit c)))
                      (not (satisfied? c (try (observe c dir base scratch)
                                              (catch #?(:lpy python/Exception :clj Exception :cljs :default) e
                                                {:crash (str (ex-message e))}))))
                      true)]
    {:id (:id c) :negative (boolean (:negative c)) :pass (and ok? control-ok?)
     :got (when-not (and ok? control-ok?) got)
     :control-failed (when-not control-ok? true)}))

(defn run-cases [path]
  (let [doc (hio/parse-edn (hio/slurp-utf8 path))
        fixtures (str (hio/dirname path) "/" (:fixtures doc "fixtures"))
        scratch (str (hio/dirname path) "/scratch")]
    (hio/mkdirs! scratch)
    (let [results (mapv #(run-case % fixtures scratch) (:cases doc))]
    {:runner (hio/runner-version)
     :cases (count results)
     :pass (count (filter :pass results))
     :fail (vec (for [r results :when (not (:pass r))] (dissoc r :pass)))
     :negative-controls (count (filter :negative results))
     :ids (mapv #(str (:id %) (if (:pass %) " ok" " FAIL")) results)})))

;; ---------------------------------------------------------------- main

(defn- capabilities-arg [args]
  (loop [as (seq args) caps []]
    (cond (nil? as) caps
          (and (= "--capability" (first as)) (second as))
          (recur (nnext as) (conj caps (keyword (second as))))
          :else (recur (next as) caps))))

(defn- store-flags [args]
  (loop [as (seq args) m {:pos []}]
    (cond (nil? as) m
          (= "--profile" (first as))
          (if (second as) (recur (nnext as) (assoc m :profile (second as))) (recur (next as) m))
          (= "--limit" (first as))
          (if (second as) (recur (nnext as) (assoc m :limit (second as))) (recur (next as) m))
          :else (recur (next as) (update m :pos conj (first as))))))

(defn- usage []
  (println "usage: itonami-otehon check <dir> | dry-run <dir> <job> [--capability c]... | tick <dir> | turn-gate <dir> <prompt> [--capability c]... | run-plan <dir> <job> [--capability c]... [--script-file <path> | --script-error <text>] [--script-no-wake] | store-plan --direction import|export --from <dir> --to <dir> [--force] | pause [reason]... | resume | status | model <dir> [NAME] [--provider P] [--model M] | fallback <dir> [-m M] [--provider P] | run-exec <dir> <job> [--capability c]... | doctor [--profile P] | sessions [list|show <id>|resume] [--profile P] [--limit N] | cas-put <store> <file>... | cas-get <store> <sha> <out> | cas-verify <store> | cas-gc <store> [--apply] [--retain-days N] | cas-reconcile <store> <peerdir>... [--as <node> --registry <reg.edn>] | fleet-status <peerdir>... [--as <node> --registry <reg.edn>] [--copies N] | peer-push <store> <peerdir> [--as <node> --registry <reg.edn>] | peer-pull <store> <peerdir> [--as <node> --registry <reg.edn>] | peer-repair <store> <peerdir>... [--as <node> --registry <reg.edn>] | market-match <asks.edn> <bids.edn> [--today YYYY-MM-DD] [--secrets <secrets.edn>] | market-sign <att.edn> --issuer-secret <secret> | market-verify <att.edn> --suite-secret <secret> [--today YYYY-MM-DD] | test <cases.edn>")
  64)

(defn- exit-for
  "0 ok (and planned / silent plans), 2 held, 1 refused."
  [r]
  (case (:status r) :ok 0 :planned 0 :silent 0 :held 2 1))

(defn- script-arg [args]
  (loop [as (seq args) file nil err nil wake? true]
    (cond (nil? as) (cond file {:ok? true :output (hio/slurp-utf8 file) :wake? wake?}
                           err {:ok? false :output err :wake? wake?}
                           :else nil)
          (= "--script-file" (first as))
          (if (second as) (recur (nnext as) (second as) err wake?) (recur (next as) file err wake?))
          (= "--script-error" (first as))
          (if (second as) (recur (nnext as) file (second as) wake?) (recur (next as) file err wake?))
          (= "--script-no-wake" (first as)) (recur (next as) file err false)
          :else (recur (next as) file err wake?))))

(defn- store-listing
  "The store-relative {path text} listing of a profile directory: the
  present definition files plus everything under scripts/ and skills/."
  [dir]
  (let [rt (hio/read-text-fn dir)
        base (into {} (keep (fn [f] (let [t (rt f)] (when (some? t) [f t])))
                         store/definition-files))
        under (fn [d]
                (let [rtf (hio/read-text-fn (str dir "/" d))]
                  (into {} (keep (fn [rel] (let [t (rtf rel)] (when (some? t) [(str d "/" rel) t])))
                                 (store/collect-files hio/read-dir-names hio/dir? (str dir "/" d))))))]
    (merge base (under "scripts") (under "skills"))))

(defn- store-args [args]
  (loop [as (seq args) m {}]
    (cond (nil? as) m
          (= "--direction" (first as)) (recur (nnext as) (assoc m :direction (keyword (second as))))
          (= "--from" (first as)) (recur (nnext as) (assoc m :from (second as)))
          (= "--to" (first as)) (recur (nnext as) (assoc m :to (second as)))
          (= "--force" (first as)) (recur (next as) (assoc m :force? true))
          :else (recur (next as) m))))

;; ---------------------------------------------------------------- cas store on disk

(defn- cas-manifest-path [store] (str store "/manifest.edn"))

(defn- read-edn-file [path]
  (when (hio/file? path) (hio/parse-edn (hio/slurp-utf8 path))))

(defn- kw-keys
  "Keywordize a map's string keys (hand-written EDN); keywords pass
  through. Applied at the CLI boundary so pure namespaces always see
  keyword keys while files stay hand-writable."
  [m]
  (into {} (map (fn [[k v]] [(if (string? k) (keyword k) k) v]) m)))

(defn- cas-read-manifest [store]
  (let [m (or (read-edn-file (cas-manifest-path store)) {})]
    (into {} (map (fn [[rel e]] [rel (if (map? e) (kw-keys e) e)]) m))))

(defn- cas-write-manifest! [store m]
  (hio/mkdirs! store)
  (hio/write-latin1! (cas-manifest-path store) (canon m)))

(defn- cas-object-path [store sha]
  (str store "/objects/" (cas/object-rel sha)))

(defn- cas-rel-name [path]
  (last (str/split path #"/")))

(defn- cas-put [store files]
  (let [inputs (mapv (fn [f] [f (hio/read-latin1 f)]) files)
        missing (vec (sort (map first (filter #(nil? (second %)) inputs))))
        names (mapv (comp cas-rel-name first) inputs)
        dupes (vec (sort (for [[n c] (frequencies names) :when (< 1 c)] n)))]
    (cond (seq missing)
          {:status :refused :reason "cas/file-missing" :missing missing}
          (seq dupes)
          {:status :refused :reason "cas/duplicate-name" :names dupes}
          :else
          (let [items (mapv (fn [[f s]] (assoc (cas/ingest (cas-rel-name f) (hio/bytes-of s)) :text s)) inputs)]
            (doseq [{:keys [sha text]} items]
              (hio/mkdirs! (str store "/objects/" (subs sha 0 2)))
              (hio/write-latin1! (cas-object-path store sha) text))
            (let [m (reduce (fn [m {:keys [rel sha size]}] (assoc m rel {:sha sha :size size}))
                            (cas-read-manifest store) items)]
              (cas-write-manifest! store m)
              {:status :ok :store store :stored (count items) :manifest (count m)})))))

(defn- cas-get [store sha out]
  (let [s (hio/read-latin1 (cas-object-path store sha))]
    (cond (nil? s)
          {:status :refused :reason "cas/object-missing" :sha sha}
          (not= sha (cas/sha256-hex (hio/bytes-of s)))
          {:status :refused :reason "cas/object-corrupt" :sha sha}
          :else
          (do (hio/write-latin1! out s)
              {:status :ok :sha sha :size (count (hio/bytes-of s))}))))

(defn- cas-verify [store]
  (let [m (cas-read-manifest store)
        r (cas/verify-manifest m (fn [rel] (when-let [e (get m rel)]
                                             (when-let [s (hio/read-latin1 (cas-object-path store (:sha e)))]
                                               (hio/bytes-of s)))))
        ok? (and (empty? (:mismatch r)) (empty? (:missing r)))]
    (assoc r :status (if ok? :ok :refused)
           :reason (when-not ok? "cas/verify-failed"))))

(defn- cas-inventory
  "Physical objects/ content as {sha size}. Missing dirs read empty;
  files that vanish mid-listing are skipped (the next gc sees them)."
  [store]
  (let [top (str store "/objects")]
    (into {} (mapcat (fn [shard]
                       (let [d (str top "/" shard)]
                         (keep (fn [f] (when-let [sz (hio/file-size (str d "/" f))]
                                         [(str shard f) sz]))
                               (hio/read-dir-names d))))
                     (hio/read-dir-names top)))))

(defn- cas-tombs-path [store] (str store "/tombstones.edn"))

(defn- cas-read-tombs
  "tombstones.edn {sha {:deleted-at :size}}, {} when absent. Hand-written
  files get the same kw-keys normalization as manifests."
  [store]
  (let [m (or (read-edn-file (cas-tombs-path store)) {})]
    (into {} (map (fn [[sha e]] [sha (if (map? e) (kw-keys e) e)]) m))))

(defn- cas-gc
  "Drop objects no manifest references. Dry-run by default (prints the
  plan, deletes nothing); --apply deletes and reports :deleted. The
  manifest is never rewritten; a failed delete refuses the whole gc.
  With --retain-days N, unreferenced objects rest in tombstones.edn
  for N days first: grace-expired ones are deleted, re-referenced ones
  revived, stale tomb entries cleared. The tombstone file is the audit
  trail of every deferred and completed drop."
  [store {:keys [apply? retain-days]} today]
  (let [m (cas-read-manifest store)
        inv (cas-inventory store)]
    (if (nil? retain-days)
      (let [plan (cas/gc-plan m inv)]
        (if-not apply?
          (assoc plan :status :ok :applied false)
          (let [paths (mapv #(str store "/objects/" (cas/object-rel %)) (:drop plan))
                ok? (mapv hio/delete-file! paths)
                failed (mapv first (filter (complement second) (map vector (:drop plan) ok?)))]
            (if (seq failed)
              {:status :refused :reason "cas/gc-failed" :failed failed}
              (assoc plan :status :ok :applied true :deleted (count (:drop plan)))))))
      (let [tombs (cas-read-tombs store)
            plan (cas/retention-plan m inv tombs today retain-days)]
        (if-not apply?
          (assoc plan :status :ok :applied false :tombs (count tombs))
          (let [paths (mapv #(str store "/objects/" (cas/object-rel %)) (:drop plan))
                ok? (mapv hio/delete-file! paths)
                failed (mapv first (filter (complement second) (map vector (:drop plan) ok?)))]
            (if (seq failed)
              {:status :refused :reason "cas/gc-failed" :failed failed}
              (let [stale (vec (sort (remove (set (keys inv)) (keys tombs))))
                    kept (apply dissoc tombs (concat (:drop plan) (:revived plan) stale))
                    marked (zipmap (:mark plan)
                                   (map (fn [s] {:deleted-at today :size (get inv s)})
                                        (:mark plan)))
                    next-tombs (merge kept marked)]
                (hio/mkdirs! store)
                (hio/write-latin1! (cas-tombs-path store) (canon next-tombs))
                (assoc plan :status :ok :applied true :deleted (count (:drop plan))
                       :stale stale :tombs (count next-tombs))))))))))

(defn- peer-apply-one [src dst sha size]
  (let [s (hio/read-latin1 (cas-object-path src sha))]
    (if (or (nil? s) (not= sha (cas/sha256-hex (hio/bytes-of s))))
      {:sha sha :size size :ok false}
      (do (hio/mkdirs! (str dst "/objects/" (subs sha 0 2)))
          (hio/write-latin1! (cas-object-path dst sha) s)
          (let [back (hio/read-latin1 (cas-object-path dst sha))]
            {:sha sha :size size
             :ok (and (some? back) (= sha (cas/sha256-hex (hio/bytes-of back))))})))))

(defn- peer-merge-manifest [dst-manifest src-manifest shas]
  (let [by-sha (into {} (map (fn [[rel e]] [(:sha e) [rel e]]) src-manifest))]
    (reduce (fn [m sha] (if-let [[rel e] (get by-sha sha)] (assoc m rel e) m))
            dst-manifest shas)))

(defn- peer-args [args]
  "Parses --as/--registry/--copies (a flag consumes its value); every
  other arg is a peer dir, collected in :peers in order. push/pull
  ignore :peers; repair heals across them, fleet-status censuses them."
  [args]
  (loop [as (seq args) m {:peers []}]
    (cond (nil? as) m
          (= "--as" (first as)) (recur (nnext as) (assoc m :as (second as)))
          (= "--registry" (first as)) (recur (nnext as) (assoc m :registry (second as)))
          (= "--copies" (first as)) (recur (nnext as) m)
          :else (recur (next as) (update m :peers conj (first as))))))

(defn- peer-gate [registry as peerdir today]
  (cond (nil? registry) {:ok true}
        (nil? as) {:ok false :reason "peer/node-required"}
        (not (peer/member? registry as today)) {:ok false :reason "peer/not-member" :node as}
        (not= (peer/node-prefix registry as) (cas-rel-name peerdir))
        {:ok false :reason "peer/prefix-mismatch" :node as}
        :else {:ok true}))

(defn- peer-sync [store peerdir direction opts today]
  (let [raw (when (:registry opts) (read-edn-file (:registry opts)))
        registry (when raw (into {} (map (fn [[n e]] [n (if (map? e) (kw-keys e) e)]) raw)))
        gate (peer-gate registry (:as opts) peerdir today)]
    (if-not (:ok gate)
      {:status :refused :gate (dissoc gate :ok)}
      (let [lm (cas-read-manifest store)
            rm (cas-read-manifest peerdir)
            plan (peer/sync-plan lm rm)
            jobs (if (= direction :send) (:send plan) (:fetch plan))
            src (if (= direction :send) store peerdir)
            dst (if (= direction :send) peerdir store)
            srcm (if (= direction :send) lm rm)
            dstm (if (= direction :send) rm lm)
            applied (mapv (fn [{:keys [sha size]}] (peer-apply-one src dst sha size)) jobs)]
        (if (some (complement :ok) applied)
          {:status :refused :reason "peer/copy-failed" :plan plan :applied applied}
          (do (cas-write-manifest! dst (peer-merge-manifest dstm srcm (map :sha jobs)))
              {:status :ok :direction direction :plan plan :applied (count applied)}))))))

(defn- admitted-peers
  "Gate every listed peer dir, in order. Returns {:admitted [{:peer
  :manifest}] :skipped [{:peer :reason}]}. Skipped peers are reported
  and never read."
  [opts today]
  (let [raw (when (:registry opts) (read-edn-file (:registry opts)))
        registry (when raw (into {} (map (fn [[n e]] [n (if (map? e) (kw-keys e) e)]) raw)))
        gated (mapv (fn [pd] {:dir pd :gate (peer-gate registry (:as opts) pd today)})
                    (:peers opts))
        ok? #(get-in % [:gate :ok])]
    {:admitted (mapv (fn [{:keys [dir]}] {:peer dir :manifest (cas-read-manifest dir)})
                     (filter ok? gated))
     :skipped (mapv (fn [{:keys [dir gate]}] {:peer dir :reason (:reason gate)})
                    (remove ok? gated))}))

(defn- read-edn-safe
  "read-edn-file that survives corruption: nil when missing, ::corrupt
  when the file is there but unparsable, data otherwise."
  [path]
  (if-not (hio/file? path)
    nil
    (try (hio/parse-edn (hio/slurp-utf8 path))
         (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ ::corrupt))))

(defn- cas-rehash-inventory
  "Re-hash every stored object from its bytes: [{:sha :size}] sorted by
  sha. The path-derived digest is never trusted here; a tampered file
  reports under its true digest (or drops out when unreadable)."
  [store]
  (->> (cas-inventory store)
       keys
       (keep (fn [psha]
               (when-let [s (hio/read-latin1 (str store "/objects/" (cas/object-rel psha)))]
                 (let [bs (hio/bytes-of s)]
                   {:sha (cas/sha256-hex bs) :size (count bs)}))))
       distinct
       vec
       (sort-by :sha)))

(defn- cas-reconcile
  "Rebuild damaged manifest entries from content + peers. Adopts rels
  the local manifest lacks (first peer wins, content re-hashed); never
  overwrites a local entry. Conflicts refuse the whole reconcile and
  nothing is written. Reports :local-damaged when the manifest file
  was present but unparsable."
  [store opts today]
  (let [{:keys [admitted skipped]} (admitted-peers opts today)]
    (if (empty? admitted)
      {:status :refused :reason "cas/reconcile-no-peer" :skipped skipped}
      (let [raw-local (read-edn-safe (cas-manifest-path store))
            local (if (map? raw-local)
                    (into {} (map (fn [[rel e]] [rel (if (map? e) (kw-keys e) e)]) raw-local))
                    {})
            damaged (and (some? raw-local) (not (map? raw-local)))
            plan (cas/manifest-rebuild local (cas-rehash-inventory store) admitted)]
        (cond (seq (:conflicts plan))
              {:status :refused :reason "cas/reconcile-conflict"
               :conflicts (:conflicts plan) :skipped skipped :local-damaged damaged}
              (empty? (:adopted plan))
              {:status :ok :adopted 0 :entries [] :unmatched (:unmatched plan)
               :skipped skipped :local-damaged damaged}
              :else
              (do (cas-write-manifest!
                   store (merge local (into {} (map (fn [{:keys [rel sha size]}]
                                                      [rel {:sha sha :size size}])
                                                    (:adopted plan)))))
                  {:status :ok :adopted (count (:adopted plan)) :entries (:adopted plan)
                   :unmatched (:unmatched plan) :skipped skipped :local-damaged damaged}))))))

(defn- fleet-status
  "Fleet-wide copy census over admitted peers: who holds what, and what
  falls below `desired` copies. Read-only; skipped peers are reported,
  never read. No admitted peer refuses the whole status."
  [opts today desired]
  (let [{:keys [admitted skipped]} (admitted-peers opts today)]
    (if (empty? admitted)
      {:status :refused :reason "fleet/no-peer" :skipped skipped}
      (let [fc (peer/fleet-copies admitted)
            plan (peer/replication-plan (:copies fc) desired)]
        {:status :ok :peers (:peers fc) :objects (:objects fc)
         :desired desired :under (:under plan) :healthy (:healthy plan)
         :skipped skipped}))))

(defn- repair-one
  "Copy one bad object from the first candidate peer whose manifest
  lists its rel (try-order), re-hashed on receipt. Returns the
  peer-apply-one record with :rel and the winning :from, or :ok false
  when every candidate copy failed."
  [candidates store {:keys [rel sha size]}]
  (loop [cs (filter #(contains? (:manifest %) rel) candidates)]
    (if (empty? cs)
      {:rel rel :sha sha :size size :ok false :from nil}
      (let [r (peer-apply-one (:peer (first cs)) store sha size)]
        (if (:ok r)
          (assoc r :rel rel :from (:peer (first cs)))
          (recur (rest cs)))))))

(defn- peer-repair
  "Heal tampered or missing local objects from admitted peers, in
  listed order with fallback: each bad object is copied from the first
  peer whose manifest lists it, and on copy failure the next such peer
  is tried. Peers failing the registry gate are listed in :skipped and
  never touched. :ok only when the final verify is clean; otherwise
  refused with the remaining bad rels. The manifest is untouched: bad
  rels already point at the right digests."
  [store opts today]
  (let [{:keys [admitted skipped]} (admitted-peers opts today)]
    (if (empty? admitted)
      {:status :refused :reason "peer/repair-no-peer" :skipped skipped}
      (let [lm (cas-read-manifest store)
            read-local (fn [rel] (when-let [e (get lm rel)]
                                   (when-let [s (hio/read-latin1 (cas-object-path store (:sha e)))]
                                     (hio/bytes-of s))))
            rms admitted
            plan (peer/repair-plan (cas/verify-manifest lm read-local) rms)
            healed (mapv #(repair-one rms store %) (:repair plan))
            failed (filterv (complement :ok) healed)]
        (if (seq failed)
          {:status :refused :reason "peer/repair-copy-failed"
           :failed (mapv :sha failed) :skipped skipped}
          (let [vr (cas/verify-manifest lm read-local)
                left (vec (sort (distinct (concat (:mismatch vr []) (:missing vr [])))))]
            (if (seq left)
              {:status :refused :reason "peer/repair-incomplete"
               :unrepairable (:unrepairable plan) :remaining left :skipped skipped}
              {:status :ok :repaired (count healed)
               :from (frequencies (map :from healed))
               :skipped skipped :unrepairable (:unrepairable plan)})))))))

(defn- market-today [more]
  (loop [as (seq more) today nil]
    (cond (nil? as) today
          (= "--today" (first as)) (recur (nnext as) (second as))
          :else (recur (next as) today))))

(defn- market-flag [more flag]
  (loop [as (seq more)]
    (cond (nil? as) nil
          (= flag (first as)) (second as)
          :else (recur (next as)))))

(defn- str-keys
  "Issuer secrets keyed by name: keyword keys become their name so
  hand-written {\"k\" ...} and {:k ...} EDN agree. Values pass through."
  [m]
  (into {} (map (fn [[k v]] [(if (keyword? k) (name k) (str k)) v]) m)))

(defn main* [args]
  (let [[cmd a b & more] args]
    (case cmd
      "check" (if-not a
                (usage)
                (let [r (check-result (load-dir a (hio/read-text-fn a)))]
                  (println (canon r))
                  (if (zero? (:errors r)) 0 1)))
      "dry-run" (if-not (and a b)
                  (usage)
                  (let [r (profile/dry-run (load-dir a (hio/read-text-fn a)) b
                                           {:capabilities (capabilities-arg more)})]
                    (println (canon r))
                    (case (:status r) :ok 0 :held 2 1)))
      "tick" (if-not a
               (usage)
               (let [prof (load-dir a (hio/read-text-fn a))
                     errs (profile/errors (profile/check prof))]
                 (if (seq errs)
                   (do (println (canon {:profile (:name prof) :status :refused
                                        :reason "profile/invalid" :problems errs}))
                       1)
                   (let [now (hio/now-parts)
                         r (assoc (tick/tick-plan (:jobs prof) now) :profile (:name prof))]
                     (println (canon r))
                     0))))
      "turn-gate" (if-not (and a b)
                    (usage)
                    (let [r (turn/turn-gate (load-dir a (hio/read-text-fn a)) b
                                            {:capabilities (capabilities-arg more)})]
                      (println (canon r))
                      (exit-for r)))
      "run-plan" (if-not (and a b)
                   (usage)
                   (let [now (hio/now-parts)
                         r (run/run-plan (load-dir a (hio/read-text-fn a)) b
                                         {:capabilities (capabilities-arg more)
                                          :script (script-arg more)
                                          :now now
                                          :stamp "plan"
                                          :run-at "plan"})]
                     (println (canon r))
                     (exit-for r)))
      "store-plan" (let [{:keys [direction from to force?]} (store-args args)]
                     (if-not (and (contains? #{:import :export} direction) from to)
                       (usage)
                       (let [r (store/plan-copy {:from (store-listing from) :to (store-listing to)
                                                 :force? force? :direction direction
                                                 :from-name from :to-name to})]
                         (println (canon r))
                         (if (= :ok (:status r)) 0 1))))
      "pause" (let [root (pause/store-root hio/env)
                    reason (let [r (str/trim (str/join " " (filter some? (cons a (cons b more)))))]
                             (when-not (str/blank? r) r))]
                (hio/mkdirs! root)
                (hio/write-text! (pause/marker-path root)
                                 (pause/format-record (:iso (hio/now-parts)) reason))
                (println (pause/pause-message reason))
                0)
      "resume" (let [root (pause/store-root hio/env)
                     m (pause/marker-path root)
                     was (hio/file? m)]
                 (when was (hio/delete-file! m))
                 (println (pause/resume-message was))
                 0)
      "status" (let [r (gather-status (pause/store-root hio/env))]
                 (doseq [l (status/render-status r)] (println l))
                 0)
      "model" (if-not a
                (usage)
                (let [{:keys [pos run-model provider]} (model-flags (cons b more))
                      req (model/model-request pos run-model provider)]
                  (if (:error req)
                    (do (println (:error req)) 2)
                    (let [flags {:run-model run-model :provider provider}]
                      (if-let [s (:set req)]
                        (let [p (str a "/config.yaml")
                              cur (when (hio/file? p) (hio/slurp-utf8 p))]
                          (try
                            (let [written (model/set-model-text (or cur "") (:model s) (:provider s))]
                              (hio/mkdirs! a)
                              (hio/replace-file! p written)
                              (let [r (resolve-in a flags)]
                                (doseq [l (model/model-lines r true)] (println l))
                                0))
                            (catch #?(:lpy python/Exception :clj Exception :cljs :default) e
                              (println (ex-message e)) 1)))
                        (let [r (resolve-in a flags)]
                          (doseq [l (model/model-lines r false)] (println l))
                          0))))))
      "fallback" (if-not a
                   (usage)
                   (let [{:keys [pos run-model provider]} (model-flags (cons b more))]
                     (if (seq pos)
                       (do (println "usage: itonami-agent fallback [--profile P] [-m model]") 2)
                       (let [r (resolve-in a {:run-model run-model :provider provider})]
                         (doseq [l (model/fallback-lines r)] (println l))
                         0))))
      "run-exec" (if (or (not a) (not b))
                   (usage)
                   (let [prof (load-dir a (hio/read-text-fn a))
                         r (execute-run prof b (capabilities-arg more) (host-now) nil)]
                     (doseq [l (:lines r)] (println l))
                     (:exit r)))
      "doctor" (let [{:keys [profile]} (store-flags (cons b more))
                     r (doctor-for (pause/store-root hio/env) (or profile "default")
                                   (hio/all-env) nil)]
                 (doseq [l (:lines r)] (println l))
                 (:exit r))
      "sessions" (let [{:keys [profile limit pos]} (store-flags (cons b more))
                       r (sessions-for (pause/store-root hio/env) (or profile "default")
                                       (first pos) (second pos) limit)]
                   (doseq [l (:lines r)] (println l))
                   (:exit r))
      "cas-put" (let [files (if b (cons b more) [])]
                  (if (or (nil? a) (empty? files))
                    (usage)
                    (let [r (cas-put a files)]
                      (println (canon r))
                      (if (= :ok (:status r)) 0 1))))
      "cas-get" (if-not (and a b (first more))
                  (usage)
                  (let [r (cas-get a b (first more))]
                    (println (canon r))
                    (if (= :ok (:status r)) 0 1)))
      "cas-verify" (if-not a
                     (usage)
                     (let [r (cas-verify a)]
                       (println (canon r))
                       (if (= :ok (:status r)) 0 1)))
      "cas-gc" (if-not a
                 (usage)
                 (let [tail (cons b more)
                       days-str (market-flag tail "--retain-days")
                       days (when days-str (cas/parse-int days-str))]
                   (if (and days-str (or (nil? days) (neg? days)))
                     (do (println (canon {:status :refused :reason "cas/bad-retain-days"})) 1)
                     (let [r (cas-gc a {:apply? (boolean (some #{"--apply"} tail))
                                        :retain-days days}
                                     (subs (:iso (hio/now-parts)) 0 10))]
                       (println (canon r))
                       (if (= :ok (:status r)) 0 1)))))
      "cas-reconcile" (if-not (and a b)
                        (usage)
                        (let [r (cas-reconcile a (peer-args (cons b more)) (subs (:iso (hio/now-parts)) 0 10))]
                          (println (canon r))
                          (if (= :ok (:status r)) 0 1)))
      "fleet-status" (if-not a
                       (usage)
                       (let [tail (filterv some? (cons a (cons b more)))
                             copies-str (market-flag tail "--copies")
                             copies (if (nil? copies-str) 2 (cas/parse-int copies-str))]
                         (if (or (nil? copies) (< copies 1))
                           (do (println (canon {:status :refused :reason "fleet/bad-copies"})) 1)
                           (let [r (fleet-status (peer-args tail) (subs (:iso (hio/now-parts)) 0 10) copies)]
                             (println (canon r))
                             (if (= :ok (:status r)) 0 1)))))
      "peer-push" (if-not (and a b)
                    (usage)
                    (let [r (peer-sync a b :send (peer-args more) (subs (:iso (hio/now-parts)) 0 10))]
                      (println (canon r))
                      (if (= :ok (:status r)) 0 1)))
      "peer-pull" (if-not (and a b)
                    (usage)
                    (let [r (peer-sync a b :fetch (peer-args more) (subs (:iso (hio/now-parts)) 0 10))]
                      (println (canon r))
                      (if (= :ok (:status r)) 0 1)))
      "peer-repair" (if-not (and a b)
                      (usage)
                      (let [r (peer-repair a (peer-args (cons b more)) (subs (:iso (hio/now-parts)) 0 10))]
                        (println (canon r))
                        (if (= :ok (:status r)) 0 1)))
      "market-match" (if-not (and a b)
                       (usage)
                       (let [asks (mapv (fn [x] (if (map? x) (kw-keys x) x)) (or (read-edn-file a) []))
                             bids (mapv (fn [x] (if (map? x) (kw-keys x) x)) (or (read-edn-file b) []))
                             today (or (market-today more) (subs (:iso (hio/now-parts)) 0 10))
                             bad (vec (sort (map :node (filter #(not (market/valid-attestation? % today)) asks))))]
                         (if (seq bad)
                           (do (println (canon {:status :refused :reason "market/attestation-invalid" :nodes bad})) 1)
                           (let [secrets (when-let [p (market-flag more "--secrets")]
                                           (when-let [raw (read-edn-file p)] (str-keys raw)))
                                 r (market/match-signed asks bids secrets)]
                             (println (canon (if (= :refused (:status r)) r (assoc r :status :ok))))
                             (if (= :refused (:status r)) 1 0)))))
      "market-verify" (let [secret (market-flag (cons b more) "--suite-secret")]
                      (if-not (and a secret)
                        (usage)
                        (let [att (let [raw (read-edn-file a)] (if (map? raw) (kw-keys raw) raw))
                              today (or (market-today more) (subs (:iso (hio/now-parts)) 0 10))
                              r (market/verify-asymmetric att {(:issuer att) secret}
                                                          market/verify-hmac-sig today)]
                          (println (canon r))
                          (if (:valid r) 0 1))))
      "market-sign" (let [secret (market-flag (cons b more) "--issuer-secret")]
                      (if-not (and a secret)
                        (usage)
                        (let [att (let [raw (read-edn-file a)] (if (map? raw) (kw-keys raw) raw))
                              signed (market/sign-attestation att secret)]
                          (if (nil? signed)
                            (do (println (canon {:status :refused :reason "market/unsignable"})) 1)
                            (do (println (canon signed)) 0)))))
      "test" (if-not a
               (usage)
               (let [r (run-cases a)]
                 (doseq [l (:ids r)] (println l))
                 (println (canon (dissoc r :ids)))
                 (if (and (pos? (:cases r)) (= (:pass r) (:cases r))) 0 1)))
      (usage))))

(defn -main [& args]
  (hio/exit! (main* (vec args))))

#?(:lpy (when (= (str *main-ns*) "itonami.otehon.cli")
          (apply -main *command-line-args*)))
