(ns itonami.otehon.io
  "The only host-specific namespace: files, JSON, EDN, env, exit.
  Each branch is one runner: :lpy Basilisp (Python), :clj Babashka, :cljs nbb."
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
