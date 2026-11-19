(ns test-build
  (:require
   [runthis.run :as run]))

(defn build [build-directory]
  (run/run-command! build-directory
                    "git"
                    "clone"
                    "https://github.com/jvillste/runthis.git"
                    ".")

  (run/run-command! build-directory
                    "./build.sh")

  (dotimes [line 800]
    (println "foo" line))

  (println "ready."))

(run/register-task! :runthis build)

(run/register-task! :runthis-2 build)
