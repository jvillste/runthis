(ns runthis.core
  (:gen-class)
  (:require
   [clojure.test :refer [deftest is]]
   [cognitect.transit :as transit]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.file :as file]
   [ring.util.response :as response])
  (:import
   (java.io ByteArrayOutputStream)))

(def frontend-public-path "../frontend/public")

(defn get-hello [name]
  (str "hello " name "!"))

(deftest test-get-hello
  (is (= "hello world!"
         (get-hello "world"))))

(defn to-transit-string [value]
  (let [output-stream (ByteArrayOutputStream. 4096)
        writer (transit/writer output-stream :json)]
    (transit/write writer value)
    (.toString output-stream)))

(defn transit-response [body]
  {:status 200
   :headers {"Content-Type" "application/transit+json"}
   :body (to-transit-string body)})

(defn read-transit-body [request]
  (when-some [body (:body request)]
    (with-open [input-stream body]
      (transit/read (transit/reader input-stream :json)))))

(defn wrap-api-method [handler uri api-handler]
  (fn [request]
    (if (= uri (:uri request))
      (api-handler request)
      (handler request))))

(defn not-found-handler [_request]
  {:status 404
   :headers {"Content-Type" "text/plain"}
   :body "Not found"})

(defn hello-handler [request]
  (let [name (:name (read-transit-body request))]
    (if (string? name)
      (transit-response (get-hello name))
      (assoc (transit-response "name is required")
             :status 400))))

(defn wrap-serve-index-html [handler root]
  (fn [request]
    (if (= "/" (:uri request))
      (or (response/file-response "/index.html" {:root root})
          (handler request))
      (handler request))))

(def handler (-> not-found-handler
                 (file/wrap-file frontend-public-path)
                 (wrap-serve-index-html frontend-public-path)
                 (wrap-api-method "/api/get-hello" hello-handler)))

(defonce server-atom (atom nil))

(defn stop-server []
  (when @server-atom
    (doto @server-atom
      .stop
      .join)

    (reset! server-atom nil)))

(defn start-server [& [{:keys [port] :or {port 3000}}]]
  (reset! server-atom
          (jetty/run-jetty handler
                           {:port port
                            :join? false})))

(defn -main [& [port]]
  (start-server {:port (if port
                         (Integer/parseInt port)
                         3000)}))
