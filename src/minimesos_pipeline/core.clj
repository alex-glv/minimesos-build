(ns minimesos-pipeline.core
  (:require
      [minimesos-pipeline.pipeline :as pipeline]
      [ring.server.standalone :as ring-server]
      [lambdacd.ui.ui-server :as ui]
      [lambdacd.runners :as runners]
      [lambdacd.util :as util]
      [lambdacd.core :as lambdacd]
      [clojure.tools.logging :as log])
  (:gen-class))


(defonce server (atom nil))

(defn -main [& args]
  (let [;; the home dir is where LambdaCD saves all data.
        ;; point this to a particular directory to keep builds around after restarting
        home-dir (util/create-temp-dir)
        config {:home-dir home-dir
                :name     "minimesos pipeline"}
        ;; initialize and wire everything together
        pipeline (lambdacd/assemble-pipeline pipeline/pipeline config)
        ;; create a Ring handler for the UI
        app (ui/ui-for pipeline)
        ]
    ;; (log/log-capture! 'lambdacd.util)
    (log/info "LambdaCD Home Directory is " home-dir)
    ;; this starts the pipeline and runs one build after the other.
    ;; there are other runners and you can define your own as well.
    (runners/start-one-run-after-another pipeline)
    ;; start the webserver to serve the UI
    (reset! server (ring-server/serve app {:open-browser? false
                                           :port          8080}))))


