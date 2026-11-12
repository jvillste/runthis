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

## Build scripts

Build scripts register themselves in `runthis.build/builds-atom`. The first
one is `backend/src/test_build.clj`:

```clojure
(ns test-build
  (:require
   [runthis.build :as build]))

(defn build [build-directory]
  (build/run-command! build-directory
                      "git"
                      "clone"
                      "https://github.com/jvillste/runthis.git"
                      "."))

(build/register-build! :runthis build)
```

The JVM cannot reliably change its own working directory, so the runner passes
the build directory to the build function. `run-command!` runs the command in
that directory and streams its stdout and stderr into the build log.

## Build dashboard

The UI lists every registered build script by key. Pressing **Run** creates:

```
builds/<build key>/<build number>/
builds/<build key>/<build number>.log
```

The build number is the next available number for that key. The UI polls the
backend while a build is running and appends stdout to the log view. Every log
on disk is listed, including logs from previous server runs.

## API

```
POST /api/builds
POST /api/start-build        {:key :runthis}
POST /api/build-output       {:key :runthis :number 1 :offset 0}
```

`/api/build-output` returns the next chunk of output and the current status.

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
