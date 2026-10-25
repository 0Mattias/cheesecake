#!/usr/bin/env bash
# Builds and runs ReplayBench over every replay set in a directory. Run from the repository root:
#   measure/run-bench.sh <dir with *.bin.gz and *.bin.gz.count> <forks>
set -euo pipefail

replays=$1
forks=${2:-4}
# iterations and their length; the defaults are what CI runs
wi=${WI:-5}; it=${IT:-10}; w=${WT:-2s}; r=${RT:-2s}
# NATIVE=false leaves out the 26.3 native library; EXCLUDES is a list of JMH -e regexes
native=${NATIVE:-true}
excludes=()
for e in ${EXCLUDES:-}; do excludes+=(-e "$e"); done
if [ "$native" != true ]; then excludes+=(-e 'ReplayBench\.native.*'); fi
sep=':'
py=python3
if [ "${RUNNER_OS:-}" = "Windows" ]; then sep=';'; py=python; fi

mkdir -p mlib mbuild
maven=https://repo1.maven.org/maven2
fetch() {
  [ -f "mlib/$2" ] || curl -sSfL --retry 5 -o "mlib/$2" "$1"
}
fetch $maven/org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar jmh-core.jar
fetch $maven/org/openjdk/jmh/jmh-generator-annprocess/1.37/jmh-generator-annprocess-1.37.jar jmh-ap.jar
fetch $maven/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar jopt.jar
fetch $maven/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar math3.jar
fetch $maven/org/tukaani/xz/1.9/xz-1.9.jar xz.jar
# the native library 26.3 ships (gradle.properties: nether_pathfinder_version=1.6)
fetch https://babbaj.github.io/maven/dev/babbaj/nether-pathfinder/1.6/nether-pathfinder-1.6.jar nether-pathfinder-1.6.jar

cp="mlib/jmh-core.jar${sep}mlib/jopt.jar${sep}mlib/math3.jar${sep}mlib/xz.jar${sep}mlib/nether-pathfinder-1.6.jar"
javac -proc:full -cp "$cp" -processorpath "mlib/jmh-ap.jar${sep}mlib/jmh-core.jar" -d mbuild $(find measure/src -name '*.java')

java -version 2>&1
for replay in "$replays"/*.bin.gz; do
  name=$(basename "$replay" .bin.gz)
  echo "::group::JMH $name"
  java -cp "mbuild${sep}$cp" org.openjdk.jmh.Main 'ReplayBench' ${excludes[@]+"${excludes[@]}"} \
    -f "$forks" -wi "$wi" -w "$w" -i "$it" -r "$r" -rf json -rff "results-$name.json" \
    -jvmArgsAppend "-Xmx3g -Dreplay=$replay -Dnative=$native" | tee "jmh-$name.log"
  echo "::endgroup::"
  echo "::group::allocation $name"
  java -cp "mbuild${sep}$cp" org.openjdk.jmh.Main 'ReplayBench\.(java|native)(Current|RecordVolatile|RecordPlain|Pr5156Record|PerThread|PerSolver|Pr5155PerSolver)$' ${excludes[@]+"${excludes[@]}"} \
    -f 1 -wi 3 -w "$w" -i 3 -r "$r" -prof gc -rf json -rff "gc-$name.json" \
    -jvmArgsAppend "-Xmx3g -Dreplay=$replay -Dnative=$native" | tee "gc-$name.log"
  echo "::endgroup::"
  $py measure/summarize.py "$replay.count" "results-$name.json" "gc-$name.json" | tee "summary-$name.md"
done
