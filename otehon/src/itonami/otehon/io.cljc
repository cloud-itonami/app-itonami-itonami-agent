(ns itonami.otehon.io
  "The only host-specific namespace: files, JSON, EDN, env, exit, clock and
  directory listing. Each branch is one runner: :lpy Basilisp (Python),
  :clj Babashka, :cljs nbb. Pure namespaces stay branch-free and call in
  here, so every host difference is visible in this one file."
  (:require [clojure.string :as str]
            #?(:clj [cheshire.core :as json])
            #?(:clj [clojure.edn :as edn])
            #?(:cljs [cljs.reader :as reader])
            #?(:cljs ["fs" :as fs])
            #?(:lpy [basilisp.edn :as edn]))
  #?(:lpy (:import json os sys)))

(def runner #?(:lpy "basilisp" :clj "babashka" :cljs "nbb"))

(defn runner-version []
  #?(:lpy (str "basilisp " (.version (python/__import__ "importlib.metadata" ** :fromlist ["version"]) "basilisp")
               " / python " (first (str/split (.-version sys) #" ")))
     :clj (str "babashka " (System/getProperty "babashka.version"))
     :cljs (str "nbb on node " (.-version js/process))))

(defn file? [path]
  #?(:lpy (.isfile os/path path)
     :clj (.isFile (java.io.File. ^String path))
     :cljs (and (.existsSync fs path) (.isFile (.statSync fs path)))))

(defn slurp-utf8 [path]
  #?(:lpy (with-open [f (python/open path ** :mode "r" :encoding "utf-8")] (.read f))
     :clj (slurp path :encoding "UTF-8")
     :cljs (.readFileSync fs path "utf8")))

(defn read-text-fn
  "relative path -> text or nil, under `dir`."
  [dir]
  (fn [rel] (let [p (str dir "/" rel)] (when (file? p) (slurp-utf8 p)))))

(defn parse-json
  "JSON text -> data with string keys."
  [s]
  #?(:lpy (py->lisp (.loads json s) :keywordize-keys false)
     :clj (json/parse-string s)
     :cljs (js->clj (.parse js/JSON s))))

(defn parse-edn [s]
  #?(:lpy (edn/read-string s)
     :clj (edn/read-string s)
     :cljs (reader/read-string s)))

(defn dir? [path]
  #?(:lpy (.isdir os/path path)
     :clj (.isDirectory (java.io.File. ^String path))
     :cljs (and (.existsSync fs path) (.isDirectory (.statSync fs path)))))

(defn read-dir-names
  "Single-level names under `dir`, [] when it is missing."
  [dir]
  #?(:lpy (if (.isdir os/path dir) (vec (.listdir os dir)) [])
     :clj (vec (or (seq (.list (java.io.File. ^String dir))) []))
     :cljs (try (vec (.readdirSync fs dir)) (catch :default _ []))))

(defn now-parts
  "Now in the host zone: {:iso \"YYYY-MM-DDTHH:MM:SS+HH:MM\" (the same shape
  the legacy route writes to next_run_at) :parts {:year :month :day :hour
  :minute}}. The pure tick namespace compares and advances from this."
  []
  #?(:lpy (let [dt (python/__import__ "datetime")
                now (.astimezone (.now (.-datetime dt)))
                off (.strftime now "%z")
                iso (str (.strftime now "%Y-%m-%dT%H:%M:%S") (subs off 0 3) ":" (subs off 3 5))]
            {:iso iso
             :parts {:year (.-year now) :month (.-month now) :day (.-day now)
                     :hour (.-hour now) :minute (.-minute now)}})
     :clj (let [z (java.time.ZonedDateTime/now)
                fmt (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ssxxx")]
            {:iso (.format z fmt)
             :parts {:year (.getYear z) :month (.getMonthValue z) :day (.getDayOfMonth z)
                     :hour (.getHour z) :minute (.getMinute z)}})
     :cljs (let [d (js/Date.)
                 pad (fn [n w] (.padStart (str n) w "0"))
                 m (- (.getTimezoneOffset d))
                 s (if (neg? m) "-" "+")
                 a (js/Math.abs m)
                 off (str s (pad (quot a 60) 2) ":" (pad (mod a 60) 2))]
             {:iso (str (.getFullYear d) "-" (pad (inc (.getMonth d)) 2) "-" (pad (.getDate d) 2)
                        "T" (pad (.getHours d) 2) ":" (pad (.getMinutes d) 2) ":" (pad (.getSeconds d) 2)
                        off)
              :parts {:year (.getFullYear d) :month (inc (.getMonth d)) :day (.getDate d)
                      :hour (.getHours d) :minute (.getMinutes d)}})))

(defn basename [path] (last (str/split (str/replace path #"/+$" "") #"/")))

(defn dirname [path]
  (let [parts (str/split path #"/")]
    (if (> (count parts) 1) (str/join "/" (butlast parts)) ".")))

(defn env [k]
  #?(:lpy (.get os/environ k)
     :clj (System/getenv k)
     :cljs (aget (.-env js/process) k)))

(defn exit! [code]
  #?(:lpy (.exit sys code)
     :clj (System/exit code)
     :cljs (.exit js/process code)))

;; ---------------------------------------------------------------- bytes
;; latin-1 is a byte<->char bijection on every runner, so the same .cljc
;; hashing sees the same bytes everywhere. Binary content travels as text
;; through these two functions; sha and manifests stay pure.

(defn read-latin1
  "File text decoded latin-1 (one char per byte), nil when missing."
  [path]
  (when (file? path)
    #?(:lpy (with-open [f (python/open path ** :mode "r" :encoding "latin-1")] (.read f))
       :clj (slurp path :encoding "ISO-8859-1")
       :cljs (.readFileSync fs path "latin1"))))

(defn write-latin1!
  "Write latin-1 text (one byte per char)."
  [path s]
  #?(:lpy (with-open [f (python/open path ** :mode "w" :encoding "latin-1")] (.write f s))
     :clj (spit path s :encoding "ISO-8859-1")
     :cljs (.writeFileSync fs path s "latin1")))

(defn bytes-of
  "latin-1 string -> vector of 0-255 ints. Exact on every runner because
  the input chars are already confined to one byte."
  [s]
  #?(:lpy (vec (.encode s "latin-1"))
     :clj (mapv int s)
     :cljs (let [n (.-length s)]
             (loop [i 0 v []]
               (if (= i n) v (recur (inc i) (conj v (.charCodeAt s i))))))))

(defn mkdirs!
  "Create `dir` and parents. The return value differs per runner and is
  not part of any contract."
  [dir]
  #?(:lpy (.makedirs os dir ** :exist_ok true)
     :clj (.mkdirs (java.io.File. ^String dir))
     :cljs (.mkdirSync fs dir #js {:recursive true})))

(defn file-size
  "Size of `path` in bytes, nil when missing."
  [path]
  (when (file? path)
    #?(:lpy (.getsize os/path path)
       :clj (.length (java.io.File. ^String path))
       :cljs (.-size (.statSync fs path)))))

(defn delete-file!
  "Delete one file. True when it is gone afterwards, false when it was
  already missing (no throw); a failed delete reads false on :clj."
  [path]
  #?(:lpy (if (.isfile os/path path) (do (.remove os path) true) false)
     :clj (.delete (java.io.File. ^String path))
     :cljs (try (.unlinkSync fs path) true (catch :default _ false))))

(defn write-text!
  "Write UTF-8 text to `path` (no mkdir; the caller creates parents).
  The pause marker and other small store files go through here."
  [path s]
  #?(:lpy (with-open [f (python/open path ** :mode "w" :encoding "utf-8")] (.write f s))
     :clj (spit path s :encoding "UTF-8")
     :cljs (.writeFileSync fs path s "utf8")))

(defn pid-alive?
  "Whether `s` names a live process: digits only, in 2..2^31-1 (0 and 1
  are never a gateway owner), and the host still sees the owner.
  Mirrors the legacy pid checks: a dead owner (ESRCH) reads false, an
  unparsable value is garbage and reads false, EPERM (another user owns
  it) still reads live."
  [s]
  (if-not (re-matches #"\d+" (str s))
    false
    ;; strip leading zeros first so a long zero-padded value cannot
    ;; overflow the parse; anything left over 10 digits is out of int32
    ;; and reads as garbage, as in the legacy check.
    (let [digits (str/replace (str s) #"^0+" "")
          digits (if (= digits "") "0" digits)]
      (if (> (count digits) 10)
        false
        (let [n (parse-long digits)]
          (if (or (nil? n) (< n 2) (> n 2147483647))
            false
        #?(:lpy (try (.kill os n 0) true
                     (catch python/Exception e (not= 3 (.-errno e))))
           :clj (try (let [o (java.lang.ProcessHandle/of (long n))]
                       (boolean (and (.isPresent o) (.isAlive (.get o)))))
                     (catch Exception _ false))
           :cljs (try (.kill js/process n 0) true
                      (catch :default e (not= "ESRCH" (.-code e)))))))))))

;; ---------------------------------------------------------------- exec plans
;; Declarative requests for the two host executions (JUN-1020, owner choice
;; io.cljc extension). Pure constructors: no process spawns, no model is
;; called, nothing is written here. The host executes the steps in the order
;; the plan lists them and reports results back by step :id.

(def script-timeout-ms
  "Default script timeout: the native route waits 180s (process.cljk
  wait-result default)."
  180000)

(defn script-request
  "One host script execution, as data. `script-ref` names the script (a job's
  \"script\" value); opts :timeout-ms (default 180000), :workdir, :env."
  [script-ref {:keys [timeout-ms workdir env]}]
  {:kind :script
   :ref (str script-ref)
   :timeout-ms (or timeout-ms script-timeout-ms)
   :workdir workdir
   :env (or env {})})

(defn inference-request
  "One host inference turn, as data. `target` is the rail target (with
  :verdict), `prompt` the full prompt; the rest rides along so the host
  needs nothing else."
  [{:keys [target prompt toolsets reasoning-effort decisions]}]
  {:kind :inference
   :target target
   :prompt (str prompt)
   :toolsets (vec toolsets)
   :reasoning-effort reasoning-effort
   :decisions (vec decisions)})
