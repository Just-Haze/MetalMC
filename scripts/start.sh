#!/usr/bin/env bash
# MetalMC launcher with tuned JVM flags (Java 25+).
#
# Usage: ./start.sh [server jar]
#   MEMORY=8G ./start.sh            # heap size (default 4G), used for both -Xms and -Xmx
#   JAVA=/path/to/java ./start.sh   # java binary (default: java on PATH)
#   EXTRA_JVM_ARGS="..." ./start.sh # extra JVM flags appended before -jar
#
# Flags: Aikar's G1 flags (https://docs.papermc.io/paper/aikars-flags) plus
# compact object headers (JEP 519, Java 25), which shrink every Java object by
# 4 bytes. Minecraft allocates huge numbers of small objects, so this lowers
# memory use and GC pressure.

set -euo pipefail

JAVA="${JAVA:-java}"
MEMORY="${MEMORY:-4G}"
JAR="${1:-$(ls -1 metalmc-*.jar 2>/dev/null | sort -V | tail -n 1)}"

if [[ -z "${JAR}" || ! -f "${JAR}" ]]; then
    echo "No MetalMC jar found. Pass the jar path as the first argument." >&2
    exit 1
fi

java_major="$("${JAVA}" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}' | cut -d. -f1)"
if [[ -z "${java_major}" || "${java_major}" -lt 25 ]]; then
    echo "MetalMC needs Java 25 or newer (found: ${java_major:-unknown})." >&2
    exit 1
fi

# Heap size in MB, to pick the G1 tuning profile.
case "${MEMORY}" in
    *[gG]) heap_mb=$(( ${MEMORY%[gG]} * 1024 )) ;;
    *[mM]) heap_mb=${MEMORY%[mM]} ;;
    *) echo "MEMORY must end in G or M (e.g. 4G, 6144M)." >&2; exit 1 ;;
esac

if (( heap_mb > 12 * 1024 )); then
    g1_new=40; g1_max_new=50; g1_region=16M; g1_reserve=15; g1_ihop=20
else
    g1_new=30; g1_max_new=40; g1_region=8M; g1_reserve=20; g1_ihop=15
fi

exec "${JAVA}" \
    -Xms"${MEMORY}" -Xmx"${MEMORY}" \
    -XX:+UseG1GC \
    -XX:+ParallelRefProcEnabled \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+DisableExplicitGC \
    -XX:+AlwaysPreTouch \
    -XX:G1NewSizePercent="${g1_new}" \
    -XX:G1MaxNewSizePercent="${g1_max_new}" \
    -XX:G1HeapRegionSize="${g1_region}" \
    -XX:G1ReservePercent="${g1_reserve}" \
    -XX:G1HeapWastePercent=5 \
    -XX:G1MixedGCCountTarget=4 \
    -XX:InitiatingHeapOccupancyPercent="${g1_ihop}" \
    -XX:G1MixedGCLiveThresholdPercent=90 \
    -XX:G1RSetUpdatingPauseTimePercent=5 \
    -XX:SurvivorRatio=32 \
    -XX:+PerfDisableSharedMem \
    -XX:MaxTenuringThreshold=1 \
    -XX:+UseCompactObjectHeaders \
    --enable-native-access=ALL-UNNAMED \
    -Dusing.aikars.flags=https://mcflags.emc.gs \
    -Daikars.new.flags=true \
    ${EXTRA_JVM_ARGS:-} \
    -jar "${JAR}" --nogui
