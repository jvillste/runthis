(defproject ci-server "0.1.0-SNAPSHOT"
  :description "Minimal CI server backend"
  :url "http://example.com/ci-server"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [ring/ring-core "1.15.5"]
                 [ring/ring-jetty-adapter "1.15.5"]
                 [com.cognitect/transit-clj "1.1.363"]
                 [org.clojure/tools.namespace "1.5.1"]]
  :main ^:skip-aot ci-server.core
  :target-path "target/%s"
  :test-paths ["test" "src"]
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
