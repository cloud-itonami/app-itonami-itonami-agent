(ns itonami.otehon.doctor
  "The `doctor` findings: what is configured and what is missing, named
  by code. Ported from main.cljk (doctor-cmd, profile-readiness-issues,
  kotoba-job-issues) plus platform_adapters/token-env-name and
  cron.cljk (script-runtime-issues, hy-dependencies shape).
  Read-only: nothing is started and no profile changes.
  Toolset/browser/oauth finding kinds stay slice S6 (they need the S6
  registries); only their S6-free neighbours are reported here."
  (:require [clojure.string :as str]
            [itonami.otehon.exec :as exec]))

(def token-env-defaults
  "The env var each lane's token comes from when delivery.<lane>.token_env
  does not name one. Verbatim from platform_adapters."
  {"telegram" "TELEGRAM_BOT_TOKEN"
   "slack" "SLACK_BOT_TOKEN"
   "discord" "DISCORD_BOT_TOKEN"
   "mattermost" nil
   "matrix" "MATRIX_ACCESS_TOKEN"
   "feishu" "FEISHU_TENANT_ACCESS_TOKEN"
   "dingtalk" "DINGTALK_ACCESS_TOKEN"
   "wecom" "WECOM_ACCESS_TOKEN"
   "whatsapp" "WHATSAPP_ACCESS_TOKEN"})

(defn token-env-name
  "The env var that carries this lane's token, or nil when the lane needs
  no token. Verbatim from platform_adapters/token-env-name."
  [lane config]
  (let [lane (str/lower-case (str lane))
        c (get-in config ["delivery" lane])]
    (when-not (or (= "mattermost" lane)
                  (and (= "discord" lane)
                       (not (str/blank? (str (get c "webhook_url"))))))
      (or (get c "token_env") (get token-env-defaults lane)))))

(defn- active-job?
  "A job the readiness gate inspects. Verbatim from main.cljk."
  [job]
  (and (get job "enabled" true)
       (not (contains? #{"paused" "completed" "disabled"} (get job "state" "scheduled")))))

(defn- script-findings
  "Unsafe/missing script refs in one job. Verbatim from
  cron.cljk (script-runtime-issues), minus the Hy part."
  [home job {:keys [file? symlink?]}]
  (let [scripts (str home "/scripts")]
    (mapcat (fn [field]
              (let [path (get job field)]
                (when (some? path)
                  (cond
                    (not (exec/script-path-safe? scripts path symlink?))
                    [{:code :unsafe_script_path :detail (str field "=" path)}]
                    (not (file? (str scripts "/" path)))
                    [{:code :script_not_found :detail (str field "=" path)}]
                    :else []))))
            ["script" "monitor_script"])))

(defn- hy-findings
  "The Hy runtime finding for one job's .hy refs. `hy-deps` nil means no
  Hy runtime was resolved. Verbatim from cron.cljk."
  [job hy-deps]
  (let [refs (for [field ["script" "monitor_script"]
                   :let [path (get job field)]
                   :when (some? path)]
               [field path])
        hy-fields (filter (fn [[_ path]]
                            (and (string? path) (re-find #"(?i)\.hy$" path)))
                          refs)]
    (when (and (seq hy-fields) (nil? hy-deps))
      [{:code :hy_runtime_unavailable
        :detail (str "Hy runtime not found for "
                     (str/join ", " (map (fn [[field path]] (str field "=" path))
                                         hy-fields)))}])))

(defn- delivery-finding
  "The delivery-adapter finding for one job. Verbatim from
  main.cljk (kotoba-job-issues): any non-local lane is named."
  [job]
  (let [lane (or (get job "deliver") "local")]
    (when (not= "local" (str/lower-case (str lane)))
      [{:code :delivery_adapter :detail (str lane)}])))

(defn job-findings
  "Readiness findings for one active job: delivery lane, script refs, Hy
  refs. Toolset/browser/oauth kinds stay slice S6."
  [job home probes hy-deps]
  (concat (delivery-finding job)
          (script-findings home job probes)
          (hy-findings job hy-deps)))

(defn script-runtime-issues
  "Readiness issues for one job's script paths and Hy entry points.
  Verbatim from cron.cljk (path issues plus Hy issues, no delivery
  lane — the lane lives in job-findings)."
  [job home probes hy-deps]
  (concat (script-findings home job probes)
          (hy-findings job hy-deps)))

(defn readiness-issues
  "Job-level readiness issues for a profile: what `profile readiness`,
  `adopt` and `hy-migrate` refuse on. Verbatim from main.cljk
  profile-readiness-issues minus the S6-owned kotoba-job-issues kinds
  (toolsets/tools, browser backends, oauth cache)."
  [jobs home probes hy-deps]
  (vec (mapcat (fn [job]
                 (when (and (map? job) (active-job? job))
                   (map #(assoc % :job (get job "id" "(unknown)"))
                        (job-findings job home probes hy-deps))))
               (or jobs []))))

(defn profile-findings
  "All doctor findings for a profile. `inputs`: :config, :home, :jobs,
  :env (host env map for token checks), :model-override (CLI --model),
  :paused-record (pause record string or nil), :state-db? (state.db
  exists), :hy-deps (nil or string), :probes {:file? :symlink?}."
  [{:keys [config home jobs]} {:keys [env model-override paused-record
                                      state-db? hy-deps probes]}]
  (let [readiness (readiness-issues jobs home probes hy-deps)
        model-issue (when (and (str/blank? (str (get-in config ["model" "default"])))
                               (str/blank? (str (get-in config ["cron" "model"])))
                               (str/blank? (str model-override)))
                      {:code :no-model
                       :detail "model.default is not set — resolution is unset"})
        db-issue (when-not state-db?
                   {:code :no-state-db
                    :detail "state.db missing — sessions / session_search will be empty"})
        pause-note (when paused-record
                     {:code :paused
                      :detail (str "emergency stop is set (" paused-record ")")})
        token-issues (when (map? (get config "delivery"))
                       (for [[lane _] (get config "delivery")
                             :let [envn (token-env-name lane config)]
                             :when (and envn (str/blank? (str (get env envn))))]
                         {:code :missing-token
                          :detail (str "delivery." lane " needs env " envn " (unset)")}))]
    (vec (concat readiness
                 (keep identity [model-issue db-issue pause-note])
                 token-issues))))

(defn render
  "Findings to CLI lines + exit: named lines plus the count, or \"ok\"."
  [findings]
  (if (seq findings)
    {:exit 1
     :lines (conj (mapv (fn [f] (str (name (:code f)) ": " (:detail f))) findings)
                  (str (count findings) " finding(s)"))}
    {:exit 0 :lines ["ok"]}))
