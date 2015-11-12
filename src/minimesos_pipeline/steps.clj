(ns minimesos-pipeline.steps
  (:require [lambdacd.steps.shell :as shell]
            [clojure.tools.logging :as log]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [lambdacd.util :as util]
            [lambdacd.steps.git :as git]
            [lambdacd.core :as core]
            [lambdacd.steps.status :as status]))

(defn with-tag [ctx args repo-uri tag steps]
  ;; get pull request commit
  ;; git fetch remote refs/pr/:pr-id/head 
  ;; trigger build with revision
  (let  [dir (util/create-temp-dir (:home-dir (:config ctx)))
         _ (shell/bash ctx dir (str "git clone --depth 100 " repo-uri " ."))
         log-result (shell/bash ctx
                                (io/file dir)
                                (str "git checkout " tag))
         _ (log/info "Tag clone: " log-result)
         log-output (:out log-result)
         revision-out (shell/bash ctx dir (str "git rev-parse " tag))
         rev-parse (s/trim (:out revision-out))]
    (util/with-temp dir
      (if (and (zero? (:exit log-result)) (zero? (:exit revision-out)))
        (git/checkout-and-execute repo-uri rev-parse (assoc args :revision rev-parse :cwd dir) ctx steps)
        {:status :failed
         :out (:out log-result)
         :exit (:exit log-result)}))))

(defn with-pull-request [ctx args repo-uri pr-id steps]
  ;; get pull request commit
  ;; git fetch remote refs/pr/:pr-id/head 
  ;; trigger build with revision
  (let [dir (util/create-temp-dir (:home-dir (:config ctx)))
        _ (shell/bash ctx dir (str "git clone --depth 100 " repo-uri " ."))
        log-result (shell/bash ctx
                               (io/file dir)
                               (str "git fetch " repo-uri " refs/pull/" pr-id "/head &&"
                                    "git checkout -b pr" pr-id " FETCH_HEAD"))
        _ (log/info "PR branch clone: " log-result)
        log-output (:out log-result)
        revision-out (shell/bash ctx dir (str "git rev-parse pr" pr-id))
        rev-parse (s/trim (:out revision-out))]
    (util/with-temp dir
      (if (and (zero? (:exit log-result)) (zero? (:exit revision-out)))
        (git/checkout-and-execute repo-uri rev-parse (assoc args :revision rev-parse :cwd dir) ctx steps)
        {:status :failed
         :out (:out log-result)
         :exit (:exit log-result)}))))
