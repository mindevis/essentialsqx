#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mod_version=$(grep '^mod_version=' gradle.properties | head -1 | cut -d= -f2- | tr -d '\r')
base=$(grep '^archives_base_name=' gradle.properties | head -1 | cut -d= -f2- | tr -d '\r')
if [[ -z "${mod_version:-}" || -z "${base:-}" ]]; then
  echo "collect-release-jars: could not read mod_version or archives_base_name from gradle.properties" >&2
  exit 1
fi

rm -rf release-jars
mkdir -p release-jars

jar="build/libs/${base}-neoforge-${mod_version}.jar"
if [[ ! -f "$jar" ]]; then
  echo "collect-release-jars: expected jar not found: $jar" >&2
  ls -la build/libs >&2 || true
  exit 1
fi
cp "$jar" "release-jars/$(basename "$jar")"

ls -la release-jars/
