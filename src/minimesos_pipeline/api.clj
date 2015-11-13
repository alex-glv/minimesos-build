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
  (-> (context "/api" []
               (POST "/slack-github/tag" {{text "text" trigger "trigger"} :json-params :as data}
                     (do
                       (log/info "Slack build request with " text trigger (re-find #"build-tag " text))
                       (let [tag-request (string/split text #" ")
                             tag (second tag-request)]
                         (if tag
                           (do  (event-bus/publish ctx :tag-trigger {:final-result {:status :success :step-name (str "Building tag " tag)}})
                                (pipeline/run-async (pipeline/get-pipeline :auto) ctx {:tag-id tag})
                                (json {:status :success}))
                           (json {:status :failed})))))

               (POST "/slack-github/pr" {{text "text" trigger "trigger"} :json-params :as data}
                     (do
                       (log/info "Slack build request with " data)
                       (let [pr-request (string/split text #" ")
                             pr-no (second pr-request)]
                         (if pr-no
                           (do  (event-bus/publish ctx :pr-trigger {:final-result {:status :success :step-name (str "Building pr " pr-no)}})
                                (pipeline/run-async (pipeline/get-pipeline :auto) ctx {:pr-id pr-no})
                                (json {:status :success}))
                           (json {:status :failed})))))
               
               (POST "/github/commit" []
                     (do
                       (pipeline/run-async (pipeline/get-pipeline :auto) ctx {})
                       (json {:status :success}))))
      ring-kw/wrap-keyword-params
      ring-json/wrap-json-params
      ))
