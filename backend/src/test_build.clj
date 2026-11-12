(ns test-build
  (:require
    [runthis.build :as build]))

(defn build []
  (println "building runthis"))

(build/register-build! :runthis build)
