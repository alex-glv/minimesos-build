(ns minimesos-pipeline.api
  (:use compojure.core)
  (require [clojure.string :as string]
           [ring.middleware.json :as ring-json]
           [minimesos-pipeline.pipeline :as pipeline]
           [cheshire.core :as ch]
           [cheshire.generate :as chg]
           [lambdacd.runners :as runners]
           [lambdacd.core :as lambdacd]
           [lambdacd.event-bus :as event-bus]))

(defn json [data]
  {:headers { "Content-Type" "application/json"}
   :body (ch/generate-string data)
   :status 200 })

(defn rest-api [ctx]
  (ring-json/wrap-json-params
   (context "/api" []
    (POST "/github/pr/:pr" {{pr :pr} :params data :json-params}
          (do
            (event-bus/publish ctx :pr-trigger {:final-result {:status :success :step-name (str "Building PR " pr)}})
            (pipeline/run-async (pipeline/get-pipeline :auto) ctx {:pr-id pr})
            (json {:status :success})))

    (POST "/github/tag/:tag" {{tag :tag} :params data :json-params}
          (do
            (event-bus/publish ctx :tag-trigger {:final-result {:status :success :step-name (str "Building tag " tag)}})
            (pipeline/run-async (pipeline/get-pipeline :auto) ctx {:tag-id tag})
            (json {:status :success})))
    (POST "/github/commit" []
          (do
            (pipeline/run-async (pipeline/get-pipeline :auto) ctx {})
            (json {:status :success}))))))
