# Backend

Ring/Jetty backend for the CI server.

```
lein test
lein run
```

The server listens on port 3000 by default. You can pass another port:

```
lein run 3001
```

During development the backend serves the frontend from `../frontend/public`
and exposes `POST /api/get-hello`, which accepts a Transit JSON map with a
`:name` key and returns `"hello <name>!"`.

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
