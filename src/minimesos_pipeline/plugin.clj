(ns minimesos-pipeline.plugin
  (:require [clojure.core.async :refer [go-loop go <!]]
            [clojure.tools.logging :as log]
            [lambdacd.event-bus :as event-bus]))

(defonce steps [:step-result-updated :step-finished :tag-trigger :pr-trigger :info])
(defonce agents (atom (into {} (map #(vector % (agent {})) steps))))

(defn bootstrap-agents
  "Starts thread that fetches updates from lambdacd events channel and updates agents. 
  Every step has respective agent. If no agent exists for the step, create and update agents map."
  [ctx]
  (let [error-mode :continue
        error-handler (fn [failed-agent ^Exception exception]
                        (log/error (.getMessage exception)))
        dagents @agents
        subscription (event-bus/subscribe ctx :info)
        steps-finished (event-bus/only-payload subscription)
        ch (go-loop []
             (let [_ (log/info "Bootstrapping agents for  " subscription)
                   {:keys [topic payload]} (<! subscription)
                   topic-agent (get dagents topic)]
               (log/info "Received payload update: " topic)
               (log/debug "Payload " payload)
               (if (nil? topic-agent)
                 (log/error "No agent for topic: " topic)
                 (do (log/info "Sending off " topic)
                     (send-off topic-agent assoc :topic topic :payload payload))))
             (recur))]
    dagents))

(defn on-step
  "Subscribe to specific step event."
  [step f]
  (let [nUUID (java.util.UUID/randomUUID)
        candidate-agent (get @agents step)]
    (if (nil? candidate-agent)
      (throw (Exception. (str "Agent does not exist for step " step)))
      (add-watch candidate-agent nUUID f))
    nUUID))
