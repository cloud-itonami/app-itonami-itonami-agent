;; Build an itonami.autonomy-snapshot.v1 file for the Itonami app's
;; Autonomy view from job receipts and job observations.
;;
;;   bb -cp src scripts/autonomy-snapshot.clj OUT.json RECEIPT-DIR... -- OBSERVATION.json...
;;
;; Reads only; writes OUT.json. The output holds bot ids, job ids, Linear issue
;; ids, counts and times — no prompts, results or secrets. It is private
;; operational data: keep it out of the repository.
(require '[cheshire.core :as json]
         '[clojure.java.io :as io]
         '[itonami.app.autonomy :as autonomy])

(let [[out & more] *command-line-args*
      [dirs [_ & obs]] (split-with #(not= "--" %) more)
      read-json (fn [f] (try (json/parse-string (slurp f)) (catch Exception _ nil)))
      receipts (for [d dirs
                     f (sort (.listFiles (io/file d)))
                     :when (and (.isFile f) (.endsWith (.getName f) ".json")
                                (not= "current.json" (.getName f)))]
                 (read-json f))
      observations (keep read-json obs)
      snap (autonomy/activity-from-records (vec receipts) (vec observations)
                                           (str (java.time.Instant/now)))]
  (when-not out (binding [*out* *err*] (println "usage: OUT.json RECEIPT-DIR... -- OBSERVATION.json...")) (System/exit 64))
  (spit out (json/generate-string snap))
  (println (format "%d receipts, %d unread, %d bots, %d hours, observed %s -> %s"
                   (count receipts) (get snap "unreadReceipts") (count (get snap "bots"))
                   (count (get snap "hourly")) (get snap "observedAt") out)))
