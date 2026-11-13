(ns itonami.otehon.conversation-test
 (:require [clojure.test :refer [deftest is run-tests]] [itonami.otehon.conversation :as c] [itonami.otehon.io :as io]))
(def id "00000000-0000-4000-8000-000000000001")
(deftest owner-bot-session-isolation-and-legacy-default
 (is (= (io/sha256-utf8 (str "itonami-agent/conversation/v1\u0000owner\u0000profile:sample")) (c/session-scope "owner" "profile:sample" "default")))
 (is (not= (c/session-scope "owner" "profile:sample" id) (c/session-scope "other" "profile:sample" id)))
 (is (not= (c/session-scope "owner" "profile:sample" id) (c/session-scope "owner" "profile:other" id)))
 (is (thrown? Exception (c/session-scope "owner" "profile:sample" "../escape"))))
(deftest private-session-list-is-latest-event-not-transcript
 (is (= [{:sessionId id :title "updated" :updatedAt "2026-11-13T10:00:00Z"}]
  (c/session-list [{:sessionId id :title "first" :updatedAt "2026-11-13T09:00:00Z" :text "must not leak"}
                   {:sessionId id :title "updated" :updatedAt "2026-11-13T10:00:00Z"}]))))
(deftest requests-are-explicit-and-do-not-route-through-app-origin
 (is (= "/v1/bot-chat/history" (:path (c/request-plan {:action "show" :botId "profile:sample" :sessionId id}))))
 (is (thrown? Exception (c/request-plan {:action "send" :botId "profile:sample" :sessionId id :text "test"})))
 (is (= 2 (:exit (c/command ["list" "--endpoint" "http://localhost" "--bot" "profile:sample"])))))
(let [r (run-tests 'itonami.otehon.conversation-test)] (when (pos? (+ (:fail r) (:error r))) (System/exit 1)))
