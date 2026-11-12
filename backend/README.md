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

The backend serves the frontend from `../frontend/public` and exposes
`POST /api/get-hello`, which accepts a Transit JSON map with a `:name` key and
returns `"hello <name>!"`.
