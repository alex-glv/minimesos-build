(ns minimesos-pipeline.dev
  (:require 
            [lambdacd.core :as lambdacd]
            [lambdacd.internal.execution :as exec]
            [lambdacd.ui.ui-server :as ui]
            [lambdacd.util :as util]
            [minimesos-pipeline.pipeline :as pipeline]
            [ring.server.standalone :as ring-server]
            [minimesos-pipeline.plugin :as plugin]
            [minimesos-pipeline.slack :as slack]
            [minimesos-pipeline.api :as api]))

(defonce sys-map (atom nil))

(defn start-one-run-after-another-once
  "Runner that always keeps one pipeline-run active. It waits for a run to complete, then starts a new one."
  [{pipeline-def :pipeline-def context :context}]
  (let [th (Thread. #(exec/run pipeline-def context))
        _ (.start th)]
    th))

(defn start-server [app]  
  )

(defn get-new-context [pipeline-type]
  (let [home-dir (util/create-temp-dir)
        config {:home-dir home-dir
                :name     "minimesos pipeline"} 
        pipeline (lambdacd/assemble-pipeline (pipeline/get-pipeline pipeline-type) config)]
    pipeline))

(defn stop-system
  ([] (stop-system @sys-map))
  ([sys-map]
   (let [webui-conn (:webui sys-map)
         api-conn (:api sys-map)
         myth (:run-thread sys-map)]
     (if (not= webui-conn nil)
       (.stop webui-conn))
     (if (not= api-conn nil)
       (.stop api-conn))
     ;; stop pipeline thread
     (.stop myth))))

(defn start-system []
  (let [manual-ctx (get-new-context :manual)
        auto-ctx (get-new-context :auto)
        ui (ui/ui-for manual-ctx)
        webui-server (ring-server/serve ui {:open-browser? false
                                            :port          8080})
        api-server (ring-server/serve (api/rest-api (:context auto-ctx))
                                      {:open-browser? false
                                          :port 8000})
        myth (start-one-run-after-another-once  manual-ctx)
        sysm
        {:auto-context (:context auto-ctx)
         :manual-context (:context manual-ctx)
         :webui webui-server
         :api api-server
         :run-thread myth
         }]
    (plugin/bootstrap-agents (:context auto-ctx) (:context manual-ctx))
    (reset! sys-map sysm)
    sysm))
