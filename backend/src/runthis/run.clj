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

(defn register-task! [task-id function]
  (swap! tasks-atom assoc task-id function))

(defn task-exists? [task-id]
  (contains? @tasks-atom task-id))

(defn normalize-task-id [task-id]
  (cond
    (keyword? task-id) task-id
    (string? task-id) (keyword task-id)
    :else nil))

(defn task-id-string [task-id]
  (name task-id))

(defn run-directory [task-id number]
  (io/file runs-directory (task-id-string task-id) (str number)))

(defn log-file [task-id number]
  (io/file runs-directory (task-id-string task-id) (str number ".log")))

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

(defn existing-run-numbers [task-id]
  (->> (directory-files (io/file runs-directory (task-id-string task-id)))
       (keep (fn [file]
               (when (.isDirectory file)
                 (parse-long (.getName file)))))
       set))

(defn next-run-number-for-task [task-id]
  (next-run-number (existing-run-numbers task-id)))

(defn create-run-directory! [task-id number]
  (let [directory (run-directory task-id number)]
    (.mkdirs directory)
    directory))

(defn log-numbers [task-id]
  (->> (directory-files (io/file runs-directory (task-id-string task-id)))
       (keep (fn [file]
               (when-some [[_ number-string] (re-matches #"(\d+)\.log"
                                                         (.getName file))]
                 (parse-long number-string))))
       sort
       vec))

(defn task-id-strings []
  (->> (directory-files (io/file runs-directory))
       (filter (fn [file]
                 (.isDirectory file)))
       (map (fn [file]
              (.getName file)))
       sort))

(defn run-summary [task-id number]
  (let [run (get @runs-atom [task-id number])]
    {:id task-id
     :number number
     :status (:status run :finished)
     :run-directory (str (run-directory task-id number))
     :log-file (str (log-file task-id number))}))

(defn run-identifiers []
  (let [registered-runs (for [task-id (keys @tasks-atom)
                              number (log-numbers task-id)]
                          [task-id number])
        logged-runs (for [id-string (task-id-strings)
                          :let [task-id (keyword id-string)]
                          number (log-numbers task-id)]
                      [task-id number])]
    (->> (concat registered-runs
                 logged-runs
                 (keys @runs-atom))
         distinct
         (sort-by (fn [[task-id number]]
                    [(task-id-string task-id) number]))
         vec)))

(defn list-runs []
  {:task-ids (->> (keys @tasks-atom)
                  (sort-by task-id-string)
                  vec)
   :runs (mapv (fn [[task-id number]]
                 (run-summary task-id number))
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

(defn run-task! [task-id]
  (let [task-function (get @tasks-atom task-id)]
    (when-not task-function
      (throw (ex-info "unknown task" {:id task-id})))
    (let [[number run-directory run-log-file]
          (locking run-lock
            (let [number (next-run-number-for-task task-id)
                  run-directory (create-run-directory! task-id number)
                  run-log-file (log-file task-id number)]
              [number run-directory run-log-file]))
          run-id [task-id number]
          run {:id task-id
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
                (write! (str "=== run " (task-id-string task-id) " #" number " ===\n"))
                (task-function run-directory))
              (write! "=== run succeeded ===\n")
              (swap! runs-atom assoc-in [run-id :status] :succeeded)
              (catch Throwable throwable
                (write! (str "=== run failed: " (.getMessage throwable) " ===\n"))
                (swap! runs-atom assoc-in [run-id :status] :failed))
              (finally
                (swap! runs-atom assoc-in [run-id :finished-at] (str (Instant/now))))))))
      (run-summary task-id number))))

(defn start-run! [task-id]
  (when-not (task-exists? task-id)
    (throw (ex-info "unknown task" {:id task-id})))
  (run-task! task-id))

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

(defn output-for-run [task-id number offset]
  (let [run-id [task-id number]
        active-output (get-in @runs-atom [run-id :output])
        run-log-file (log-file task-id number)
        output (if (some? active-output)
                 active-output
                 (if (.exists run-log-file)
                   (slurp run-log-file)
                   ""))
        offset (normalize-offset offset)
        next-offset (count output)]
    {:id task-id
     :number number
     :output (subs output (min offset next-offset))
     :next-offset next-offset
     :status (get-in @runs-atom [run-id :status] :finished)}))
