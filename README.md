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

To build an optimized frontend bundle:

```
npx shadow-cljs release app
```
