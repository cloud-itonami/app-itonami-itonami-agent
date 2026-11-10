(ns itonami.otehon.browser-provider
  "The cloud browser provider registry core: name normalization and
  provider resolution. Faithful port of the pure core of
  itonami.agent.browser-provider (Hermes agent/browser_provider.py +
  agent/browser_registry.py + tools/browser_tool_cloud.py).

  The registry is explicit data — {name {:available? bool}} — never a
  global atom, so the same resolution is what the cases pin. A legacy
  `:is-available` fn entry is still honored with throw->false
  (Hermes is_available_safe: a broken provider must not take the
  resolution down); the host maps plugin fns to `:available?` flags or
  hands them through. Lifecycle (create-session / close-session /
  emergency-cleanup), plugin registration and the local browser stay
  host-side (S6)."
  (:require [clojure.string :as str]))

(def default-provider "local")

(def legacy-preference
  "The only auto-eligible provider names (Hermes _LEGACY_PREFERENCE)."
  ["browser-use" "browserbase"])

(defn normalize-provider
  "Hermes normalize_browser_cloud_provider: trimmed, lower-cased;
  \"local\" when unset or blank."
  [v]
  (let [s (str/trim (str/lower-case (str v)))]
    (if (str/blank? s) default-provider s)))

(defn- available?
  "is_available with exceptions treated as unavailable (Hermes
  is_available_safe). Data entries answer `:available?`; fn entries
  answer `:is-available` and a throw reads false."
  [entry]
  (try (boolean (if (contains? entry :is-available)
                  ((get entry :is-available))
                  (get entry :available?)))
       (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ false)))

(defn resolve-provider
  "The active cloud browser provider NAME for a config value
  (browser.cloud_provider), or nil for local browser mode — the same
  decision the legacy record carries, as data the host resolves
  against its plugin registry. Unset auto-detects; an explicit local
  is never a provider; a configured but unregistered name falls back
  to auto-detect; auto-detect walks ONLY `legacy-preference` filtered
  by availability (no single-eligible shortcut, so a shared Firecrawl
  key never silently routes to a paid cloud browser)."
  [configured registry]
  (let [raw (str/trim (str configured))
        nm (normalize-provider configured)
        auto (fn [] (some (fn [name]
                           (when (available? (get registry name)) name))
                         legacy-preference))]
    (cond
      (= raw "local") nil
      (not (str/blank? raw)) (if (contains? registry nm) nm (auto))
      :else (auto))))
