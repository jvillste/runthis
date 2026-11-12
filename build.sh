#!/usr/bin/env bash
set -euo pipefail

project_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cd "$project_directory/frontend"
npm install
rm -rf "$project_directory/frontend/public/js"
npx shadow-cljs release app

rm -rf "$project_directory/backend/resources/public"
mkdir -p "$project_directory/backend/resources"
cp -R "$project_directory/frontend/public" "$project_directory/backend/resources/public"

cd "$project_directory/backend"
lein clean
lein uberjar

jar_path="$(find target/uberjar -maxdepth 1 -name '*-standalone.jar' | head -1)"
echo "Built $project_directory/backend/$jar_path"
