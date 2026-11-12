# Runthis

Minimal Clojure/ClojureScript CI server.

The backend is a Ring application running on Jetty. It serves the static
frontend files and exposes Transit JSON API methods. The frontend is a
shadow-cljs, Reagent and cljs-ajax single-page application.

## Running the backend

```
cd backend
lein test
lein run
```

The backend listens on http://localhost:3000 and serves
`../frontend/public`. Start it from the `backend` directory so it can find the
frontend files.

## Running the frontend

```
cd frontend
npm install
npx shadow-cljs watch app
```

Open http://localhost:3000 while the backend is running.

## Tasks

Tasks register themselves in `runthis.run/tasks-atom`. The first task is
`backend/src/test_task.clj`:

```clojure
(ns test-task
  (:require
   [runthis.run :as run]))

(defn execute [run-directory]
  (run/run-command! run-directory
                    "git"
                    "clone"
                    "https://github.com/jvillste/runthis.git"
                    "."))

(run/register-task! :runthis execute)
```

The JVM cannot reliably change its own working directory, so the runner passes
the run directory to the task function. `run-command!` runs the command in that
directory and streams its stdout and stderr into the run log.

## Run dashboard

The front page lists every registered task by id. Clicking a task opens its
deep URL:

```
/task/<task id>
```

The task page shows a `home/<task id>` breadcrumb, a **Run** button, all runs
for that task, and the selected run's log. Pressing **Run** creates:

```
runs/<task id>/<run number>/
runs/<task id>/<run number>.log
```

The run number is the next available number for that task. The UI polls the
backend while a run is in progress and appends stdout to the log view. Every
log on disk is listed, including logs from previous server runs.

## API

```
POST /api/runs
POST /api/start-run          {:id :runthis}
POST /api/run-output         {:id :runthis :number 1 :offset 0}
```

`/api/run-output` returns the next chunk of output and the current status.

## Building a single jar

```
./build.sh
```

The script builds an optimized frontend bundle, copies `frontend/public` into
`backend/resources/public`, and runs `lein uberjar`. The frontend files are
then packaged inside the standalone jar and served from the classpath.

Run the jar from any directory:

```
java -jar backend/target/uberjar/runthis-0.1.0-SNAPSHOT-standalone.jar
```

To build the jar manually:

```
cd frontend
npx shadow-cljs release app
mkdir -p ../backend/resources/public
cp -R public/. ../backend/resources/public/
cd ../backend
lein uberjar
```
