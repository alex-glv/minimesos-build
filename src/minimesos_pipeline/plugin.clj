(ns minimesos-pipeline.plugin
  (:require [clojure.core.async :refer [go-loop <!]]))

;; (on-steps trigger-readthedocs
;;           slack-post)
(defonce agents-map {:step-running (agent nil)
                     :step-success (agent nil)
                     :step-failure (agent nil)})

(defn bootstrap-agent [ctx step]
  (let [th (Thread.
            (fn [] (go-loop []
                    (let [publ (:event-publisher (:context ctx)) 
                          {:keys [topic payload]} (<! publ)]
                      (send-off (get agents-map step) assoc :topic topic :payload payload)))))
        _ (.start th)]
    th))

(defn on-step [step f]
  (let [nUUID (java.util.UUID/randomUUID)]
    (add-watch (get agents-map step) nUUID f)
    nUUID))
