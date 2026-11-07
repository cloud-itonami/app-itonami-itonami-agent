(ns itonami.otehon.cli
  "itonami-otehon: check a profile, dry-run one cron job, run the shared cases.

    check   <profile-dir>
    dry-run <profile-dir> <job-id-or-name> [--capability <name>]...
    test    <cases.edn>

  Output is one canonical EDN value (sorted map keys) so the three runners
  can be compared byte for byte. Exit 0 ok, 1 refused / errors / failures,
  2 held for the owner, 64 usage."
  (:require [clojure.string :as str]
            [itonami.otehon.io :as hio]
            [itonami.otehon.profile :as profile]
            [itonami.otehon.yakuwari :as yakuwari]
            [itonami.otehon.yaml :as yaml]))

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
                 {:refused (or (:yaml/refused (ex-data e)) :error)}))))

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
  (println "usage: itonami-otehon check <dir> | dry-run <dir> <job> [--capability c]... | test <cases.edn>")
  64)

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
