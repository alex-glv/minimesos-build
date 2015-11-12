(ns minimesos-pipeline.pipeline
  (:use [lambdacd.steps.control-flow]
        [minimesos-pipeline.steps])
  (:require
   [clojure.core.async :as async]
   [lambdacd.steps.manualtrigger :as manualtrigger]
   [lambdacd.internal.execution :as execution]
   [lambdacd.steps.git :as git]
   [lambdacd.internal.pipeline-state :as pipeline-state]
   [lambdacd.core  :as core]
   [clojure.tools.logging :as log]
   [lambdacd.steps.shell :as shell]
   [lambdacd.steps.support :as support]))

(def minimesos-repo "https://github.com/ContainerSolutions/minimesos.git")

(defn wait-for-repo [_ ctx]
  (let [wait-result (git/wait-with-details ctx minimesos-repo "master")]
    (assoc wait-result :revision (:revision wait-result) :step-name "Github trigger")))

(defn run-tests [{cwd :cwd} ctx]
  (assoc
   (shell/bash ctx cwd
               "sh -c './gradlew test'")
   :step-name "tests"))

(defn compile-sources [{cwd :cwd} ctx]
  (assoc
   (shell/bash ctx cwd
               "sh -c './gradlew clean compileJava compileTestJava'")
   :step-name "compile"))

(defn build-docker [{cwd :cwd} ctx]
  (assoc
   (shell/bash ctx cwd
               "sh -c './gradlew buildDockerImage'")
   :step-name "buildDockerImage"))

(defn trigger-readthedocs [{cwd :cwd} ctx]
  (assoc (shell/bash ctx cwd  "curl -X POST 'https://readthedocs.org/build/minimesos'") :step-name "trigger-readthedocs"))


(defn trigger-jitpack [args ctx]
  (let [revision (:revision args)        
        _ (log/info "Triggering jitpack, revision: " revision )
        build-log (slurp (format "https://jitpack.io/com/github/ContainerSolutions/minimesos/%s/build.log" revision))
        jp-success? (not= nil?
                          (re-find #"BUILD SUCCESS" build-log))]
    (log/info "Jitpack response: " jp-success?)
    {:build-log build-log
     :step-name "trigger-jitpack"
     :status (if jp-success? :success :fail)}))

(defn github-task [args ctx]
  (cond
    (not= nil (:pr-id args)) {:status :success :step-name (str "Checking out PR: " (:pr-id args))}
    (not= nil (:tag-id args)) {:status :success :step-name (str "Checking out tag: " (:tag-id args))}
    :else (if (nil? (:revision args))
            {:status :success :step-name (str "Checking out master branch ")}
            {:status :success :step-name (str "Fetching revision: " (:revision args))})))

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
                     (merge context {:result-channel (async/chan (async/dropping-buffer 0))
                                     :step-id []
                                     :build-number build-number}))))))

(defn report [msg]
  {:status :success :step-name msg})

(defn ^{:display-type :container} with-repo [& steps]
  (fn [args ctx]
    (log/info "With-repo args: " args)
    (if (nil? (:revision args))
      (cond
        (not= nil (:pr-id args)) ((git/with-pull-request ctx minimesos-repo (:pr-id args) steps) args ctx)
        (not= nil (:tag-id args)) ((git/with-tag ctx minimesos-repo (:tag-id args) steps) args ctx)
        :else (git/checkout-and-execute minimesos-repo "master" args ctx steps :branch))
      (git/checkout-and-execute minimesos-repo (:revision args) args ctx steps))))

(defn get-pipeline
  ([] (get-pipeline :manual))
  ([exec-type]
   (let [pl `((with-repo
                github-task
                compile-sources
                (report "Compilation completed... Running tests.")
                run-tests
                (report "Tests complete... Building docker image.")
                build-docker
                (report "Docker image complete. Updating the docs and jitpack build")
                (in-parallel
                 trigger-jitpack
                 trigger-readthedocs)))]
     (if (= :manual exec-type)
       `(wait-for-repo ~@pl)
       pl))))
