(defproject minimesos-pipeline "0.1.0-SNAPSHOT"
  :description "minimesos build pipeline"
  :url ""
  :dependencies [[lambdacd "0.5.7-SNAPSHOT"]
                 [ring-server "0.3.1"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.0"]
                 [ring-basic-authentication "1.0.5"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [ch.qos.logback/logback-core "1.0.13"]
                 [ch.qos.logback/logback-classic "1.0.13"]
                 [org.julienxx/clj-slack "0.5.1"]
                 [compojure "1.1.8"]
                 [org.clojure/data.json "0.2.5"]
                 [ring/ring-json "0.3.1"]]

  :profiles {:uberjar {:aot :all}
             :prod {:main minimesos-pipeline.core}}
  :main minimesos-pipeline.dev
  )
