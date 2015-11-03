(ns minimesos-pipeline.dev
  (:require 
            [lambdacd.core :as lambdacd]
            [lambdacd.internal.execution :as exec]
            [lambdacd.ui.ui-server :as ui]
            [lambdacd.util :as util]
            [minimesos-pipeline.pipeline :as pipeline]
            [ring.server.standalone :as ring-server]))

(defonce sys-map (atom nil))

(defn start-one-run-after-another-once
  "Runner that always keeps one pipeline-run active. It waits for a run to complete, then starts a new one."
  [{pipeline-def :pipeline-def context :context}]
  (let [th (Thread. #(exec/run pipeline-def context))
        _ (.start th)]
    th))

(defn start-server [app]  
  (ring-server/serve app {:open-browser? false
                          :port          8080}))

(defn get-new-context []
  (let [home-dir (util/create-temp-dir)
        config {:home-dir home-dir
                :name     "minimesos pipeline"} 
        pipeline (lambdacd/assemble-pipeline pipeline/pipeline config)]
    pipeline))

(defn stop-system
  ([] (stop-system @sys-map))
  ([sys-map]
   (let [http-conn (:http-conn sys-map)
         myth (:run-thread sys-map)]
     (if (not= http-conn nil)
       (.stop http-conn))
     (.stop myth))))

(defn start-system []
  (let [ctx (get-new-context)
        ui (ui/ui-for ctx)
        serv (start-server ui)
        myth (start-one-run-after-another-once ctx)
        sysm
        {:context (:context ctx)
         :http-conn serv
         :run-thread myth }]
    (reset! sys-map sysm)
    sysm))
