#!/usr/bin/env python3
"""A load test's stages side by side (ops/kubernetes/loadtest.sh): for each stage the driver held, its
matches and players and their round trips to the match server; what the match server's status lines
said in that time (its CPU, how late its ticks ran, its heap); and Docker's stats of the containers
(the match server's CPU and memory as its cgroup counts them, and the driver's, which must not be
the one running out).

    python3 ops/kubernetes/loadtest_report.py <out-dir>
"""
import datetime
import re
import sys
from pathlib import Path

STAGE = re.compile(r"STAGE (\d+): (\d+) matches, (\d+) players, rtt p50 (-?\d+)ms p90 (-?\d+)ms p99 (-?\d+)ms "
                   r"max (-?\d+)ms \((\d+) samples\), (\d+) players out of their match; hold (\d+)-(\d+)")
STATUS = re.compile(r"status: (\d+) match\(es\) playing, (\d+) of (\d+) slots, (\d+) players; ticks (.*?); "
                    r"CPU ([\d.]+) of (\d+) core\(s\); GC (\d+) collection\(s\), (\d+)ms; heap (\d+)MB of (\d+)MB; "
                    r"(\d+) threads")
TICKS = re.compile(r"p50 (\S+), p99 (\S+), p99\.9 (\S+), worst ([\d.]+)ms late")


def stamp(line):
    """The epoch ms of a `docker logs -t` line, and the rest of it."""
    ts, _, rest = line.partition(" ")
    ts = ts.rstrip("Z")
    if "." in ts:
        head, frac = ts.split(".")
        ts = head + "." + frac[:6]
        when = datetime.datetime.strptime(ts, "%Y-%m-%dT%H:%M:%S.%f")
    else:
        when = datetime.datetime.strptime(ts, "%Y-%m-%dT%H:%M:%S")
    return int(when.replace(tzinfo=datetime.timezone.utc).timestamp() * 1000), rest


def bound_ms(s):
    """'<=2ms' -> 2, '>100ms' -> 1000."""
    m = re.match(r"(<=|>)(\d+)ms", s)
    if not m:
        return 0
    return int(m.group(2)) if m.group(1) == "<=" else 1000


def mib(s):
    """'312.4MiB' / '1.2GiB' -> MiB."""
    m = re.match(r"([\d.]+)([KMG]i?B)", s.strip())
    if not m:
        return 0.0
    n, unit = float(m.group(1)), m.group(2)
    return n / 1024 if unit.startswith("K") else n * 1024 if unit.startswith("G") else n


def main(out):
    out = Path(out)
    stages = []
    for line in (out / "driver.log").read_text().splitlines():
        m = STAGE.search(line)
        if m:
            stages.append(m)
    statuses = []
    for line in (out / "match.log").read_text().splitlines():
        m = STATUS.search(line)
        if m:
            t, _ = stamp(line)
            statuses.append((t, m))
    stats = []
    for line in (out / "stats.csv").read_text().splitlines():
        parts = line.split(",")
        if len(parts) == 4:
            t, name, cpu, mem = parts
            try:
                stats.append((int(t) * 1000, name, float(cpu.rstrip("%")), mib(mem.split("/")[0])))
            except ValueError:
                pass

    print((out / "run.txt").read_text().strip() if (out / "run.txt").exists() else "")
    print(f"{'matches':>7} {'players':>7} {'rtt p50/p99/max':>16} {'out':>4} | {'cores':>5} {'/match':>6} "
          f"{'ticks p99':>9} {'p99.9':>6} {'worst':>7} {'GC ms/n':>9} {'heap':>6} {'thr':>4} | {'cg cpu%':>7} {'cg MiB':>7} | "
          f"{'driver%':>7}")
    for m in stages:
        a, b = int(m.group(10)), int(m.group(11))
        matches = int(m.group(2))
        # Status windows (10s each, ending at their line) wholly within the hold: one running past
        # its end would take in the next stage's matches starting
        inside = [s for t, s in statuses if a - 500 <= t - 10000 and t <= b + 500]
        cores = [float(s.group(6)) for s in inside]
        ticks = [TICKS.search(s.group(5)) for s in inside]
        ticks = [t for t in ticks if t]
        p99 = max((bound_ms(t.group(2)) for t in ticks), default=0)
        p999 = max((bound_ms(t.group(3)) for t in ticks), default=0)
        worst = max((float(t.group(4)) for t in ticks), default=0.0)
        gc_ms = sum(int(s.group(9)) for s in inside)
        gc_n = sum(int(s.group(8)) for s in inside)
        heap = max((int(s.group(10)) for s in inside), default=0)
        threads = max((int(s.group(12)) for s in inside), default=0)
        cg = [(c, mem) for t, n, c, mem in stats if n == "gg-load-match" and a <= t <= b]
        drv = [c for t, n, c, mem in stats if n == "gg-load-driver" and a <= t <= b]
        avg = lambda xs: sum(xs) / len(xs) if xs else 0.0
        core = avg(cores)
        print(f"{matches:>7} {int(m.group(3)):>7} {m.group(4) + '/' + m.group(6) + '/' + m.group(7):>16} "
              f"{int(m.group(9)):>4} | {core:>5.2f} {core / max(1, matches):>6.3f} {p99:>7}ms {p999:>4}ms "
              f"{worst:>5.0f}ms {str(gc_ms) + '/' + str(gc_n):>9} {heap:>4}MB {threads:>4} | {avg([c for c, _ in cg]):>7.0f} "
              f"{max((mem for _, mem in cg), default=0):>7.0f} | {avg(drv):>7.0f}")


if __name__ == "__main__":
    main(sys.argv[1])
