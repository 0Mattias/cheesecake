"""Turns ReplayBench's JMH results into ns and bytes per lookup, against today's code."""
import json
import sys

count_file, results_file, gc_file = sys.argv[1:4]
lookups = int(open(count_file).read().split()[0])
results = {r["benchmark"].rsplit(".", 1)[1]: r for r in json.load(open(results_file))}
alloc = {}
try:
    for r in json.load(open(gc_file)):
        norm = r.get("secondaryMetrics", {}).get("gc.alloc.rate.norm") or r.get("secondaryMetrics", {}).get("·gc.alloc.rate.norm")
        if norm:
            alloc[r["benchmark"].rsplit(".", 1)[1]] = norm["score"] / lookups
except (OSError, ValueError):
    pass

variants = [("Current", "today's code"), ("RecordVolatile", "record + volatile"), ("RecordPlain", "record, plain field"),
            ("Pr5156Record", "record as in #5156"), ("PerSolver", "cache per solver"),
            ("Pr5155PerSolver", "cache per solver as in #5155, registry included"), ("PerThread", "ThreadLocal")]
print(f"{lookups:,} lookups per replay; ± is JMH's 99.9% confidence interval\n")
for impl, label in (("java", "Java chunks (1.21.4)"), ("native", "native chunks (26.3)")):
    for threads in ("", "2"):
        base = results.get(impl + "Current" + threads)
        if not base:
            continue
        b = base["primaryMetric"]["score"]
        title = f"{label}, {'one thread' if not threads else 'two threads sharing it'}"
        print(f"| {title} | ns/lookup | vs today | B/lookup |")
        print("|---|---|---|---|")
        for v, name in variants:
            r = results.get(impl + v + threads)
            if not r:
                continue
            m = r["primaryMetric"]
            ns = m["score"] * 1e6 / lookups
            e = m["scoreError"]
            err = (e if isinstance(e, (int, float)) else float("nan")) * 1e6 / lookups
            delta = "" if v == "Current" else f"{(m['score'] / b - 1) * 100:+.1f}%"
            a = alloc.get(impl + v) if not threads else None
            print(f"| {name} | {ns:.2f} ± {err:.2f} | {delta} | {'' if a is None else f'{a:.2f}'} |")
        print()
