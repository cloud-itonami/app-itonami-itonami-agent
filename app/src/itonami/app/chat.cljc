(ns itonami.app.chat
  "Itonami Desktop conversation logic, pure. Request plans follow
  `itonami.otehon.conversation/request-plan` exactly (that namespace needs the
  host `fs`, so the browser build cannot load it; the Babashka test checks the
  two agree). Response checks follow the Awai shell's bot-chat client: a reply
  counts only when the Agent says it stored and read it back.

  The Agent owns conversation history. Nothing here persists it."
  (:require [clojure.string :as str]))

(def model "muse-spark-1.3")
(def max-text 8192)
(def max-reply 32768)
(def max-title 120)

(defn uuid? [s]
  (boolean (and (string? s)
                (re-matches #"[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}" s))))

(defn valid-session? [id] (or (= "default" id) (uuid? id)))
(defn valid-bot? [id] (boolean (and (string? id) (re-matches #"profile:[A-Za-z0-9_.-]+" id))))
(defn bot-id [profile-name] (str "profile:" profile-name))

(defn- refuse [reason] {:error reason})

(defn plan
  "One Agent request as {:operation :path :body}, or {:error reason}.
  Operations: list-bots, list-sessions, create-session, read-history,
  send-message."
  [operation {:keys [bot session request text title]}]
  (let [session (or session "default")]
    (cond
      (= operation :list-bots) {:operation "list-bots" :path "/v1/bot-chat/bots" :body {}}
      (not (valid-bot? bot)) (refuse "invalid Bot identity")
      (not (valid-session? session)) (refuse "invalid session identity")
      :else
      (case operation
        :list-sessions {:operation "list-sessions" :path "/v1/bot-chat/sessions"
                        :body {"botId" bot "action" "list"}}
        :create-session (if (or (= session "default") (not (string? title))
                                (str/blank? title) (> (count title) max-title))
                          (refuse "new requires an explicit session UUID and bounded title")
                          {:operation "create-session" :path "/v1/bot-chat/sessions"
                           :body {"botId" bot "action" "create" "sessionId" session "title" title}})
        :read-history {:operation "read-history" :path "/v1/bot-chat/history"
                       :body {"botId" bot "sessionId" session}}
        :send-message (if (not (and (uuid? request) (string? text) (not (str/blank? text))
                                    (<= (count text) max-text)))
                        (refuse "send requires explicit model, request UUID and bounded text")
                        {:operation "send-message" :path "/v1/bot-chat/turn"
                         :body {"schema" "awai.bot-chat-request.v1" "botId" bot "sessionId" session
                                "model" model "requestId" request "text" text}})
        (refuse "unknown conversation operation")))))

(defn- stored? [v] (and (= "itonami-agent" (get-in v ["store" "owner"]))
                        (true? (get-in v ["store" "readback"]))))

(defn check-reply
  "The Agent's answer to a send, or {:error}. `v` is parsed JSON (string keys)."
  [request v]
  (let [b (:body request)]
    (if (and (map? v)
             (= (get v "requestId") (get b "requestId"))
             (= (get v "botId") (get b "botId"))
             (= (get v "sessionId" "default") (get b "sessionId"))
             (= "meta" (get v "provider"))
             (= model (get v "model"))
             (stored? v)
             (= "completed" (get v "state"))
             (string? (get v "text"))
             (not (str/blank? (get v "text")))
             (<= (count (get v "text")) max-reply))
      {:text (get v "text") :request (get v "requestId")}
      {:error "reply-unconfirmed"})))

(defn check-history [request v]
  (let [b (:body request)
        msgs (get v "messages")]
    (if (and (map? v)
             (= (get v "botId") (get b "botId"))
             (= (get v "sessionId" "default") (get b "sessionId"))
             (stored? v)
             (sequential? msgs)
             (every? #(and (#{"user" "assistant"} (get % "role")) (string? (get % "text"))) msgs))
      {:messages (mapv (fn [m] {:role (keyword (get m "role")) :text (get m "text")
                                :at (get m "createdAt")}) msgs)}
      {:error "history-unconfirmed"})))

(defn check-sessions [request v]
  (let [b (:body request)
        create? (= "create" (get b "action"))]
    (if (and (map? v) (= (get v "botId") (get b "botId")) (stored? v)
             (if create?
               (= (get-in v ["session" "sessionId"]) (get b "sessionId"))
               (sequential? (get v "sessions"))))
      (if create?
        {:session {:id (get b "sessionId") :title (get b "title")}}
        {:sessions (->> (get v "sessions")
                        (filter #(valid-session? (get % "sessionId")))
                        (mapv (fn [s] {:id (get s "sessionId") :title (get s "title")
                                       :updated (get s "updatedAt") :count (get s "messageCount")})))})
      {:error "sessions-unconfirmed"})))

(defn check-bots [v]
  (let [bots (get v "bots")]
    (if (and (map? v) (= "awai.agent-directory.v1" (get v "schema")) (sequential? bots)
             (every? #(and (valid-bot? (get % "id")) (string? (get % "name"))
                           (true? (get-in % ["chatPersona" "available"]))
                           (boolean (re-matches #"[a-f0-9]{64}" (str (get-in % ["chatPersona" "sourceSha256"])))))
                     bots))
      {:bots (mapv (fn [b] {:id (get b "id") :name (get b "name")}) bots)}
      {:error "directory-unconfirmed"})))

;; ------------------------------------------------------------ transcript

(defn outcome-message
  "What the person sees when a call did not return a confirmed result.
  `outcome` is the transport's: denied, network, http, invalid."
  [operation outcome status]
  (let [send? (= operation "send-message")]
    (case outcome
      "denied" "Not sent: you denied this request."
      "http" (cond (= status 403) "The Agent needs the owner's sign-in (403). Nothing was retried."
                   (= status 404) "This bot is not in the Agent's catalog (404). Nothing was retried."
                   send? (str "The Agent answered " status ". The result is unconfirmed; nothing was retried.")
                   :else (str "The Agent answered " status "."))
      "network" (if send?
                  "No answer reached this device. The result is unconfirmed; nothing was retried."
                  "The Agent could not be reached.")
      "unconfirmed" "The Agent's answer did not match the request. The result is unconfirmed; nothing was retried."
      "Request refused by the app.")))

(defn add-pending
  "Append the person's message and a pending reply to a transcript."
  [transcript request text]
  (conj (vec transcript)
        {:role :user :text text :request request}
        {:role :assistant :state :pending :request request}))

(defn settle
  "Replace the pending/unconfirmed reply for `request` with the result:
  {:text} confirmed, or {:state :unconfirmed :note}. A denied first attempt
  drops the pair, since nothing was sent."
  [transcript request result]
  (let [idx (first (keep-indexed (fn [i m] (when (and (= :assistant (:role m)) (= request (:request m))) i))
                                 transcript))]
    (cond
      (nil? idx) (vec transcript)
      (:drop result) (vec (remove #(= request (:request %)) transcript))
      (:text result) (assoc (vec transcript) idx {:role :assistant :text (:text result) :request request})
      :else (assoc (vec transcript) idx {:role :assistant :state :unconfirmed :note (:note result)
                                         :request request}))))

(defn session-title
  "A bounded title for a new session from the first line of a prompt."
  [text]
  (let [line (str/trim (first (str/split-lines (str text))))
        line (if (str/blank? line) "New conversation" line)]
    (if (> (count line) 60) (str (subs line 0 59) "…") line)))
