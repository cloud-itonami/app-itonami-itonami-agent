(ns itonami.otehon.bridge
  "The `bridge` JSONL protocol's pure core: the first line's request gate,
  approval-line routing and result/error row shaping.

  Faithful port of the `start-request` / approval / result branches of
  `bridge` in `src/itonami/agent/main.cljk`. One deliberate divergence:
  the legacy throws ex-info on a refused request (the CLI turns it into an
  error row and exit 1); the twin returns `{:error msg}` with the verbatim
  message so the shared cases can pin the fail-closed gate. Row shaping
  (`result-row` / `error-row`) is byte-identical by construction.

  No host reads: the profile default, the pending approval set and the
  request map are all explicit arguments. No interop, so no `:lpy`
  branch is needed."
  (:require [clojure.string :as str]
            [itonami.otehon.tools :as tools]))

(defn validate-request
  "{:profile p :prompt s :tool-names [...]} for a well-formed first line,
  else {:error msg} with the legacy message verbatim. A request naming a
  tool Kotoba cannot execute is refused, never narrowed."
  [request]
  (let [prompt (get request "prompt")
        tool-names (get request "tool_names")]
    (cond
      (not (string? prompt))
      {:error "bridge request requires a string prompt"}
      (not (vector? tool-names))
      {:error "bridge request requires an explicit tool_names array"}
      :else
      (let [unsupported (tools/unsupported-tools tool-names)]
        (if (seq unsupported)
          {:error (str "Kotoba agent does not implement requested tools: "
                       (str/join ", " unsupported))}
          {:profile (or (get request "profile") "default")
           :prompt prompt
           :tool-names tool-names})))))

(defn approval-outcome
  "{:request-id id :allow bool} when MESSAGE answers a pending approval,
  else nil. `pending-ids` is the set of request ids still awaiting a
  choice; only \"once\" allows, only \"deny\"/\"once\" resolve."
  [message pending-ids]
  (let [id (get message "request_id")
        choice (get message "choice")]
    (when (and (= (get message "type") "approval")
               (contains? #{"once" "deny"} choice)
               (contains? (set pending-ids) id))
      {:request-id id :allow (= choice "once")})))

(defn result-row
  "The JSONL result row for a finished run, verbatim: usage counters
  default to 0, a failed run without an error map reports its exit
  reason as the error."
  [result]
  (let [usage (or (:usage result) {})
        exit-reason (:exit-reason result)]
    {"type" "result"
     "result" {"final_response" (:final result)
               "completed" (= "completed" exit-reason)
               "exit_reason" exit-reason
               "error" (or (get-in result [:error :error])
                           (when-not (= "completed" exit-reason) exit-reason))
               "usage" {"input_tokens" (or (get usage "prompt_tokens") 0)
                        "output_tokens" (or (get usage "completion_tokens") 0)
                        "total_tokens" (or (get usage "total_tokens") 0)}
               "model" (:model result)
               "provider" (:provider result)}}))

(defn error-row
  "The JSONL error row: the CLI prints one and exits 1."
  [message]
  {"type" "error" "error" message})

(defn run-options
  "The data half of the agent-loop options `start-request` builds, as
  a plan for the host to execute with injected seams (`:before-tool`,
  `:tool-overrides`, `:on-event` and the run itself stay host-side).
  `cwd` is explicit: the legacy defaults to the process cwd when the
  request carries none, and the twin takes it as an argument so no
  host read hides inside the unit."
  [request cwd]
  {:model (get request "model")
   :provider (get request "provider")
   :pinned? (boolean (get request "model"))
   :tool-names (get request "tool_names")
   :history (get request "history")
   :instructions (get request "instructions")
   :cwd (or (get request "cwd") cwd)
   :platform "api_server"
   :load-context? true
   :max-turns (get request "max_turns")
   :budget-s (get request "budget_s")})
