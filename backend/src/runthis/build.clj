(ns runthis.build)

(def builds-atom (atom {}))

(defn register-build! [key function]
  (swap! builds-atom assoc key function))
