(ns itonami.app.parity-test
  "Babashka only: the app's request plans equal Otehon's conversation
  request-plan for the same inputs (the browser cannot load that namespace)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.walk :as walk]
            [itonami.app.chat :as chat]
            [itonami.otehon.conversation :as conv]))

(def bot "profile:awai-banking-ops")
(def req "0b6f8d2e-6c1a-4a8e-9f1b-2a3c4d5e6f70")
(def sid "1c7a9e3f-2b4d-4e6f-8a1b-3c5d7e9f1a2b")

(defn- otehon [m] (try (let [p (conv/request-plan m)] {:path (:path p) :body (walk/stringify-keys (:body p))})
                       (catch Exception _ :refused)))
(defn- app [op m] (let [p (chat/plan op m)] (if (:error p) :refused {:path (:path p) :body (:body p)})))

(deftest same-plans
  (doseq [[op app-in ote-in]
          [[:list-sessions {:bot bot} {:action "list" :botId bot}]
           [:read-history {:bot bot} {:action "show" :botId bot}]
           [:read-history {:bot bot :session sid} {:action "show" :botId bot :sessionId sid}]
           [:create-session {:bot bot :session sid :title "T"} {:action "new" :botId bot :sessionId sid :title "T"}]
           [:create-session {:bot bot :title "T"} {:action "new" :botId bot :title "T"}]
           [:send-message {:bot bot :request req :text "hi"} {:action "send" :botId bot :model "muse-spark-1.3" :requestId req :text "hi"}]
           [:send-message {:bot bot :request "default" :text "hi"} {:action "send" :botId bot :model "muse-spark-1.3" :requestId "default" :text "hi"}]
           [:send-message {:bot bot :request req :text ""} {:action "send" :botId bot :model "muse-spark-1.3" :requestId req :text ""}]
           [:list-sessions {:bot "nope"} {:action "list" :botId "nope"}]]]
    (is (= (otehon ote-in) (app op app-in)) (pr-str op app-in))))
