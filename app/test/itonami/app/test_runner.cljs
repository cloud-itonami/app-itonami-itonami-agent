(ns itonami.app.test-runner
  (:require [cljs.test :as t]
            [itonami.app.core-test]
            [itonami.app.chat-test]))

(defmethod t/report [::t/default :end-run-tests] [m]
  (when-not (t/successful? m) (set! (.-exitCode js/process) 1)))

(defn -main [] (t/run-tests 'itonami.app.core-test 'itonami.app.chat-test))
(set! *main-cli-fn* -main)
