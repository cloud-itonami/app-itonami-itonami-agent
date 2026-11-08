(ns itonami.otehon.turn
  "One ad-hoc turn (`-z`): the toolset gate, without running anything.

  The same gates a cron dry-run applies, for a prompt with no job: the
  profile must be an itonami profile and check-clean, the resolved inference
  target must pass the itonami.edn :inference verdict, every named
  capability must be :autonomous for the turn to proceed (:voice-required /
  :approval-required hold it for the owner, :blocked refuses it), and the
  toolsets are the cron-deny rules with an empty job (messaging and clarify
  need a user; cronjob stays off unless the profile allows agent
  scheduling).

  Pure: the caller hands in the loaded profile. No prompt is sent, no model
  is called, nothing is written. The host runs the gated turn."
  (:require [clojure.string :as str]
            [itonami.otehon.profile :as profile]
            [itonami.otehon.rail :as rail]
            [itonami.otehon.yakuwari :as yakuwari]
            [itonami.otehon.cron :as cron]))

(defn- refuse [reason & [more]]
  (merge {:status :refused :reason reason} more))

(defn turn-gate
  "Gate one turn of `prof` running `prompt`. opts :capabilities — the
  capabilities this turn would exercise, decided by yakuwari.edn."
  [prof prompt {:keys [capabilities]}]
  (let [problems (profile/check prof)
        errs (profile/errors problems)
        {:keys [config manifest yakuwari]} prof]
    (cond
      (not= "itonami" (:runtime prof))
      (refuse "rail/itonami-without-manifest" {:detail "a Hermes profile; itonami-agent runs itonami profiles only"})

      (seq errs) (refuse "profile/invalid" {:problems errs})

      (str/blank? (str prompt)) (refuse "turn/empty-prompt")

      :else
      (let [target (rail/resolve-job config {})
            verdict (rail/inference-verdict (:inference manifest) target)
            decisions (mapv #(yakuwari/decide yakuwari (:authority manifest) %) capabilities)
            worst (cond (some #(= :blocked (:decision %)) decisions) :blocked
                        (some #(not= :autonomous (:decision %)) decisions) :held
                        :else nil)
            base {:profile (:name prof)
                  :runtime (:runtime prof)
                  :prompt-chars (count (str prompt))
                  :inference (assoc target :verdict verdict)
                  :toolsets (cron/tool-sets-for config {})
                  :reasoning-effort (cron/effective-reasoning-effort config {})
                  :decisions decisions
                  :side-effects :none}]
        (cond
          (not= verdict "rail/ok") (merge base (refuse verdict))
          (= worst :blocked) (merge base (refuse "authority/blocked"))
          (= worst :held) (merge base {:status :held :reason "authority/needs-owner"})
          :else (merge base {:status :ok}))))))
