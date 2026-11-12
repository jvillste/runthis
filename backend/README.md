# Backend

Ring/Jetty backend for Runthis.

```
lein test
lein run
```

The server listens on port 3000 by default. You can pass another port:

```
lein run 3001
```

During development the backend serves the frontend from `../frontend/public`.

## Tasks

Tasks are Clojure namespaces that register a function with
`runthis.run/register-task!`:

```clojure
(ns my-task
  (:require
   [runthis.run :as run]))

(defn execute [run-directory]
  (run/run-command! run-directory "make"))

(run/register-task! :my-task execute)
```

The task function receives the run directory. `run-command!` runs a command in
that directory and streams stdout and stderr into the run log.

The first task is `src/test_task.clj`. It clones
`https://github.com/jvillste/runthis.git` into the run directory.

## Run directories and logs

Each run creates:

```
runs/<task id>/<run number>/
runs/<task id>/<run number>.log
```

The run number is the next available number for that task. Logs from all runs
remain under `runs/` and are listed by the UI.

## API

```
POST /api/runs
POST /api/start-run          {:id :runthis}
POST /api/run-output         {:id :runthis :number 1 :offset 0}
```

`/api/run-output` returns the next chunk of output and the current status.

## Building a single jar

From the repository root:

```
./build.sh
```

This builds the frontend, copies it into `backend/resources/public`, and runs
`lein uberjar`. The resulting standalone jar contains both the backend and the
frontend:

```
java -jar target/uberjar/runthis-0.1.0-SNAPSHOT-standalone.jar
```
