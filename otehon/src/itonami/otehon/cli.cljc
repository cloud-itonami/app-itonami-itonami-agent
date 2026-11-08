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
    cas-put    <store> <file>...
    cas-get    <store> <sha> <out>
    cas-verify <store>
    cas-gc     <store> [--apply]
    cas-reconcile <store> <peerdir>... [--as <node> --registry <reg.edn>]
    peer-push  <store> <peerdir> [--as <node> --registry <reg.edn>]
    peer-pull  <store> <peerdir> [--as <node> --registry <reg.edn>]
    peer-repair <store> <peerdir>... [--as <node> --registry <reg.edn>]
    market-match <asks.edn> <bids.edn> [--today YYYY-MM-DD] [--secrets <secrets.edn>]
    market-sign <att.edn> --issuer-secret <secret>
    test       <cases.edn>

  tick, turn-gate and run-plan decide without doing: no script runs, no
  inference is called, nothing is saved (side-effects :none). store-plan
  prints the copy plan; the host copies the bytes.
  Output is one canonical EDN value (sorted map keys) so the three runners
  can be compared byte for byte. Exit 0 ok, 1 refused / errors / failures,
  2 held for the owner, 64 usage."
  (:require [clojure.string :as str]
            [itonami.otehon.io :as hio]
            [itonami.otehon.profile :as profile]
            [itonami.otehon.yakuwari :as yakuwari]
            [itonami.otehon.yaml :as yaml]
            [itonami.otehon.text :as text]
            [itonami.otehon.tick :as tick]
            [itonami.otehon.store :as store]
            [itonami.otehon.turn :as turn]
            [itonami.otehon.run :as run]
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
  (profile/load-profile {:name (hio/basename dir) :read-text read-text
                         :parse-edn hio/parse-edn :parse-json hio/parse-json}))

(defn check-result [prof]
  (let [ps (profile/check prof)]
    (merge {:profile (:name prof) :runtime (:runtime prof)} (profile/summary ps))))

;; ---------------------------------------------------------------- shared cases

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
  "What one case's operation returns for the given read-text."
  [c dir read-text]
  (case (:op c)
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
    (and (subset? (dissoc want :messages-include :reason-in) got)
         (includes-all? (:messages got) (:messages-include want []))
         (or (nil? (:reason-in want)) (contains? (:reason-in want) (:reason got))))))

(defn run-case [c fixtures]
  (let [dir (when (:profile c) (str fixtures "/" (:profile c)))
        base (if dir (hio/read-text-fn dir) (constantly nil))
        edited (apply-edits base (:edit c []))
        got (try (observe c dir edited)
                 (catch #?(:lpy python/Exception :clj Exception :cljs :default) e
                   {:crash (str (ex-message e))}))
        ok? (satisfied? c got)
        ;; negative control: the edit, not the fixture, must cause the rejection
        control-ok? (if (and (:negative c) (seq (:edit c)))
                      (not (satisfied? c (try (observe c dir base)
                                              (catch #?(:lpy python/Exception :clj Exception :cljs :default) e
                                                {:crash (str (ex-message e))}))))
                      true)]
    {:id (:id c) :negative (boolean (:negative c)) :pass (and ok? control-ok?)
     :got (when-not (and ok? control-ok?) got)
     :control-failed (when-not control-ok? true)}))

(defn run-cases [path]
  (let [doc (hio/parse-edn (hio/slurp-utf8 path))
        fixtures (str (hio/dirname path) "/" (:fixtures doc "fixtures"))
        results (mapv #(run-case % fixtures) (:cases doc))]
    {:runner (hio/runner-version)
     :cases (count results)
     :pass (count (filter :pass results))
     :fail (vec (for [r results :when (not (:pass r))] (dissoc r :pass)))
     :negative-controls (count (filter :negative results))
     :ids (mapv #(str (:id %) (if (:pass %) " ok" " FAIL")) results)}))

;; ---------------------------------------------------------------- main

(defn- capabilities-arg [args]
  (loop [as (seq args) caps []]
    (cond (nil? as) caps
          (and (= "--capability" (first as)) (second as))
          (recur (nnext as) (conj caps (keyword (second as))))
          :else (recur (next as) caps))))

(defn- usage []
  (println "usage: itonami-otehon check <dir> | dry-run <dir> <job> [--capability c]... | tick <dir> | turn-gate <dir> <prompt> [--capability c]... | run-plan <dir> <job> [--capability c]... [--script-file <path> | --script-error <text>] [--script-no-wake] | store-plan --direction import|export --from <dir> --to <dir> [--force] | cas-put <store> <file>... | cas-get <store> <sha> <out> | cas-verify <store> | cas-gc <store> [--apply] | cas-reconcile <store> <peerdir>... [--as <node> --registry <reg.edn>] | peer-push <store> <peerdir> [--as <node> --registry <reg.edn>] | peer-pull <store> <peerdir> [--as <node> --registry <reg.edn>] | peer-repair <store> <peerdir>... [--as <node> --registry <reg.edn>] | market-match <asks.edn> <bids.edn> [--today YYYY-MM-DD] [--secrets <secrets.edn>] | market-sign <att.edn> --issuer-secret <secret> | test <cases.edn>")
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

(defn- cas-gc
  "Drop objects no manifest references. Dry-run by default (prints the
  plan, deletes nothing); --apply deletes and reports :deleted. The
  manifest is never rewritten; a failed delete refuses the whole gc."
  [store apply?]
  (let [plan (cas/gc-plan (cas-read-manifest store) (cas-inventory store))]
    (if-not apply?
      (assoc plan :status :ok :applied false)
      (let [paths (mapv #(str store "/objects/" (cas/object-rel %)) (:drop plan))
            ok? (mapv hio/delete-file! paths)
            failed (mapv first (filter (complement second) (map vector (:drop plan) ok?)))]
        (if (seq failed)
          {:status :refused :reason "cas/gc-failed" :failed failed}
          (assoc plan :status :ok :applied true :deleted (count (:drop plan))))))))

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
  "Parses --as/--registry; every other arg is a peer dir, collected in
  :peers in order. push/pull ignore :peers; repair heals across them."
  [args]
  (loop [as (seq args) m {:peers []}]
    (cond (nil? as) m
          (= "--as" (first as)) (recur (nnext as) (assoc m :as (second as)))
          (= "--registry" (first as)) (recur (nnext as) (assoc m :registry (second as)))
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
                 (let [r (cas-gc a (boolean (some #{"--apply"} (cons b more))))]
                   (println (canon r))
                   (if (= :ok (:status r)) 0 1)))
      "cas-reconcile" (if-not (and a b)
                        (usage)
                        (let [r (cas-reconcile a (peer-args (cons b more)) (subs (:iso (hio/now-parts)) 0 10))]
                          (println (canon r))
                          (if (= :ok (:status r)) 0 1)))
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
