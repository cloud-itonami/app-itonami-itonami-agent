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
