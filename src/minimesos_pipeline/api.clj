(ns minimesos-pipeline.api
  (:use compojure.core)
  (require [clojure.string :as string]
           [ring.middleware.json :as ring-json]
           [ring.middleware.keyword-params :as ring-kw]
           [minimesos-pipeline.pipeline :as pipeline]
           [cheshire.core :as ch]
           [cheshire.generate :as chg]
           [lambdacd.runners :as runners]
           [lambdacd.core :as lambdacd]
           [lambdacd.event-bus :as event-bus]
           [clojure.tools.logging :as log]))

(defn json [data]
  {:headers { "Content-Type" "application/json"}
   :body (ch/generate-string data)
   :status 200 })

(defn rest-api [ctx]
  (-> (context
       "/api" []
       (POST "/slack-github/build"
             {body :body}
             (do (log/info "Slack request: " (slurp body))
                 (let [body-text (slurp body)
                       body-parsed (into {} (map #(string/split % #"=") (string/split body-text  #"\n")))
                       [trigger-word trigger-type identifier] (string/split (get body-parsed "text") #" ")]
                   (log/info "Slack request: " body-parsed)
                   (case trigger-type
                     "pr" (do (event-bus/publish ctx :pr-trigger {:final-result {:status :success :step-name (str "Building pr " identifier)}})
                              (pipeline/run-async (pipeline/get-pipeline :auto) ctx {:pr-id identifier}))
                     "tag" (do (event-bus/publish ctx :tag-trigger {:final-result {:status :success :step-name (str "Building tag " identifier)}})
                               (pipeline/run-async (pipeline/get-pipeline :auto) ctx {:tag-id identifier}))
                     :else nil
                     (json {:status :success})))))

       (POST "/github/commit" []
             (do
               (pipeline/run-async (pipeline/get-pipeline :auto) ctx {})
               (json {:status :success})))
       )
      ;; ring-kw/wrap-keyword-params
      ;; ring-json/wrap-json-params
      ))
