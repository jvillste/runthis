(ns runthis.core
  (:gen-class)
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer [deftest is]]
   [cognitect.transit :as transit]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.file :as file]
   [ring.middleware.resource :as resource]
   [ring.util.response :as response]
   [runthis.build :as build]
   [test-build])
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

(defn build-list-handler [_request]
  (transit-response (build/list-builds)))

(defn start-build-handler [request]
  (let [key (build/normalize-build-key (:key (read-transit-body request)))]
    (if (and key (build/build-exists? key))
      (transit-response (build/start-build! key))
      (assoc (transit-response {:error "unknown build"})
             :status 400))))

(defn build-output-handler [request]
  (let [{:keys [key number offset]} (read-transit-body request)
        key (build/normalize-build-key key)
        number (if (string? number)
                 (parse-long number)
                 number)]
    (if (and key (number? number))
      (transit-response (build/output-for-run key number offset))
      (assoc (transit-response {:error "build key and number are required"})
             :status 400))))

(defn running-from-jar? []
  (when-some [source-resource (io/resource "runthis/core.clj")]
    (string/includes? (str source-resource) "jar:")))

(defn wrap-serve-index-html [handler]
  (fn [request]
    (if (= "/" (:uri request))
      (or (response/resource-response "public/index.html")
          (handler request))
      (handler request))))

(defn wrap-frontend-directory [handler]
  (if (and (not (running-from-jar?))
           (.exists (io/file frontend-public-path)))
    (file/wrap-file handler frontend-public-path)
    handler))

(def handler (-> not-found-handler
                 (resource/wrap-resource "public")
                 (wrap-serve-index-html)
                 (wrap-frontend-directory)
                 (wrap-api-method "/api/get-hello" hello-handler)
                 (wrap-api-method "/api/builds" build-list-handler)
                 (wrap-api-method "/api/start-build" start-build-handler)
                 (wrap-api-method "/api/build-output" build-output-handler)))

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
