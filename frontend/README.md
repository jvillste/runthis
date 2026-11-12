# Frontend

Reagent frontend for the CI server.

### Development mode

```
npm install
npx shadow-cljs watch app
```

The build writes `public/js/app.js`. The backend serves `public` at
http://localhost:3000, so start the backend too.

### Building for production

```
npx shadow-cljs release app
```
