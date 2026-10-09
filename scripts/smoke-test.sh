#!/usr/bin/env bash
# Boots a MetalMC server jar once in a temporary directory, waits for startup
# to finish, then stops it. Fails if the server crashes, never finishes
# starting, or doesn't write MetalMC's default settings.
#
# Usage: scripts/smoke-test.sh <server jar>

set -euo pipefail

JAR="$(realpath "$1")"
JAVA="${JAVA:-java}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-600}"

work="$(mktemp -d)"
cd "${work}"
echo "eula=true" > eula.txt
mkfifo console

"${JAVA}" -Xms1G -Xmx2G -jar "${JAR}" --nogui < console > server.log 2>&1 &
pid=$!
exec 3> console

fail() {
    cat server.log
    echo "::error::$1"
    kill "${pid}" 2> /dev/null || true
    exit 1
}

deadline=$(( SECONDS + TIMEOUT_SECONDS ))
until grep -q 'Done (' server.log; do
    kill -0 "${pid}" 2> /dev/null || fail "Server exited before startup finished"
    (( SECONDS < deadline )) || fail "Server did not finish starting within ${TIMEOUT_SECONDS}s"
    sleep 2
done

echo "version" >&3
sleep 5
echo "stop" >&3
wait "${pid}" || fail "Server did not shut down cleanly"
cat server.log

grep -q 'Metal' server.log || fail "Server did not report the Metal brand"
grep -q 'redstone-implementation: ALTERNATE_CURRENT' config/paper-world-defaults.yml \
    || fail "MetalMC performance defaults were not written to paper-world-defaults.yml"
echo "Smoke test passed"
