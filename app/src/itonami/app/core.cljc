(ns itonami.app.core
  "What the app shows for one bundled profile: the Otehon `check` and a
  `dry-run` of each cron job. Pure. The caller supplies the profile files as
  {relative-path text} and the two format readers, exactly as the Otehon CLI
  does, so this namespace runs unchanged on ClojureScript (the app) and
  Babashka (the oracle test).

  No capability is named for a dry run: Hermes jobs carry no capability list
  and the app does not invent authority (otehon/README.md, Decisions)."
  (:require [itonami.otehon.profile :as profile]
            [itonami.otehon.cron :as cron]))

(defn load
  "One profile from its files. `files` maps a relative path to its text."
  [profile-name files {:keys [parse-edn parse-json]}]
  (profile/load-profile {:name profile-name
                         :read-text (fn [f] (get files f))
                         :parse-edn parse-edn
                         :parse-json parse-json}))

(defn- job-row [prof job]
  (let [ref (or (get job "id") (get job "name"))
        plan (profile/dry-run prof ref {:capabilities []})]
    {:id (get job "id")
     :name (get job "name")
     :schedule (cron/schedule-display job)
     :active? (boolean (cron/active? job))
     :status (:status plan)
     :reason (:reason plan)
     :inference (select-keys (:inference plan) [:provider :model :host :verdict])}))

(defn report
  "The view of one profile: runtime, check counts, every problem and one row
  per cron job. Never throws on bad profile text."
  [profile-name files readers]
  (let [prof (load profile-name files readers)
        problems (profile/check prof)
        summary (profile/summary problems)]
    {:profile profile-name
     :runtime (:runtime prof)
     :present (:present prof)
     :usable? (zero? (:errors summary))
     :errors (:errors summary)
     :warnings (:warnings summary)
     :problems (mapv #(select-keys % [:level :file :path :message]) problems)
     :jobs (mapv #(job-row prof %) (:jobs prof))}))

(defn reports
  "Reports for every bundled profile, sorted by name. `bundle` maps a profile
  name to its files."
  [bundle readers]
  (mapv (fn [[n files]] (report n files readers)) (sort-by key bundle)))
