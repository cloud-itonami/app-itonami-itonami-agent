(ns itonami.app.chat-test
  (:require [clojure.test :refer [deftest is testing]]
            [itonami.app.chat :as chat]))

(def bot "profile:awai-banking-ops")
(def req "0b6f8d2e-6c1a-4a8e-9f1b-2a3c4d5e6f70")
(def sid "1c7a9e3f-2b4d-4e6f-8a1b-3c5d7e9f1a2b")

(deftest plans
  (is (= "/v1/bot-chat/bots" (:path (chat/plan :list-bots {}))))
  (is (= {"botId" bot "action" "list"} (:body (chat/plan :list-sessions {:bot bot}))))
  (is (= {"botId" bot "sessionId" "default"} (:body (chat/plan :read-history {:bot bot}))))
  (is (= "awai.bot-chat-request.v1" (get-in (chat/plan :send-message {:bot bot :request req :text "hi"}) [:body "schema"])))
  (is (= "muse-spark-1.3" (get-in (chat/plan :send-message {:bot bot :request req :text "hi"}) [:body "model"]))))

(deftest plan-refusals
  (testing "negative controls: each refusal has a passing twin above"
    (is (:error (chat/plan :list-sessions {:bot "awai-banking-ops"})))
    (is (:error (chat/plan :read-history {:bot bot :session "not-a-uuid"})))
    (is (:error (chat/plan :send-message {:bot bot :request "default" :text "hi"})))
    (is (:error (chat/plan :send-message {:bot bot :request req :text "   "})))
    (is (:error (chat/plan :send-message {:bot bot :request req :text (apply str (repeat 8193 "a"))})))
    (is (:error (chat/plan :create-session {:bot bot :session "default" :title "t"})))
    (is (:error (chat/plan :create-session {:bot bot :session sid :title (apply str (repeat 121 "a"))})))
    (is (:error (chat/plan :delete {:bot bot})))))

(def good-reply
  {"requestId" req "botId" bot "sessionId" "default" "provider" "meta" "model" "muse-spark-1.3"
   "store" {"owner" "itonami-agent" "readback" true} "state" "completed" "text" "hello"})

(deftest replies
  (let [p (chat/plan :send-message {:bot bot :request req :text "hi"})]
    (is (= "hello" (:text (chat/check-reply p good-reply))))
    (testing "an answer not stored and read back is unconfirmed"
      (is (:error (chat/check-reply p (assoc-in good-reply ["store" "readback"] false))))
      (is (:error (chat/check-reply p (assoc good-reply "requestId" sid))))
      (is (:error (chat/check-reply p (assoc good-reply "botId" "profile:other"))))
      (is (:error (chat/check-reply p (assoc good-reply "provider" "openrouter"))))
      (is (:error (chat/check-reply p (assoc good-reply "state" "running"))))
      (is (:error (chat/check-reply p nil))))))

(deftest history-sessions-bots
  (let [h (chat/plan :read-history {:bot bot})
        ok {"botId" bot "store" {"owner" "itonami-agent" "readback" true}
            "messages" [{"role" "user" "text" "q"} {"role" "assistant" "text" "a"}]}]
    (is (= [:user :assistant] (map :role (:messages (chat/check-history h ok)))))
    (is (:error (chat/check-history h (assoc ok "messages" [{"role" "system" "text" "x"}])))))
  (let [l (chat/plan :list-sessions {:bot bot})
        c (chat/plan :create-session {:bot bot :session sid :title "T"})
        store {"owner" "itonami-agent" "readback" true}]
    (is (= [sid] (map :id (:sessions (chat/check-sessions l {"botId" bot "store" store
                                                             "sessions" [{"sessionId" sid} {"sessionId" "bad"}]})))))
    (is (= sid (get-in (chat/check-sessions c {"botId" bot "store" store "session" {"sessionId" sid}}) [:session :id])))
    (is (:error (chat/check-sessions c {"botId" bot "store" store "session" {"sessionId" req}}))))
  (let [sha (apply str (repeat 64 "a"))]
    (is (= ["profile:x"] (map :id (:bots (chat/check-bots {"schema" "awai.agent-directory.v1"
                                                            "bots" [{"id" "profile:x" "name" "x" "chatPersona" {"available" true "sourceSha256" sha}}]})))))
    (is (:error (chat/check-bots {"schema" "awai.agent-directory.v1"
                                  "bots" [{"id" "profile:x" "name" "x" "chatPersona" {"available" false "sourceSha256" sha}}]})))))

(deftest transcript
  (let [t (chat/add-pending [] req "hi")]
    (is (= [:user :assistant] (map :role t)))
    (is (= "yo" (:text (second (chat/settle t req {:text "yo"})))))
    (is (= :unconfirmed (:state (second (chat/settle t req {:note "n"})))))
    (testing "a denied first attempt leaves no trace, since nothing was sent"
      (is (= [] (chat/settle t req {:drop true}))))
    (is (= t (chat/settle t sid {:text "other"})))))

(deftest titles
  (is (= "New conversation" (chat/session-title "  ")))
  (is (= 60 (count (chat/session-title (apply str (repeat 100 "a")))))))
