(ns test-task
  (:require
   [runthis.run :as run]))

(defn execute [workspace-directory]
  ;; The JVM cannot reliably change its working directory, so runthis passes the
  ;; workspace directory to the task function and the command runs there.
  (run/run-command! workspace-directory
                    "git"
                    "clone"
                    "https://github.com/jvillste/runthis.git"
                    ".")

  (run/run-command! workspace-directory
                    "./build.sh"))

(run/register-task! :runthis execute)

(run/register-task! :runthis-2 execute)
