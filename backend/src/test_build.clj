(ns test-build
  (:require
   [runthis.build :as build]))

(defn build [build-directory]
  ;; The JVM cannot reliably change its working directory, so runthis passes the
  ;; build directory to the build function and the command runs there.
  (build/run-command! build-directory
                      "git"
                      "clone"
                      "https://github.com/jvillste/runthis.git"
                      ".")

  (build/run-command! build-directory
                      "./build.sh"))

(build/register-build! :runthis build)

(build/register-build! :runthis-2 build)
