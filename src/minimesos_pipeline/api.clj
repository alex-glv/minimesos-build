(ns minimesos-pipeline.api
  (:use compojure.core)
  (require [clojure.string :as string]
           [ring.middleware.json :as ring-json]
           
           [minimesos-pipeline :as pipeline]))

(defn rest-api []
  (ring-json/wrap-json-params
   (routes
    
    (POST "/github/pr/:pr" {{pr :pr} :params data :json-params}
          (do
            
            (util/json {:status :success})))

    (POST "/github/tag/:tag" {{tag :tag} :params data :json-params}
          (do
            
            (util/json {:status :success}))))))
