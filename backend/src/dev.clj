(ns dev
  (:require [clojure.tools.namespace.repl :as repl]
            [runthis.core :as runthis]))

(defn stop []
  (runthis/stop-server))

(defn start []
  (runthis/start-server))

(defn restart []
  (runthis/stop-server)
  (repl/refresh :after 'runna/start-server))
