(ns runthis.run
  (:require
   [clojure.java.io :as io]
   [clojure.java.process :as process]
   [clojure.test :refer [deftest is]])
  (:import
   (java.time Instant)))

(def runs-directory "runs")

(defonce tasks-atom (atom {}))
(defonce runs-atom (atom {}))
(defonce run-lock (Object.))

(def ^:dynamic *run-output* nil)

(defn register-task! [key function]
  (swap! tasks-atom assoc key function))

(defn task-exists? [key]
  (contains? @tasks-atom key))

(defn normalize-task-key [key]
  (cond
    (keyword? key) key
    (string? key) (keyword key)
    :else nil))

(defn task-key-string [key]
  (name key))

(defn run-directory [key number]
  (io/file runs-directory (task-key-string key) (str number)))

(defn log-file [key number]
  (io/file runs-directory (task-key-string key) (str number ".log")))

(defn next-run-number [run-numbers]
  (if (empty? run-numbers)
    1
    (inc (apply max run-numbers))))

(deftest test-next-run-number
  (is (= 1
         (next-run-number #{})))
  (is (= 3
         (next-run-number #{1 2})))
  (is (= 4
         (next-run-number #{1 3}))))

(deftest test-run-directory
  (is (= (io/file "runs" "runthis" "2")
         (run-directory :runthis 2))))

(deftest test-log-file
  (is (= (io/file "runs" "runthis" "2.log")
         (log-file :runthis 2))))

(defn directory-files [directory]
  (when (.exists directory)
    (.listFiles directory)))

(defn existing-run-numbers [key]
  (->> (directory-files (io/file runs-directory (task-key-string key)))
       (keep (fn [file]
               (when (.isDirectory file)
                 (parse-long (.getName file)))))
       set))

(defn next-run-number-for-task [key]
  (next-run-number (existing-run-numbers key)))

(defn create-run-directory! [key number]
  (let [directory (run-directory key number)]
    (.mkdirs directory)
    directory))

(defn log-numbers [key]
  (->> (directory-files (io/file runs-directory (task-key-string key)))
       (keep (fn [file]
               (when-some [[_ number-string] (re-matches #"(\d+)\.log"
                                                         (.getName file))]
                 (parse-long number-string))))
       sort
       vec))

(defn task-key-strings []
  (->> (directory-files (io/file runs-directory))
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
     :run-directory (str (run-directory key number))
     :log-file (str (log-file key number))}))

(defn run-identifiers []
  (let [registered-runs (for [key (keys @tasks-atom)
                              number (log-numbers key)]
                          [key number])
        logged-runs (for [key-string (task-key-strings)
                          :let [key (keyword key-string)]
                          number (log-numbers key)]
                      [key number])]
    (->> (concat registered-runs
                 logged-runs
                 (keys @runs-atom))
         distinct
         (sort-by (fn [[key number]]
                    [(task-key-string key) number]))
         vec)))

(defn list-runs []
  {:task-keys (->> (keys @tasks-atom)
                   (sort-by task-key-string)
                   vec)
   :runs (mapv (fn [[key number]]
                 (run-summary key number))
               (run-identifiers))})

(defn write-output! [text]
  (when *run-output*
    (*run-output* text)))

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

(defn run-task! [key]
  (let [task-function (get @tasks-atom key)]
    (when-not task-function
      (throw (ex-info "unknown task" {:key key})))
    (let [[number run-directory run-log-file]
          (locking run-lock
            (let [number (next-run-number-for-task key)
                  run-directory (create-run-directory! key number)
                  run-log-file (log-file key number)]
              [number run-directory run-log-file]))
          run-id [key number]
          run {:key key
               :number number
               :status :running
               :run-directory (str run-directory)
               :log-file (str run-log-file)
               :started-at (str (Instant/now))
               :output ""}]
      (swap! runs-atom assoc run-id run)
      (future
        (with-open [writer (io/writer run-log-file)]
          (let [write! (fn [text]
                         (.write writer text)
                         (.flush writer)
                         (swap! runs-atom update-in [run-id :output] (fnil str "") text))]
            (try
              (binding [*run-output* write!]
                (write! (str "=== run " (task-key-string key) " #" number " ===\n"))
                (task-function run-directory))
              (write! "=== run succeeded ===\n")
              (swap! runs-atom assoc-in [run-id :status] :succeeded)
              (catch Throwable throwable
                (write! (str "=== run failed: " (.getMessage throwable) " ===\n"))
                (swap! runs-atom assoc-in [run-id :status] :failed))
              (finally
                (swap! runs-atom assoc-in [run-id :finished-at] (str (Instant/now))))))))
      (run-summary key number))))

(defn start-run! [key]
  (when-not (task-exists? key)
    (throw (ex-info "unknown task" {:key key})))
  (run-task! key))

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
        run-log-file (log-file key number)
        output (if (some? active-output)
                 active-output
                 (if (.exists run-log-file)
                   (slurp run-log-file)
                   ""))
        offset (normalize-offset offset)
        next-offset (count output)]
    {:key key
     :number number
     :output (subs output (min offset next-offset))
     :next-offset next-offset
     :status (get-in @runs-atom [run-id :status] :finished)}))
