(ns minimesos-pipeline.core
  (:require [clojure.tools.logging :as log]
            [lambdacd.core :as lambdacd]
            [lambdacd.runners :as runners]
            [lambdacd.ui.ui-server :as ui]
            [lambdacd.util :as util]
            [minimesos-pipeline.api :as api]
            [minimesos-pipeline.pipeline :as pipeline]
            [minimesos-pipeline.plugin :as plugin]
            [minimesos-pipeline.slack :as slack]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [ring.server.standalone :as ring-server])
  (:gen-class))


(defonce server (atom nil))
(defonce api-server (atom nil))

(defn authenticated? [name pass]
  (and (= name (System/getenv "AUTH_UNAME"))
       (= pass (System/getenv "AUTH_PASSWD"))))

(defn -main [& args]
  (let [;; the home dir is where LambdaCD saves all data.
        ;; point this to a particular directory to keep builds around after restarting
        home-dir (util/create-temp-dir)
        config {:home-dir home-dir
                :name     "minimesos pipeline"}
        ;; initialize and wire everything together
        auto-pipeline (lambdacd/assemble-pipeline (pipeline/get-pipeline :auto) config)
        man-pipeline (lambdacd/assemble-pipeline (pipeline/get-pipeline :manual) config)
        ;; create a Ring handler for the UI
        app (->  (ui/ui-for man-pipeline)
                 (wrap-basic-authentication authenticated?))]
    (log/info "LambdaCD Home Directory is " home-dir)
    (plugin/bootstrap-agents (:context auto-pipeline))
    (slack/bootstrap-slack [:step-finished :info :pr-trigger :tag-trigger] (System/getenv "SLACK_CHAN_ID") {:api-url "https://slack.com/api", :token (System/getenv "SLACK_TOKEN")})
    (runners/start-one-run-after-another man-pipeline)
    ;; start the webserver to serve the UI
    (reset! server
            (ring-server/serve app {:open-browser? false
                                    :port (read-string (or (System/getenv "SERVE_WEBUI_PORT") "8080" ))}))
    (reset! api-server
            (ring-server/serve (api/rest-api (:context auto-pipeline)) {:open-browser? false
                                                                        :port (read-string (or (System/getenv "SERVE_API_PORT") "8081" ))}))))


