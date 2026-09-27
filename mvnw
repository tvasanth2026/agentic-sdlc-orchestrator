#!/bin/sh
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DIST_DIR="${HOME}/.m2/wrapper/dists/apache-maven-3.9.16-agentic"
MAVEN_HOME="${DIST_DIR}/apache-maven-3.9.16"
if [ ! -x "${MAVEN_HOME}/bin/mvn" ]; then
  mkdir -p "${DIST_DIR}"
  ARCHIVE="${DIST_DIR}/maven.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip" -o "${ARCHIVE}"
  else
    wget -q "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip" -O "${ARCHIVE}"
  fi
  unzip -q -o "${ARCHIVE}" -d "${DIST_DIR}"
  rm -f "${ARCHIVE}"
fi
exec "${MAVEN_HOME}/bin/mvn" -f "${BASE_DIR}/pom.xml" "$@"

