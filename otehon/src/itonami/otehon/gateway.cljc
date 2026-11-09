;; Messaging gateway transport surface (Otehon port of the pure core of
;; src/itonami/agent/gateway.cljk: platform values, delivery transports,
;; adapter resolution and the watcher send seam).
;;
;; Resolution is pure data over injected adapter/config maps; the send
;; fns themselves are called synchronously (shared cases pass stubs —
;; the live promise transport stays host-side). The completion fan-in
;; (batching, timers) and the serve loop need process.cljk and a live
;; runtime and stay deferred (see COMPAT.md).
(ns itonami.otehon.gateway
  (:require [clojure.string :as str]))

(def relay-platform "relay")

(defn platform-value [platform]
  (let [raw (if (map? platform) (or (:value platform) platform) platform)]
    (str/lower-case (str/trim (str (or raw ""))))))

(defn non-conversational-metadata [metadata platform]
  (if (not= (platform-value platform) "discord")
    metadata
    (assoc (or metadata {}) "non_conversational" true)))

(defn delivery-transport [adapter config transport-platform]
  {:adapter adapter
   :config config
   :transport-platform (platform-value transport-platform)})

(defn relay? [transport]
  (= relay-platform (platform-value (:transport-platform transport))))

(defn- platform-enabled? [config]
  (or (nil? config) (boolean (:enabled config))))

(defn resolve-delivery-transport
  "A concrete native adapter always wins; Relay is eligible only when
  its transport advertises that it fronts the logical platform. An
  explicitly disabled native adapter never shadows an enabled Relay
  transport. Answers the DeliveryTransport or nil."
  [platform platforms adapters]
  (let [p (platform-value platform)
        native (get adapters p)
        relay (get adapters relay-platform)
        fronts (get relay :fronts-platform)]
    (cond
      (and (some? native) (platform-enabled? (get platforms p)))
      (delivery-transport native (get platforms p) p)

      (and (some? relay)
           (platform-enabled? (get platforms relay-platform))
           (fn? fronts)
           (boolean (fronts platform)))
      (delivery-transport relay (get platforms relay-platform) relay-platform)

      :else nil)))

(defn resolve-injection-adapter
  "The alias-aware transport resolver first (one Relay fronts N logical
  platforms; native wins), the literal platform-value scan as fallback."
  [platforms adapters platform-name]
  (or (:adapter (resolve-delivery-transport platform-name platforms adapters))
      (get adapters platform-name)))

(defn- warn-delivery-error [warn-fn e]
  (warn-fn (str "Watcher delivery error: " (or (ex-message e) (str e)))))

(defn send-watcher-message!
  "Resolve the platform's injection adapter and, `if adapter and
  chat_id`, send with non-conversational metadata. A missing adapter or
  a blank chat id sends nothing. Delivery errors are suppressed through
  warn-fn and answer nil; otherwise answers the send result."
  [{:keys [platforms adapters warn-fn]} platform-name chat-id thread-id message-text]
  (let [adapter (resolve-injection-adapter platforms adapters platform-name)
        warn (or warn-fn (fn [_]))]
    (when (and (some? adapter) (not= "" (str chat-id)))
      (let [send-meta (when-not (= "" (str thread-id)) {"thread_id" thread-id})
            metadata (non-conversational-metadata send-meta platform-name)]
        (try
          ((:send adapter) (str chat-id) (str message-text) metadata)
          (catch #?(:lpy python/Exception :clj Exception :cljs :default) e
            (warn-delivery-error warn e)
            nil))))))

(defn send-hook
  "The live `:send!` binding shape over this adapter surface."
  [surface]
  (fn [platform-name chat-id thread-id message-text watcher]
    (send-watcher-message! surface platform-name chat-id thread-id message-text)))
