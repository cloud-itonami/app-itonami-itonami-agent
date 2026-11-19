;; Build an awai.bot-fleet.v1 catalog and an itonami.autonomy-snapshot.v1
;; snapshot for the Itonami app's Autonomy view from developer-bot task
;; directories (isolated clones holding .agent-output/).
;;
;;   bb -cp src scripts/dev-bot-snapshot.clj CATALOG.json SNAPSHOT.json ROOT... [-- MAP.json]
;;
;; ROOTs are scanned recursively for directories holding `.agent-output/`;
;; nothing outside the ROOTs is read. MAP.json optionally maps Linear issue
;; keys to their project (a name string or {"id" "name"}); without it there
;; is one circle per repository. Reads only; writes CATALOG.json and
;; SNAPSHOT.json. The output holds bot ids, run times, Linear issue ids,
;; token counts and delivery facts — no prompts, results or secrets. It is
;; private operational data: keep it out of the repository.
(require '[cheshire.core :as json]
         '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[itonami.app.devbots :as devbots])

(let [[catalog-out snapshot-out & more] *command-line-args*
      [roots [_ & map-files]] (split-with #(not= "--" %) more)
      fs {:is-dir? (fn [p] (.isDirectory (io/file p)))
          :list (fn [p] (let [f (io/file p)]
                          (when (.isDirectory f) (vec (.list f)))))
          :read-file (fn [p] (try (slurp p) (catch Exception _ nil)))}
      parse-json (fn [s] (json/parse-string s))
      issue->circle (if-let [f (first map-files)]
                      (try (json/parse-string (slurp f)) (catch Exception _ {}))
                      {})
      issue->circle (if (map? issue->circle) issue->circle {})
      dirs (devbots/find-task-dirs fs roots)
      tasks (mapv #(devbots/read-task-files fs %) dirs)
      built (devbots/build-fleet {:now-ms (System/currentTimeMillis)
                                  :issue->circle issue->circle
                                  :tasks tasks
                                  :parse-json parse-json})]
  (when (or (nil? catalog-out) (nil? snapshot-out) (empty? roots))
    (binding [*out* *err*]
      (println "usage: dev-bot-snapshot.clj CATALOG.json SNAPSHOT.json ROOT... [-- MAP.json]"))
    (System/exit 64))
  (spit catalog-out (json/generate-string (:catalog built)))
  (spit snapshot-out (json/generate-string (:snapshot built)))
  (let [snap (:snapshot built)]
    (println (format "%d task dirs, %d unread tasks, %d unread runs, %d circles, %d bots -> %s %s"
                     (count tasks) (get snap "unreadTasks") (get snap "unreadReceipts")
                     (dec (count (get-in (:catalog built) ["organization" "circles"])))
                     (count (get snap "bots")) catalog-out snapshot-out)))
  (doseq [p (:problems built)] (println "note:" p)))
