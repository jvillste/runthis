# CI server

Minimal Clojure/ClojureScript APP skeleton

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

The API has one method:

```
POST /api/get-hello
Content-Type: application/transit+json

{:name "World"}
```

It returns the Transit string `"hello World!"`.

## Running the frontend

```
cd frontend
npm install
npx shadow-cljs watch app
```

Open http://localhost:3000 while the backend is running. The frontend calls
`/api/get-hello` and shows the result on the page.

## Building a single jar

```
./build.sh
```

The script builds an optimized frontend bundle, copies `frontend/public` into
`backend/resources/public`, and runs `lein uberjar`. The frontend files are
then packaged inside the standalone jar and served from the classpath.

Run the jar from any directory:

```
java -jar backend/target/uberjar/ci-server-0.1.0-SNAPSHOT-standalone.jar
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
