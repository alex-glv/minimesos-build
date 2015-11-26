(ns minimesos-pipeline.plugin
  (:require [clojure.core.async :refer [go-loop <!]]
            [clojure.tools.logging :as log]))

(defonce agents (atom {}))

(defn bootstrap-agents
  "Starts thread that fetches updates from lambdacd events channel and updates agents. 
  Every step has respective agent. If no agent exists for the step, create and update agents map."
  [ctx]
  (let [error-handler (fn [failed-agent ^Exception exception]
                        (log/error (.getMessage exception)))
        steps [:step-result-updated :step-finished :info]
        agents (reset! agents (into {} (map #(vector % (agent {})) steps)))
        _ (doall (map (fn [[_ a]] (set-error-handler! a error-handler)) agents))
        _ (doall (map (fn [[_ a]] (set-error-mode! a :continue)) agents))
        ch (go-loop []
             (let [publ (:event-publisher ctx)
                   {:keys [topic payload]} (<! publ)
                   topic-agent (get agents topic)]
               (log/debug "Received payload update: " topic payload)
               (if (nil? topic-agent)
                 (log/error "No agent for topic: " topic)
                 (send-off topic-agent assoc :topic topic :payload payload)))
             (recur))]
    agents))

(defn on-step
  "Subscribe to specific step event."
  [step f]
  (let [nUUID (java.util.UUID/randomUUID)
        candidate-agent (get @agents step)]
    (if (nil? candidate-agent)
      (throw (Exception. (str "Agent does not exist for step " step)))
      (add-watch candidate-agent nUUID f))
    nUUID))
