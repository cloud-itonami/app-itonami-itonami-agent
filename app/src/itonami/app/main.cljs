(ns itonami.app.main
  "Itonami Desktop: a chat-first window with Sessions and Bots in the sidebar
  (a drawer on narrow screens), the conversation, a composer and a status bar.
  Bots come from the profiles bundled with the app (checked on the device by
  the Otehon core) and, when the person asks, from the Agent's directory.
  Every Agent request goes through `globalThis.itonamiAgent` (agent-client.js,
  the shared consent broker): the person approves each one, denying sends
  nothing, and nothing is retried automatically."
  (:refer-clojure :exclude [uuid])
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [itonami.app.core :as core]
            [itonami.app.chat :as chat]
            [itonami.app.autonomy :as autonomy]))

(def readers
  {:parse-edn reader/read-string
   :parse-json (fn [s] (js->clj (.parse js/JSON s)))})

(defonce state
  (atom {:tab :bots
         :view :chat
         :drawer? false
         :reports []
         :agent-bots nil
         :bot nil
         :sessions {}
         :session "default"
         :titles {}
         :transcript []
         :busy nil
         :notice nil
         :sent []
         :recall nil
         ;; Autonomy: files the person imported, kept in memory only.
         :org nil
         :board nil
         :activity nil
         :sources {}
         :org-q ""
         :circle nil}))

;; ------------------------------------------------------------ dom helpers

(defn- el [tag attrs & children]
  (let [n (.createElement js/document (name tag))]
    (doseq [[k v] attrs :when (some? v)]
      (cond (= k :class) (set! (.-className n) v)
            (= k :on-click) (.addEventListener n "click" v)
            (true? v) (.setAttribute n (name k) "")
            (false? v) nil
            :else (.setAttribute n (name k) (str v))))
    (doseq [c (flatten children) :when (some? c)]
      (.appendChild n (if (string? c) (.createTextNode js/document c) c)))
    n))

(defn- $ [id] (.getElementById js/document id))
(defn- mount! [id node] (let [root ($ id)] (.replaceChildren root node)))
(defn- badge [kind text] (el :span {:class (str "badge " kind)} text))

(defn- uuid [] (.randomUUID js/crypto))

;; ------------------------------------------------------------ derived data

(defn- local-bots []
  (for [r (:reports @state)]
    {:id (chat/bot-id (:profile r)) :name (:profile r) :report r}))

(defn bots
  "Bundled profiles plus the Agent directory, one row per bot id."
  []
  (let [agent (into {} (map (juxt :id identity)) (:agent-bots @state))
        local (into {} (map (juxt :id identity)) (local-bots))]
    (->> (merge-with merge local (update-vals agent #(assoc % :agent? true)))
         vals
         (sort-by :name))))

(defn- bot-name [id] (or (:name (first (filter #(= id (:id %)) (bots)))) (some-> id (subs 8))))

(defn- initials [n]
  (let [parts (remove str/blank? (str/split (str n) #"[-_. ]+"))]
    (str/upper-case (apply str (map first (take 2 (if (seq parts) parts ["?"])))))))

(defn- hue [s] (mod (reduce (fn [h c] (+ (* h 31) (.charCodeAt c 0))) 7 (str s)) 360))

(defn- avatar [id n]
  ;; CSSOM, not a style attribute: the CSP (style-src 'self') blocks inline styles.
  (doto (el :span {:class "avatar" :aria-hidden "true"} (initials n))
    (-> .-style (.setProperty "--h" (str (hue id))))))

;; ------------------------------------------------------------ text rendering

(defn- inline-code [s]
  (let [parts (str/split s #"`")]
    (map-indexed (fn [i p] (if (odd? i) (el :code {} p) p)) parts)))

(defn- rich-text
  "Paragraphs, ``` fenced code and `inline code`. Text only; never HTML."
  [text]
  (let [blocks (str/split (str text) #"```")]
    (el :div {:class "rich"}
        (map-indexed
         (fn [i b]
           (if (odd? i)
             (el :pre {} (el :code {} (str/replace b #"^[a-zA-Z0-9_-]*\n" "")))
             (for [para (remove str/blank? (str/split b #"\n{2,}"))]
               (el :p {} (interpose (el :br {}) (map inline-code (str/split-lines para)))))))
         blocks))))

;; ------------------------------------------------------------ agent calls

(declare render!)

(defn- set-state! [f & args] (apply swap! state f args) (render!))

(defn- agent-call
  "Run one planned request through the broker. Calls `on-ok` with the parsed
  body, or sets a notice. Never retries."
  [p check on-ok]
  (if (:error p)
    (set-state! assoc :notice (:error p))
    (let [client (.-itonamiAgent js/globalThis)]
      (if-not client
        (set-state! assoc :notice "The consent broker did not load; no request can be made.")
        (do (set-state! assoc :busy (:operation p) :notice nil)
            (-> (.call client (:operation p) (:path p) (clj->js (:body p)))
                (.then (fn [r]
                         (let [outcome (.-outcome r)
                               body (js->clj (.-body r))
                               checked (when (= "ok" outcome) (check p body))]
                           (swap! state assoc :busy nil)
                           (cond
                             (and checked (not (:error checked))) (on-ok checked)
                             :else (set-state! assoc :notice
                                               (chat/outcome-message (:operation p)
                                                                     (if checked "unconfirmed" outcome)
                                                                     (.-status r)))))))
                (.catch (fn [_] (set-state! assoc :busy nil
                                            :notice (chat/outcome-message (:operation p) "network" nil))))))))))

(defn load-agent-bots! []
  (agent-call (chat/plan :list-bots {}) (fn [_ v] (chat/check-bots v))
              (fn [{:keys [bots]}] (set-state! assoc :agent-bots bots
                                               :notice (str (count bots) " bots from the Agent")))))

(defn load-sessions! []
  (let [bot (:bot @state)]
    (agent-call (chat/plan :list-sessions {:bot bot}) chat/check-sessions
                (fn [{:keys [sessions]}]
                  (set-state! #(-> % (assoc-in [:sessions bot] sessions)
                                   (assoc :notice (if (seq sessions)
                                                    (str (count sessions) " sessions on the Agent")
                                                    "No other sessions on the Agent yet."))))))))

(defn load-history! []
  (let [{:keys [bot session]} @state]
    (agent-call (chat/plan :read-history {:bot bot :session session}) chat/check-history
                (fn [{:keys [messages]}]
                  (when (and (= bot (:bot @state)) (= session (:session @state)))
                    (set-state! assoc :transcript messages :notice
                                (if (seq messages) nil "No messages in this session yet.")))))))

(defn new-session! []
  (let [bot (:bot @state)
        id (uuid)
        title (chat/session-title (.-value ($ "composer-input")))]
    (agent-call (chat/plan :create-session {:bot bot :session id :title title}) chat/check-sessions
                (fn [{:keys [session]}]
                  (set-state! (fn [s] (-> s
                                          (update-in [:sessions bot] #(into [session] %))
                                          (assoc :session (:id session) :transcript [] :view :chat
                                                 :drawer? false :notice "New session created.")
                                          (assoc-in [:titles (:id session)] (:title session)))))))))

(defn- send-request! [request text]
  (let [{:keys [bot session]} @state
        p (chat/plan :send-message {:bot bot :session session :request request :text text})
        client (.-itonamiAgent js/globalThis)]
    (cond
      (:error p) (set-state! assoc :notice (:error p))
      (not client) (set-state! assoc :notice "The consent broker did not load; nothing was sent.")
      :else
      (do (set-state! assoc :busy "send-message" :notice nil)
          (-> (.call client (:operation p) (:path p) (clj->js (:body p)))
              (.then (fn [r]
                       (let [outcome (.-outcome r)
                             checked (when (= "ok" outcome) (chat/check-reply p (js->clj (.-body r))))
                             first-try? (not (contains? (:rechecks @state) request))]
                         (swap! state assoc :busy nil)
                         (set-state! update :transcript chat/settle request
                                     (cond
                                       (and checked (not (:error checked))) checked
                                       (and (= "denied" outcome) first-try?) {:drop true}
                                       :else {:note (chat/outcome-message "send-message"
                                                                          (if checked "unconfirmed" outcome)
                                                                          (.-status r))}))
                         (when (and (= "denied" outcome) first-try?)
                           (set-state! assoc :notice (chat/outcome-message "send-message" "denied" nil))
                           (set! (.-value ($ "composer-input")) text)))))
              (.catch (fn [_]
                        (swap! state assoc :busy nil)
                        (set-state! update :transcript chat/settle request
                                    {:note (chat/outcome-message "send-message" "network" nil)}))))))))

(defn send! []
  (let [input ($ "composer-input")
        text (str/trim (.-value input))]
    (cond
      (:busy @state) nil
      (nil? (:bot @state)) (set-state! assoc :notice "Choose a bot first.")
      (str/blank? text) nil
      (> (count text) chat/max-text) (set-state! assoc :notice (str "Messages are limited to " chat/max-text " characters."))
      :else (let [request (uuid)]
              (set! (.-value input) "")
              (swap! state #(-> % (update :transcript chat/add-pending request text)
                                (update :sent conj text) (assoc :recall nil)))
              (send-request! request text)))))

(defn check-again!
  "Explicitly ask the Agent about an unconfirmed send: the same request id
  and text, so a completed turn replays its saved result instead of running
  again."
  [request]
  (when-let [text (:text (first (filter #(and (= :user (:role %)) (= request (:request %))) (:transcript @state))))]
    (swap! state update :rechecks (fnil conj #{}) request)
    (swap! state update :transcript
           (fn [t] (mapv #(if (and (= :assistant (:role %)) (= request (:request %))) (assoc % :state :pending) %) t)))
    (send-request! request text)))

(defn select-bot! [id]
  (set-state! assoc :bot id :session "default" :transcript [] :view :chat :drawer? false
              :tab :sessions :notice nil))

(defn select-session! [id]
  (set-state! assoc :session id :transcript [] :view :chat :drawer? false :notice nil))

;; ------------------------------------------------------------ views

(defn- bot-row [{:keys [id name report agent?]}]
  (let [selected? (= id (:bot @state))
        jobs (count (:jobs report))]
    (el :li {:class (str "row" (when selected? " selected"))}
        (el :button {:class "row-main" :type "button" :aria-current (when selected? "true")
                     :on-click #(select-bot! id)}
            (avatar id name)
            (el :span {:class "row-text"}
                (el :span {:class "row-title"} name)
                (el :span {:class "row-sub"}
                    (str/join " · " (remove nil? [(when report (if (:usable? report) "usable" "invalid"))
                                                  (when report (str jobs " routines"))
                                                  (cond agent? "on Agent"
                                                        (:agent-bots @state) "local only")])))))
        (when report
          (el :button {:class "icon-btn" :type "button" :title "Bot details" :aria-label (str name " details")
                       :on-click #(set-state! assoc :bot id :view :bot :drawer? false)} "ⓘ")))))

(defn- bots-pane []
  (el :div {:class "pane"}
      (el :div {:class "pane-actions"}
          (el :button {:class "btn" :type "button" :disabled (boolean (:busy @state))
                       :on-click load-agent-bots!} "Load Agent bots"))
      (el :ul {:class "list" :aria-label "Bots"} (map bot-row (bots)))))

(defn- session-row [{:keys [id title updated count]}]
  (let [selected? (= id (:session @state))]
    (el :li {:class (str "row" (when selected? " selected"))}
        (el :button {:class "row-main" :type "button" :aria-current (when selected? "true")
                     :on-click #(select-session! id)}
            (el :span {:class "row-text"}
                (el :span {:class "row-title"} (or title (if (= id "default") "Bot Chat" id)))
                (el :span {:class "row-sub"}
                    (str/join " · " (remove nil? [(when count (str count " messages"))
                                                  (some-> updated (subs 0 (min 16 (cljs.core/count updated))))]))))))))

(defn- sessions-pane []
  (let [{:keys [bot sessions busy]} @state]
    (if-not bot
      (el :div {:class "pane empty"} (el :p {} "Choose a bot in Bots to see its sessions."))
      (el :div {:class "pane"}
          (el :div {:class "pane-head"} (avatar bot (bot-name bot)) (el :strong {} (bot-name bot)))
          (el :div {:class "pane-actions"}
              (el :button {:class "btn primary" :type "button" :disabled (boolean busy) :on-click new-session!} "New session")
              (el :button {:class "btn" :type "button" :disabled (boolean busy) :on-click load-sessions!} "Load sessions"))
          (el :ul {:class "list" :aria-label "Sessions"}
              (map session-row (cons {:id "default" :title "Bot Chat"}
                                     (remove #(= "default" (:id %)) (get sessions bot)))))))))

;; ------------------------------------------------------------ autonomy

(defn- now-ms [] (.now js/Date))

(defn- ago [ms]
  (when ms
    (let [d (- (now-ms) ms) h (quot d autonomy/hour)]
      (cond (< d 0) "just now" (< h 1) "<1 h ago" (< h 48) (str h " h ago") :else (str (quot h 24) " days ago")))))

(defn- iso-min [ms] (when ms (subs (.toISOString (js/Date. ms)) 0 16)))

(def state-labels {:active "Active" :stale "Stale" :scheduled "Scheduled" :held "Held" :unknown "Unknown"})

(defn- read-file! [file]
  (-> (.text file)
      (.then (fn [text]
               (let [v (try (js->clj (.parse js/JSON text)) (catch :default _ nil))
                     k (autonomy/kind-of v)
                     parsed (case k :org (autonomy/org-from v) :board (autonomy/board-from v)
                                  :activity (autonomy/activity-from v) {:error "unrecognized file"})]
                 (if (:error parsed)
                   (set-state! assoc :notice (str (.-name file) ": " (:error parsed)))
                   (do (set-state! #(-> % (assoc k parsed :view :autonomy)
                                        (assoc-in [:sources k] (.-name file))
                                        (assoc :notice (str "Imported " (name k) " from " (.-name file)))))
                       (let [shown (:notice @state)]
                         (js/setTimeout #(when (= shown (:notice @state)) (set-state! assoc :notice nil)) 5000)))))))))

(defn- import-button []
  (let [input (el :input {:type "file" :multiple true :accept "application/json,.json" :class "visually-hidden"
                          :id "autonomy-file"})]
    (.addEventListener input "change" (fn [_] (doseq [f (array-seq (.-files input))] (read-file! f))))
    (el :label {:class "btn primary file-btn"} "Import files…" input)))

(defn- state-dots [states]
  (el :span {:class "dots-row"}
      (for [k autonomy/state-order :let [n (get states k 0)] :when (pos? n)]
        (el :span {:class (str "sdot " (name k)) :title (str (state-labels k) " " n)} (str n)))))

(defn- circle-list []
  (let [{:keys [org board activity org-q circle]} @state
        rows (autonomy/circle-rows org activity (:issues board) (now-ms) org-q)]
    (el :ul {:class "list" :aria-label "Circles"}
        (for [c (take 200 rows)]
          (el :li {:class (str "row" (when (= circle (:id c)) " selected"))}
              (el :button {:class "row-main" :type "button"
                           :on-click #(set-state! assoc :circle (:id c) :view :autonomy :drawer? false)}
                  (el :span {:class "row-text"}
                      (el :span {:class "row-title"} (:name c))
                      (el :span {:class "row-sub"} (str (:bots c) " bots ") (state-dots (:states c))))))))))

(defn- org-pane []
  (let [{:keys [org sources]} @state
        search (el :input {:class "search" :type "search" :placeholder "Search circles or bots"
                           :value (:org-q @state) :aria-label "Search circles or bots"})]
    (.addEventListener search "input"
                       (fn [_] (swap! state assoc :org-q (.-value search))
                         (when-let [n ($ "circle-list")] (.replaceChildren n (circle-list)))))
    (el :div {:class "pane"}
        (el :div {:class "pane-actions"}
            (import-button)
            (el :button {:class "btn" :type "button" :on-click #(set-state! assoc :view :autonomy :circle nil :drawer? false)}
                "Overview"))
        (el :p {:class "row-sub sources"}
            (str/join " · " (for [k [:org :board :activity]]
                              (str (case k :org "Org" :board "Board" :activity "Activity") ": "
                                   (get sources k "—")))))
        (if org
          (list search (el :div {:id "circle-list"} (circle-list)))
          (el :p {:class "muted"} "Import the Soshiki/fleet catalog, a Dandori board export and an activity snapshot.")))))

(defn- tile [label value sub]
  (el :div {:class "tile"} (el :span {:class "tile-label"} label) (el :strong {:class "tile-value"} (str value))
      (when sub (el :span {:class "row-sub"} sub))))

(defn- hourly-chart [activity]
  (let [w (autonomy/hourly-window activity 48)
        mx (max 1 (apply max (map second w)))]
    (el :figure {:class "hourly"}
        (el :div {:class "bars" :role "img"
                  :aria-label (str "Job receipts per hour, 48 hours to " (iso-min (first (last w))) " UTC")}
            (for [[h n] w]
              (doto (el :span {:class "bar" :title (str (iso-min h) "Z · " n " runs")})
                (-> .-style (.setProperty "--v" (str (/ n mx)))))))
        (el :figcaption {:class "row-sub"}
            (str "Fleet receipts per hour, " (iso-min (first (first w))) " → " (iso-min (first (last w))) " UTC")))))

(defn- bot-card [b st]
  (el :li {:class "bot-card"}
      (el :div {:class "bot-card-head"}
          (avatar (:id b) (:name b))
          (el :div {:class "row-text"}
              (el :span {:class "row-title"} (:name b))
              (el :span {:class "row-sub"} (or (:purpose b) "")))
          (el :span {:class (str "state " (name (:state st)))} (state-labels (:state st))))
      (el :div {:class "bot-card-facts row-sub"}
          (str/join " · " (remove nil?
                                  [(if (:last-at st) (str (:runs st) " runs · last " (ago (:last-at st)))
                                       (str (:jobs b) " jobs · " (or (:schedule-state b) "no schedule")))
                                   (when (seq (:issues st)) (str "Linear " (str/join ", " (take 3 (:issues st)))
                                                                 (when (> (count (:issues st)) 3) "…")))
                                   (when (seq (:board st))
                                     (str "board " (str/join " " (for [[k n] (sort-by (comp autonomy/state-index key) (:board st))] (str k " " n)))))])))
      (when-let [n (:next st)]
        (el :div {:class "next"} (el :span {:class "row-sub"} "Next: ")
            (el :strong {} (:id n)) " " (or (:title n) "") (el :span {:class "row-sub"} (str " (" (:reason n) ")"))))
      (el :button {:class "btn small" :type "button" :on-click #(select-bot! (:id b))} "Chat")))

(defn- autonomy-view []
  (let [{:keys [org board activity circle]} @state
        now (now-ms)
        issues (:issues board)]
    (if-not (or org activity)
      (el :div {:class "welcome"}
          (el :h2 {} "Autonomy")
          (el :p {:class "muted"} "See how far the bots get on their own. Import the organization (Soshiki or the fleet catalog), a Dandori board export and an activity snapshot. Files stay on this device.")
          (import-button))
      (let [c (first (filter #(= circle (:id %)) (:circles org)))
            scope (if c (update org :bots (fn [bs] (filterv #(= circle (:circle %)) bs))) org)
            sm (when org (autonomy/summary scope activity issues now))
            bots (->> (:bots org)
                      (filter #(or (nil? circle) (= circle (:circle %))))
                      (map (fn [b] [b (autonomy/bot-status b activity issues now)]))
                      (sort-by (fn [[b st]] [(.indexOf (to-array autonomy/state-order) (:state st)) (- (or (:last-at st) 0)) (:name b)])))]
        (el :div {:class "autonomy"}
            (el :h2 {} (if c (:name c) "Fleet autonomy"))
            (when (:purpose c) (el :p {:class "muted"} (:purpose c)))
            (when-let [o (:observed-at activity)]
              (let [t (.parse js/Date o)]
                (el :p {:class (str "banner " (if (> (- now t) autonomy/stale-after) "warn" "ok"))}
                    (if (> (- now t) autonomy/stale-after)
                      (str "Last observation " (subs o 0 16) "Z (" (ago t) "). Nothing newer has been recorded, so no bot is shown as active now.")
                      (str "Observed " (subs o 0 16) "Z (" (ago t) ").")))))
            (when sm
              (el :div {:class "tiles"}
                  (tile "Registered" (:registered sm) nil)
                  (tile "Schedule verified" (:verified sm) "not proof of running")
                  (tile "Ran at least once" (:ran sm) "from receipts")
                  (tile "Active (24 h)" (:active sm) nil)
                  (when-not c
                    (tile "Board" (count issues) (when (seq (:board sm)) (str (get (:board sm) "started" 0) " started · " (get (:board sm) "completed" 0) " done"))))))
            (when (seq (:hourly activity)) (hourly-chart activity))
            (when org
              (list
               (el :h3 {} (str (if c "Bots in this circle" "Bots") " (" (count bots) ")"))
               (el :ul {:class "bot-cards"} (for [[b st] (take 120 bots)] (bot-card b st)))
               (when (> (count bots) 120) (el :p {:class "row-sub"} "Showing 120. Pick a circle in Org to narrow.")))))))))

(defn- sidebar []
  (let [tab (:tab @state)
        tab-btn (fn [k label] (el :button {:class (str "tab" (when (= tab k) " active")) :type "button"
                                           :role "tab" :aria-selected (str (= tab k))
                                           :on-click #(set-state! assoc :tab k)} label))]
    (el :div {:class "sidebar-inner"}
        (el :div {:class "tabs" :role "tablist"} (tab-btn :sessions "Sessions") (tab-btn :bots "Bots") (tab-btn :org "Org"))
        (case tab :bots (bots-pane) :org (org-pane) (sessions-pane)))))

(defn- message-node [{:keys [role text state note request]}]
  (case [role state]
    [:assistant :pending] (el :div {:class "msg assistant pending" :aria-live "polite"}
                              (el :span {:class "dots" :aria-label "Waiting for the Agent"} "•••"))
    [:assistant :unconfirmed] (el :div {:class "msg assistant unconfirmed"}
                                  (el :p {} note)
                                  (el :button {:class "btn" :type "button" :disabled (boolean (:busy @state))
                                               :on-click #(check-again! request)} "Check result"))
    (el :div {:class (str "msg " (name role))} (rich-text text))))

(defn- chat-view []
  (let [{:keys [bot transcript busy]} @state]
    (el :div {:class "chat"}
        (if (empty? transcript)
          (el :div {:class "welcome"}
              (if bot
                (list (avatar bot (bot-name bot))
                      (el :h2 {} (bot-name bot))
                      (el :p {:class "muted"} "History stays with the Agent. Load it, or start writing.")
                      (el :button {:class "btn" :type "button" :disabled (boolean busy) :on-click load-history!}
                          "Load history"))
                (list (el :h2 {} "Itonami")
                      (el :p {:class "muted"} "Choose a bot to talk with. Each request to the Agent asks you first.")
                      (el :button {:class "btn primary choose" :type "button"
                                   :on-click #(set-state! assoc :drawer? true :tab :bots)} "Choose a bot"))))
          (el :div {:class "transcript" :role "log" :aria-label "Conversation"}
              (map message-node transcript))))))

(defn- problem-row [{:keys [level file path message]}]
  (el :li {:class (str "problem " (name level))}
      (badge (name level) (name level)) " "
      (el :code {} (str file (when (seq path) (str " " (pr-str path))))) " " message))

(defn- job-row [{:keys [id name schedule active? status reason]}]
  (el :li {:class "routine"}
      (el :div {:class "routine-head"}
          (el :strong {} (or name id))
          (badge (case status :ok "ok" :held "held" :refused "refused" "unknown")
                 (if status (cljs.core/name status) "—")))
      (el :div {:class "row-sub"} (str/join " · " (remove str/blank? [(str schedule) (if active? "active" "paused") reason])))))

(defn- bot-view []
  (let [b (first (filter #(= (:bot @state) (:id %)) (bots)))
        r (:report b)]
    (el :div {:class "detail"}
        (el :div {:class "detail-head"}
            (avatar (:id b) (:name b))
            (el :div {}
                (el :h2 {} (:name b))
                (el :div {:class "badges"}
                    (when r (badge (if (= "itonami" (:runtime r)) "itonami" "hermes") (:runtime r)))
                    (when r (badge (if (:usable? r) "ok" "refused") (if (:usable? r) "usable" "invalid")))
                    (when (:agent? b) (badge "itonami" "on Agent")))))
        (el :div {:class "pane-actions"}
            (el :button {:class "btn primary" :type "button" :on-click #(select-bot! (:id b))} "Open Bot Chat"))
        (el :h3 {} "Routines")
        (el :p {:class "muted"} "Dry-run on this device by the Otehon core. Nothing runs and nothing is sent.")
        (if (seq (:jobs r)) (el :ul {:class "routines"} (map job-row (:jobs r))) (el :p {:class "muted"} "No routines."))
        (el :h3 {} "Profile check")
        (if (seq (:problems r)) (el :ul {:class "problems"} (map problem-row (:problems r))) (el :p {:class "muted"} "No problems.")))))

(defn- topbar []
  (let [{:keys [bot session titles busy]} @state]
    (el :div {:class "topbar-inner"}
        (el :button {:class "icon-btn menu" :type "button" :aria-label "Sessions and bots"
                     :aria-expanded (str (:drawer? @state))
                     :on-click #(set-state! update :drawer? not)} "☰")
        (el :div {:class "title"}
            (el :strong {} (cond (= :autonomy (:view @state)) "Autonomy" bot (bot-name bot) :else "Itonami"))
            (when (and bot (not= :autonomy (:view @state))) (el :span {:class "muted"}
                          (if (= session "default") "Bot Chat" (get titles session "Session")))))
        (when busy (el :span {:class "chip"} "Waiting for approval or the Agent…")))))

(defn- statusbar []
  (let [client (.-itonamiAgent js/globalThis)]
    (el :div {:class "statusbar-inner"}
        (el :span {} (str "Model " chat/model))
        (el :span {} (str "Agent " (if client (.-origin client) "unavailable")))
        (el :span {} "Each request asks first · no automatic retry"))))

(defn render! []
  (let [{:keys [view drawer? notice busy bot]} @state]
    (.toggle (.-classList (.-body js/document)) "drawer-open" (boolean drawer?))
    (mount! "topbar" (topbar))
    (mount! "sidebar" (sidebar))
    (mount! "content" (cond (= view :autonomy) (autonomy-view)
                            (and (= view :bot) bot) (bot-view)
                            :else (chat-view)))
    (mount! "notice" (if notice (el :p {:class "notice" :role "status"} notice) (el :span {})))
    (mount! "statusbar" (statusbar))
    (set! (.-disabled ($ "composer-send")) (boolean (or busy (nil? bot))))
    (set! (.-hidden ($ "composer")) (contains? #{:bot :autonomy} view))
    (when-let [log (.querySelector js/document ".transcript")]
      (set! (.-scrollTop log) (.-scrollHeight log)))))

;; ------------------------------------------------------------ composer

(defn- recall! [input dir]
  (let [{:keys [sent recall]} @state
        n (count sent)]
    (when (pos? n)
      (let [i (case dir :up (max 0 (dec (or recall n))) :down (min n (inc (or recall n))))]
        (swap! state assoc :recall i)
        (set! (.-value input) (if (< i n) (nth sent i) ""))))))

(defn- wire-composer! []
  (let [input ($ "composer-input")]
    (.addEventListener ($ "composer") "submit" (fn [e] (.preventDefault e) (send!)))
    (.addEventListener input "keydown"
                       (fn [e]
                         (cond
                           (and (= "Enter" (.-key e)) (not (.-shiftKey e)) (not (.-isComposing e)))
                           (do (.preventDefault e) (send!))
                           (and (= "ArrowUp" (.-key e)) (or (str/blank? (.-value input)) (:recall @state)))
                           (do (.preventDefault e) (recall! input :up))
                           (and (= "ArrowDown" (.-key e)) (:recall @state))
                           (do (.preventDefault e) (recall! input :down)))))
    (.addEventListener ($ "scrim") "click" #(set-state! assoc :drawer? false))))

(defn ^:export start []
  (wire-composer!)
  (render!)
  (-> (js/fetch "profiles.json")
      (.then (fn [res] (if (.-ok res) (.json res) (throw (js/Error. (str "profiles.json " (.-status res)))))))
      (.then (fn [data] (set-state! assoc :reports (core/reports (js->clj data) readers))))
      (.catch (fn [e] (set-state! assoc :notice (str "Could not load the bundled profiles: " (.-message e)))))))

(.addEventListener js/document "DOMContentLoaded" start)
