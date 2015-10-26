(ns minimesos-pipeline.pipeline
  (:use [lambdacd.steps.control-flow]
        [minimesos-pipeline.steps])
  (:require
    [lambdacd.steps.manualtrigger :as manualtrigger]
    [lambdacd.steps.git :as git]
    [clojure.tools.logging :as log]
    [lambdacd.steps.shell :as shell]))

(def minimesos-repo "git@github.com:ContainerSolutions/minimesos")

(defn wait-for-repo [_ ctx]
  (git/wait-for-git ctx minimesos-repo "master"))

(defn build [{cwd :cwd} ctx]
  (shell/bash ctx cwd
              "echo hi!!!"))

(defn ^{:display-type :container} with-repo [& steps]
  (git/with-git minimesos-repo steps))

(def pipeline
  `((either
     manualtrigger/wait-for-manual-trigger
     wait-for-repo)
    (with-repo build)))
