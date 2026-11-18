(ns itonami.app.main
  "The Tauri WebView entry. Loads the profile bundle shipped with the app
  (profiles.json, same origin), runs the Otehon core on the device and renders
  the result. No request leaves the device."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [itonami.app.core :as core]))

(def readers
  {:parse-edn reader/read-string
   :parse-json (fn [s] (js->clj (.parse js/JSON s)))})

(defn- el
  ([tag] (el tag {} []))
  ([tag attrs & children]
   (let [n (.createElement js/document (name tag))]
     (doseq [[k v] attrs]
       (if (= k :class) (set! (.-className n) v) (.setAttribute n (name k) (str v))))
     (doseq [c (flatten children) :when (some? c)]
       (.appendChild n (if (string? c) (.createTextNode js/document c) c)))
     n)))

(defn- badge [kind text] (el :span {:class (str "badge " kind)} text))

(defn- status-kind [s]
  (case s :ok "ok" :held "held" :refused "refused" "unknown"))

(defn- problem-row [{:keys [level file path message]}]
  (el :li {:class (str "problem " (name level))}
      (badge (name level) (name level))
      (el :code {} (str file (when (seq path) (str " " (pr-str path)))))
      " " message))

(defn- job-row [{:keys [id name schedule active? status reason inference]}]
  (el :tr {}
      (el :td {} (or name id))
      (el :td {} (str schedule))
      (el :td {} (if active? "active" "paused"))
      (el :td {} (badge (status-kind status) (if status (cljs.core/name status) "—")))
      (el :td {} (or reason ""))
      (el :td {} (str/join " · " (keep identity [(:provider inference) (:model inference) (:verdict inference)])))))

(defn- profile-card [{:keys [profile runtime usable? errors warnings problems jobs]}]
  (el :details {:class "card"}
      (el :summary {}
          (el :span {:class "name"} profile)
          (badge (if (= runtime "itonami") "itonami" "hermes") runtime)
          (badge (if usable? "ok" "refused") (if usable? "usable" "invalid"))
          (el :span {:class "counts"} (str errors " errors · " warnings " warnings · " (count jobs) " jobs")))
      (if (seq problems)
        (el :ul {:class "problems"} (map problem-row problems))
        (el :p {:class "muted"} "No problems."))
      (when (seq jobs)
        (el :div {:class "table-wrap"}
            (el :table {}
                (el :thead {} (el :tr {} (map #(el :th {} %) ["Job" "Schedule" "State" "Dry run" "Reason" "Inference"])))
                (el :tbody {} (map job-row jobs)))))))

(defn render! [root rs]
  (set! (.-innerHTML root) "")
  (let [usable (count (filter :usable? rs))]
    (.appendChild root (el :p {:class "summary"}
                           (str (count rs) " profiles · " usable " usable · checked on this device, nothing sent")))
    (doseq [r rs] (.appendChild root (profile-card r)))))

(defn- fail! [root msg]
  (set! (.-innerHTML root) "")
  (.appendChild root (el :p {:class "error"} msg)))

(defn ^:export start []
  (let [root (.getElementById js/document "app")]
    (-> (js/fetch "profiles.json")
        (.then (fn [res] (if (.-ok res) (.json res) (throw (js/Error. (str "profiles.json " (.-status res)))))))
        (.then (fn [data] (render! root (core/reports (js->clj data) readers))))
        (.catch (fn [e] (fail! root (str "Could not load the bundled profiles: " (.-message e))))))))

(.addEventListener js/document "DOMContentLoaded" start)
