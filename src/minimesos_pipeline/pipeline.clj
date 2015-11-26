(ns minimesos-pipeline.pipeline
  (:use [lambdacd.steps.control-flow]
        [minimesos-pipeline.steps])
  (:require
   [clojure.core.async :as async]
   [minimesos-pipeline.slack :as slack]
   [lambdacd.steps.manualtrigger :as manualtrigger]
   [minimesos-pipeline.steps :as steps]
   [lambdacd.internal.execution :as execution]
   [lambdacd.steps.git :as git]
   [clojure.java.io :as io]
   [lambdacd.util :as util]
   [clojure.string :as s]
   [lambdacd.internal.pipeline-state :as pipeline-state]
   [lambdacd.core  :as core]
   [clojure.tools.logging :as log]
   [lambdacd.steps.shell :as shell]
   [minimesos-pipeline.plugin :as plg]
   [lambdacd.steps.support :as support]))

(defn report [publisher msg]
  (log/info "Called report with " publisher msg)
  ;; (slack/send-message msg)
  (async/>!! publisher {:topic :info :payload msg})
  )

(def minimesos-repo "https://github.com/ContainerSolutions/minimesos.git")

(defn wait-for-repo [_ ctx]
  (let [wait-result (git/wait-with-details ctx minimesos-repo "master")]
    (assoc wait-result :revision (:revision wait-result) :step-name "Github trigger")))

(defn run-tests [{cwd :cwd} ctx]
  (report (:event-publisher ctx) "Running tests...")
  (assoc
   (shell/bash ctx cwd
               "sh -c './gradlew test'")
   :step-name "tests"))

(defn compile-sources [{cwd :cwd} ctx]
  (report (:event-publisher ctx) "Compiling...")
  (assoc
   (shell/bash ctx cwd
               "sh -c './gradlew clean compileJava compileTestJava'")
   :step-name "compile"))

(defn build-docker [{cwd :cwd} ctx]
  (report (:event-publisher ctx) "Building docker image...")
  (assoc
   (shell/bash ctx cwd
               "sh -c './gradlew buildDockerImage'")
   :step-name "buildDockerImage"))

(defn trigger-readthedocs [{cwd :cwd} ctx]
  (report (:event-publisher ctx) "Triggering documentation update...")
  (assoc (shell/bash ctx cwd  "curl -X POST 'https://readthedocs.org/build/minimesos'") :step-name "trigger-readthedocs"))


(defn trigger-jitpack [args ctx]
  (report (:event-publisher ctx) "Triggering jitpack build...")
  (let [revision (:revision args)        
        _ (log/info "Triggering jitpack, revision: " revision )
        build-log (slurp (format "https://jitpack.io/com/github/ContainerSolutions/minimesos/%s/build.log" revision))
        jp-success? (not= nil?
                          (re-find #"BUILD SUCCESS" build-log))]
    (log/info "Jitpack response: " jp-success?)
    {:build-log build-log
     :step-name "trigger-jitpack"
     :status (if jp-success? :success :fail)}))

;; (defn github-task [args ctx]
;;   (cond
;;     (not= nil (:pr-id args)) {:status :success :out "" :step-name (str "Checking out PR: " (:pr-id args))}
;;     (not= nil (:tag-id args)) {:status :success :out "" :step-name (str "Checking out tag: " (:tag-id args))}
;;     :else (if (nil? (:revision args))
;;             {:status :success :out "" :step-name (str "Checking out master branch ")}
;;             {:status :success :out "" :step-name (str "Fetching revision: " (:revision args))})))

;; trigger jitpack
;; trigger readthedocs
;; update website
(declare with-repo)

(defn run-async [pipeline context args]
  (let [build-number (pipeline-state/next-build-number (:pipeline-state-component context))]
    (let [runnable-pipeline (map eval pipeline)]
      (async/thread (execution/execute-steps
                     runnable-pipeline
                     args
                     (let [new-ctx (merge context {:result-channel (async/chan (async/dropping-buffer 100))
                                                   :step-id []
                                                   :build-number build-number})
                           _ (plg/bootstrap-agents new-ctx)]
                       new-ctx))))))



(defn ^{:display-type :container} with-repo [& steps]
  (fn [args ctx]
    
    (log/info "With-repo args: " args)
    (if (nil? (:revision args))
      (cond
        (not= nil (:pr-id args)) (do (report (:event-publisher ctx) (str "Building pull request " (:pr-id args)))
                                     (git/checkout-and-execute minimesos-repo (:pr-id args) args ctx steps))
        (not= nil (:tag-id args)) (do (report (:event-publisher ctx (str "Building tag id " (:tag-id args))))
                                      (with-tag ctx args minimesos-repo (:tag-id args) steps))
        :else (do (report (:event-publisher ctx) (str "Building master branch"))
                  (git/checkout-and-execute minimesos-repo "master" args ctx steps)))
      (git/checkout-and-execute minimesos-repo (:revision args) args ctx steps))))


(defn get-pipeline
  ([] (get-pipeline :manual))
  ([exec-type]
   (let [pl `((with-repo
                github-task
                compile-sources
                run-tests 
                ;; build-docker
                trigger-readthedocs
                trigger-jitpack))]
     (if (= :manual exec-type)
       `((either wait-for-repo manualtrigger/wait-for-manual-trigger) ~@pl)
       pl))))
