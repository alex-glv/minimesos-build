(ns minimesos-pipeline.slack
  (:require [clj-slack.channels :as channels]
            [clj-slack.chat :as chat]
            [clojure.tools.logging :as log]
            [minimesos-pipeline.plugin :refer [on-step]]))

(def connection (atom nil))
(def channel (atom nil))

(defn set-conn! [conn]
  (reset! connection conn))

(defn set-chan! [chan]
  (reset! channel chan))

(defn subscribe-to [steps]  
  (doseq [step steps]
    (let [conn @connection
          chan @channel
          watch-fn (fn [_ _ _ message]
                     (let [topic (:topic message)
                           payload (:payload message)
                           _ (log/info "Logging to " chan " with " conn " payload " payload)]
                       (chat/post-message conn chan
                                          (str "Build " (:build-number payload)
                                               " update step: " (if (= topic :step-finished) (:step-name (:final-result payload)) (:step-id payload))
                                               " result: "
                                               (if (= topic :step-finished)
                                                 (name (:status (:final-result payload)))
                                                 topic))
                                          {:username "minimesos-CD"})))]
      (minimesos-pipeline.plugin/on-step step watch-fn ))))

(defn bootstrap-slack [steps channel-id connection]
  (set-conn! connection)
  (set-chan! channel-id)
  (subscribe-to steps))


