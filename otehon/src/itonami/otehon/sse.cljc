(ns itonami.otehon.sse
  "Portable SSE stream parsing core of `itonami.agent.provider` (the
  inference rail) for the Otehon .cljc core, with one pinned answer on
  every runner.

  Ported: `sse-acc`, `timings->usage`, `apply-tool-delta`, `sse-apply`,
  `sse-line`, `sse-complete?`, `sse-finish`, `sse-parse-text`,
  `cooldown-ms`, `hop-error`. The `:sse` shared-case op exercises these.

  The network path (`fetch-json`, `fetch-sse`, `read-sse-lines`),
  the breaker file/clock (`read-health`, `record-authority!`,
  `breaker-open?`) and the retry chain (`complete-hop`) stay host-side:
  they read files, the clock and the network, so no shared case can
  pin them.

  Portability (all verified on bb/nbb/basilisp before pinning):
  - `:tools` is a plain map, not a `sorted-map` (basilisp has no
    sorted-map at all); `sse-finish` iterates `(sort-by key tools)`,
    which is exactly the legacy emission order. Intermediate
    accumulators are never pinned, only finished bodies.
  - `str/split` is always 2-arity: basilisp's 3-arity limit argument
    does not split. Empty lines are no-ops for `sse-line`, so the
    trailing-empty difference is unobservable in the fold.
  - `hio/parse-json` throws on malformed input on every runner, so
    `sse-line` keeps the legacy try/catch."
  (:require [clojure.string :as str]
            [itonami.otehon.io :as hio]))

(defn sse-acc
  []
  {:seen? false :done? false :content "" :reasoning "" :tools {}
   :finish nil :model nil :usage nil :error nil})

(defn- timings->usage
  [t]
  (let [pn (get t "prompt_n") cn (get t "predicted_n")]
    (when (or pn cn)
      {"prompt_tokens" (or pn 0) "completion_tokens" (or cn 0) "total_tokens" (+ (or pn 0) (or cn 0))})))

(defn- apply-tool-delta
  [acc tc]
  (let [i (or (get tc "index") 0)
        f (get tc "function")]
    (update-in acc [:tools i]
               (fn [t]
                 (let [t (or t {:id nil :name "" :args ""})]
                   (cond-> t
                     (get tc "id") (assoc :id (get tc "id"))
                     (string? (get f "name")) (update :name str (get f "name"))
                     (string? (get f "arguments")) (update :args str (get f "arguments"))))))))

(defn sse-apply
  "Fold one parsed stream chunk (string-keyed map) into the accumulator."
  [acc chunk]
  (let [choice (first (get chunk "choices"))
        delta (get choice "delta")
        acc (cond-> acc
              (get chunk "error") (assoc :error (get chunk "error"))
              (get chunk "model") (assoc :model (get chunk "model"))
              (get chunk "usage") (assoc :usage (get chunk "usage"))
              (and (nil? (get chunk "usage")) (get chunk "timings"))
              (assoc :usage (timings->usage (get chunk "timings")))
              choice (assoc :seen? true)
              (get choice "finish_reason") (assoc :finish (get choice "finish_reason"))
              (string? (get delta "content")) (update :content str (get delta "content"))
              (string? (get delta "reasoning_content")) (update :reasoning str (get delta "reasoning_content")))]
    (reduce apply-tool-delta acc (get delta "tool_calls"))))

(defn sse-line
  "Fold one raw SSE line. Comment lines (keep-alives), blank lines, `[DONE]`
  and malformed JSON leave the accumulator alone."
  [acc line]
  (let [line (str/replace (str line) #"\r$" "")]
    (if-not (str/starts-with? line "data:")
      acc
      (let [payload (str/trim (subs line 5))]
        (cond
          (str/blank? payload) acc
          (= payload "[DONE]") (assoc acc :done? true)
          :else
          (let [chunk (try (hio/parse-json payload) (catch #?(:lpy python/Exception :clj Exception :cljs :default) _ nil))]
            (if (map? chunk) (sse-apply acc chunk) acc)))))))

(defn sse-complete?
  "Did the stream end the way a server ends one: a finish_reason, `[DONE]`, or an
  error chunk? A connection that just stops (gateway cut, reset) has none of
  these, and what was read is a fragment, not a reply."
  [{:keys [done? finish error]}]
  (boolean (or done? finish error)))

(defn sse-finish
  "The accumulator as a one-shot chat-completion body."
  [{:keys [seen? content reasoning tools finish model usage error]}]
  (cond
    error {"error" error}
    (not seen?) {"choices" []}
    :else
    {"choices" [{"index" 0
                 "finish_reason" finish
                 "message" (cond-> {"role" "assistant"
                                    "content" (when-not (str/blank? content) content)}
                             (not (str/blank? reasoning)) (assoc "reasoning_content" reasoning)
                             (seq tools) (assoc "tool_calls"
                                                (mapv (fn [[_ {:keys [id name args]}]]
                                                        {"id" id "type" "function"
                                                         "function" {"name" name "arguments" args}})
                                                      (sort-by key tools))))}]
     "model" model
     "usage" usage}))

(defn sse-parse-text
  "A whole SSE body -> one-shot body map (what the network path builds incrementally)."
  [text]
  (sse-finish (reduce sse-line (sse-acc) (str/split (str text) #"\n"))))

(defn cooldown-ms
  "How long an authority is skipped after a failure of this kind."
  [{:keys [status error]}]
  (cond
    (= 402 status) (* 30 60000)
    (#{401 403} status) (* 30 60000)
    (= 429 status) (* 2 60000)
    :else (* 5 60000)))

(defn hop-error
  [hop status msg]
  {:provider (:provider hop) :model (:model hop) :status status :error (subs (str msg) 0 (min 400 (count (str msg))))})
