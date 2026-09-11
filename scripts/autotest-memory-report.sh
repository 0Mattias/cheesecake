#!/usr/bin/env bash
#
# Reads the samples written by the in-world test job and says what the run peaked at and which
# stage it peaked in. Kept out of the workflow so that it can be run against a downloaded
# autotest-log artifact:
#
#   scripts/autotest-memory-report.sh memory.tsv autotest.log
#
# The sampler deliberately records nothing but numbers -- correlating a sample with the stage that
# was running costs a grep per sample, and on a runner that is already saturated by software
# rendering that cost lands on the thing being measured. The correlation happens here instead,
# once, after the game is gone.
#
# Sample columns: epoch-seconds, MemAvailable kB, swap used kB, client VmRSS kB, client VmHWM kB.

set -uo pipefail

samples=${1:-memory.tsv}
log=${2:-autotest.log}

if [ ! -s "$samples" ]; then
    echo "no samples were taken"
    exit 0
fi

awk '/^MemTotal:/ { printf "runner memory: %d MiB total\n", $2 / 1024 }' /proc/meminfo 2>/dev/null

echo "samples: $(wc -l < "$samples")"
echo

awk -v logfile="$log" '
    function hhmmss(secs,   t) {
        t = int(secs) % 86400
        return sprintf("%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
    }
    # The game log timestamps stages as [HH:MM:SS]; the samples are epoch seconds. Both are UTC on
    # a runner, so seconds-of-day is a common key.
    function stage_at(secs,   i, best) {
        best = "before the first stage"
        for (i = 1; i <= nstages; i++) {
            if (stage_secs[i] <= int(secs) % 86400) { best = stage_name[i] } else { break }
        }
        return best
    }
    BEGIN {
        while ((getline line < logfile) > 0) {
            gsub(/\033\[[0-9;]*m/, "", line)
            if (match(line, /\[[0-9][0-9]:[0-9][0-9]:[0-9][0-9]\]/)) {
                ts = substr(line, RSTART + 1, 8)
                if (match(line, /stage [0-9]+ of [0-9]+: [a-z0-9-]+/)) {
                    split(ts, p, ":")
                    nstages++
                    stage_secs[nstages] = p[1] * 3600 + p[2] * 60 + p[3]
                    stage_name[nstages] = substr(line, RSTART, RLENGTH)
                }
            }
        }
        close(logfile)
    }
    {
        stage = stage_at($1)
        if ($5 + 0 > hwm) { hwm = $5 + 0; hwm_stage = stage; hwm_at = $1 }
        if (nlow == 0 || $2 + 0 < low) { low = $2 + 0; low_stage = stage; low_at = $1 }
        nlow++
        if ($4 + 0 > peak[stage]) { peak[stage] = $4 + 0 }
        if (!(stage in seen)) { seen[stage] = ++order }
        if ($3 + 0 > swap) { swap = $3 + 0 }
    }
    END {
        if (nlow == 0) { print "no samples"; exit }
        printf "client high-water (VmHWM, the kernel\047s own peak for the process): %d MiB, at %s during %s\n", hwm / 1024, hhmmss(hwm_at), hwm_stage
        printf "least memory available at any sample: %d MiB, at %s during %s\n", low / 1024, hhmmss(low_at), low_stage
        printf "most swap used at any sample: %d MiB\n", swap / 1024
        printf "\npeak client RSS per stage:\n"
        for (s in peak) { printf "%d\t%d\t%s\n", seen[s], peak[s] / 1024, s | "sort -n" }
        close("sort -n")
    }
' "$samples" | awk -F'\t' 'NF == 3 { printf "  %5d MiB  %s\n", $2, $3; next } { print }'
