(ns runthis.build
  (:require
   [clojure.java.io :as io]
   [clojure.java.process :as process]
   [clojure.test :refer [deftest is]])
  (:import
   (java.time Instant)))

(def builds-directory "builds")

(defonce builds-atom (atom {}))
(defonce runs-atom (atom {}))
(defonce build-lock (Object.))

(def ^:dynamic *build-output* nil)

(defn register-build! [key function]
  (swap! builds-atom assoc key function))

(defn build-exists? [key]
  (contains? @builds-atom key))

(defn normalize-build-key [key]
  (cond
    (keyword? key) key
    (string? key) (keyword key)
    :else nil))

(defn build-key-string [key]
  (name key))

(defn build-directory [key number]
  (io/file builds-directory (build-key-string key) (str number)))

(defn log-file [key number]
  (io/file builds-directory (build-key-string key) (str number ".log")))

(defn next-build-number [build-numbers]
  (if (empty? build-numbers)
    1
    (inc (apply max build-numbers))))

(deftest test-next-build-number
  (is (= 1
         (next-build-number #{})))
  (is (= 3
         (next-build-number #{1 2})))
  (is (= 4
         (next-build-number #{1 3}))))

(deftest test-build-directory
  (is (= (io/file "builds" "runthis" "2")
         (build-directory :runthis 2))))

(deftest test-log-file
  (is (= (io/file "builds" "runthis" "2.log")
         (log-file :runthis 2))))

(defn directory-files [directory]
  (when (.exists directory)
    (.listFiles directory)))

(defn existing-build-numbers [key]
  (->> (directory-files (io/file builds-directory (build-key-string key)))
       (keep (fn [file]
               (when (.isDirectory file)
                 (parse-long (.getName file)))))
       set))

(defn next-build-number-for-key [key]
  (next-build-number (existing-build-numbers key)))

(defn create-build-directory! [key number]
  (let [directory (build-directory key number)]
    (.mkdirs directory)
    directory))

(defn log-numbers [key]
  (->> (directory-files (io/file builds-directory (build-key-string key)))
       (keep (fn [file]
               (when-some [[_ number-string] (re-matches #"(\d+)\.log"
                                                         (.getName file))]
                 (parse-long number-string))))
       sort
       vec))

(defn build-key-strings []
  (->> (directory-files (io/file builds-directory))
       (filter (fn [file]
                 (.isDirectory file)))
       (map (fn [file]
              (.getName file)))
       sort))

(defn run-summary [key number]
  (let [run (get @runs-atom [key number])]
    {:key key
     :number number
     :status (:status run :finished)
     :build-directory (str (build-directory key number))
     :log-file (str (log-file key number))}))

(defn run-identifiers []
  (let [registered-runs (for [key (keys @builds-atom)
                              number (log-numbers key)]
                          [key number])
        logged-runs (for [key-string (build-key-strings)
                          :let [key (keyword key-string)]
                          number (log-numbers key)]
                      [key number])]
    (->> (concat registered-runs
                 logged-runs
                 (keys @runs-atom))
         distinct
         (sort-by (fn [[key number]]
                    [(build-key-string key) number]))
         vec)))

(defn list-builds []
  {:build-keys (->> (keys @builds-atom)
                    (sort-by build-key-string)
                    vec)
   :runs (mapv (fn [[key number]]
                 (run-summary key number))
               (run-identifiers))})

(defn write-output! [text]
  (when *build-output*
    (*build-output* text)))

(defn run-command! [directory & command]
  (let [process-handle (apply process/start {:dir directory
                                             :out :pipe
                                             :err :stdout}
                              command)]
    (with-open [reader (io/reader (process/stdout process-handle))]
      (doseq [line (line-seq reader)]
        (write-output! (str line "\n"))))
    (let [exit-value @(process/exit-ref process-handle)]
      (when-not (zero? exit-value)
        (throw (ex-info (str "command failed with exit value " exit-value)
                        {:command command
                         :exit-value exit-value}))))))

(defn run-build! [key]
  (let [build-function (get @builds-atom key)]
    (when-not build-function
      (throw (ex-info "unknown build" {:key key})))
    (let [[number build-directory build-log-file]
          (locking build-lock
            (let [number (next-build-number-for-key key)
                  build-directory (create-build-directory! key number)
                  build-log-file (log-file key number)]
              [number build-directory build-log-file]))
          run-id [key number]
          run {:key key
               :number number
               :status :running
               :build-directory (str build-directory)
               :log-file (str build-log-file)
               :started-at (str (Instant/now))
               :output ""}]
      (swap! runs-atom assoc run-id run)
      (future
        (with-open [writer (io/writer build-log-file)]
          (let [write! (fn [text]
                         (.write writer text)
                         (.flush writer)
                         (swap! runs-atom update-in [run-id :output] (fnil str "") text))]
            (try
              (binding [*build-output* write!]
                (write! (str "=== build " (build-key-string key) " #" number " ===\n"))
                (build-function build-directory))
              (write! "=== build succeeded ===\n")
              (swap! runs-atom assoc-in [run-id :status] :succeeded)
              (catch Throwable throwable
                (write! (str "=== build failed: " (.getMessage throwable) " ===\n"))
                (swap! runs-atom assoc-in [run-id :status] :failed))
              (finally
                (swap! runs-atom assoc-in [run-id :finished-at] (str (Instant/now))))))))
      (run-summary key number))))

(defn start-build! [key]
  (when-not (build-exists? key)
    (throw (ex-info "unknown build" {:key key})))
  (run-build! key))

(defn normalize-offset [offset]
  (cond
    (number? offset) (max 0 offset)
    (string? offset) (max 0 (or (parse-long offset) 0))
    :else 0))

(deftest test-normalize-offset
  (is (= 0
         (normalize-offset nil)))
  (is (= 0
         (normalize-offset -1)))
  (is (= 5
         (normalize-offset "5"))))

(defn output-for-run [key number offset]
  (let [run-id [key number]
        active-output (get-in @runs-atom [run-id :output])
        build-log-file (log-file key number)
        output (if (some? active-output)
                 active-output
                 (if (.exists build-log-file)
                   (slurp build-log-file)
                   ""))
        offset (normalize-offset offset)
        next-offset (count output)]
    {:key key
     :number number
     :output (subs output (min offset next-offset))
     :next-offset next-offset
     :status (get-in @runs-atom [run-id :status] :finished)}))
