# Frontend

Reagent frontend for Runthis.

### Development mode

```
npm install
npx shadow-cljs watch app
```

The build writes `public/js/app.js`. The backend serves `public` at
http://localhost:3000, so start the backend too.

The front page lists registered tasks. Clicking a task opens
`/task/<task key>`, which shows the task's runs and live run output. Logs from
all previous runs are listed and can be opened.

### Building for production

```
npx shadow-cljs release app
```

The root `build.sh` runs this release build and copies `public` into
`backend/resources/public` before creating the standalone backend jar.
