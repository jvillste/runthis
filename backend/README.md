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

## Build scripts

Build scripts are Clojure namespaces that register a function with
`runthis.build/register-build!`:

```clojure
(ns my-build
(:require
[runthis.build :as build]))

(defn build [build-directory]
  (build/run-command! build-directory "make"))

(build/register-build! :my-build build)
```

The build function receives the build directory. `run-command!` runs a command
in that directory and streams stdout and stderr into the build log.

The first build script is `src/test_build.clj`. It clones
`https://github.com/jvillste/runthis.git` into the build directory.

## Build directories and logs

Each run creates:

```
builds/<build key>/<build number>/
builds/<build key>/<build number>.log
```

The build number is the next available number for that key. Logs from all
runs remain under `builds/` and are listed by the UI.

## API

```
POST /api/builds
POST /api/start-build        {:key :runthis}
POST /api/build-output       {:key :runthis :number 1 :offset 0}
```

`/api/build-output` returns the next chunk of output and the current status.

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
