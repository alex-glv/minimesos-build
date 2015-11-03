(ns minimesos-pipeline.plugin
  (:require [clojure.core.async :refer [go-loop <!]]
            [clojure.tools.logging :as log]))

;; (on-steps trigger-readthedocs
;;           slack-post)
(defonce agents-map {:step-running (agent {})
                     :step-success (agent {})
                     :step-failure (agent {})
                     :step-result-updated (agent {})
                     :step-finished (agent {})})

(defn bootstrap-agents [ctx agents-map]
  (let [ch (go-loop []
             (let [publ (:event-publisher ctx)
                   {:keys [topic payload]} (<! publ)
                   ag (get agents-map topic)]
               (log/info "Received payload update: " topic payload)
               (if (= ag nil)
                 (log/error "No agent found for event " topic)
                 (send-off (get agents-map topic) assoc :topic topic :payload payload)))
             (recur))]
    ch))

(defn on-step [step f]
  (let [nUUID (java.util.UUID/randomUUID)]
    (add-watch (get agents-map step) nUUID f)
    nUUID))
