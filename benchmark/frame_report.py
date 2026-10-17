#!/usr/bin/env python3
"""
Second view of Macrobenchmark traces, for devices whose SurfaceFlinger keeps the buffer queue full
(Galaxy A23: every frame is "Buffer Stuffing", so frameOverrunMs and frameDurationCpuMs include the time
RenderThread is blocked in queueBuffer, not app work). Runs trace_processor on the phone.

  python3 benchmark/frame_report.py [trace dir] [name filter]      (FR_ITER=1: one line per iteration too)

Per test, pooled over iterations:
  frames, % App Deadline Missed (real app jank), % Buffer Stuffing,
  cpu   = UI doFrame + RenderThread DrawFrames - (queueBuffer + dequeueBuffer + UI postAndWait)   (work only)
  macro = UI doFrame + RenderThread DrawFrames                                    (close to frameDurationCpuMs)
"""
import glob
import os
import re
import subprocess
import sys
import tempfile
import zipfile

PKG = "com.example.storyrig"
DEV_TP = "/data/local/tmp/tp"
DEFAULT_DIR = "benchmark/build/outputs/connected_android_test_additional_output/benchmark/connected"

SQL = """
with app as (select upid from process where name = '%(pkg)s'),
ui as (
  select s.id, cast(substr(s.name, 23) as int) vs, s.dur from slice s
  join thread_track tt on s.track_id = tt.id join thread t using(utid) join process p using(upid)
  where p.name = '%(pkg)s' and t.tid = p.pid and s.name like 'Choreographer#doFrame %%'
),
rt as (
  select s.id, cast(substr(s.name, 12) as int) vs, s.dur from slice s
  join thread_track tt on s.track_id = tt.id join thread t using(utid) join app using(upid)
  where t.name = 'RenderThread' and s.name like 'DrawFrames %%'
),
wait as (
  -- Both are nested in themselves (queueBuffer > queueBuffer, dequeueBuffer - ViewRootImpl > dequeueBuffer):
  -- count the outer one only.
  select rt.vs, sum(d.dur) w from rt join descendant_slice(rt.id) d join slice p on p.id = d.parent_id
  where (d.name = 'queueBuffer' and p.name != 'queueBuffer')
     or (d.name like 'dequeueBuffer%%' and p.name not like 'dequeueBuffer%%')
  group by rt.vs
),
-- UI thread blocked on RenderThread (which is itself blocked on the full queue).
uiwait as (
  select ui.vs, sum(d.dur) w from ui join descendant_slice(ui.id) d where d.name = 'postAndWait' group by ui.vs
),
jank as (
  select cast(a.name as int) vs, a.jank_type from actual_frame_timeline_slice a join app using(upid)
)
select ui.vs, ui.dur, coalesce(rt.dur, 0), coalesce(wait.w, 0) + coalesce(uiwait.w, 0), coalesce(uiwait.w, 0),
  coalesce(jank.jank_type, '')
from ui left join rt using(vs) left join wait using(vs) left join uiwait using(vs) left join jank using(vs);
"""


def adb(*args, **kw):
    return subprocess.run(["adb", *args], check=True, capture_output=True, text=True, **kw).stdout


def ensure_tp():
    if adb("shell", f"ls {DEV_TP} 2>/dev/null || true").strip() == DEV_TP:
        return
    found = glob.glob(os.path.expanduser(
        "~/.gradle/caches/*/transforms/*/transformed/benchmark-common-*/assets/trace_processor_shell_aarch64"))
    if not found:
        sys.exit("trace_processor_shell_aarch64 not found in the Gradle cache")
    adb("push", found[0], DEV_TP)
    adb("shell", "chmod", "755", DEV_TP)


def trace_pb(path, tmp):
    """Macrobenchmark traces may be zips holding Trace_output.pb."""
    if not zipfile.is_zipfile(path):
        return path
    with zipfile.ZipFile(path) as z:
        name = max(z.namelist(), key=lambda n: z.getinfo(n).file_size)
        out = os.path.join(tmp, "t.pb")
        with open(out, "wb") as f:
            f.write(z.read(name))
        return out


def query(pb, tmp):
    sql = os.path.join(tmp, "q.sql")
    with open(sql, "w") as f:
        f.write(SQL % {"pkg": PKG})
    adb("push", pb, "/data/local/tmp/fr.pb")
    adb("push", sql, "/data/local/tmp/fr.sql")
    out = adb("shell", f"{DEV_TP} -q /data/local/tmp/fr.sql /data/local/tmp/fr.pb 2>/dev/null")
    rows = []
    for line in out.splitlines():
        m = re.match(r'^(\d+),(\d+),(\d+),(\d+),(\d+),"(.*)"$', line.strip())
        if m:
            rows.append((int(m[2]), int(m[3]), int(m[4]), int(m[5]), m[6]))
    return rows


def pct(xs, p):
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(len(xs) * p / 100))] if xs else float("nan")


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_DIR
    filt = sys.argv[2] if len(sys.argv) > 2 else ""
    traces = sorted(glob.glob(os.path.join(root, "**", "*.perfetto-trace"), recursive=True))
    tests = {}
    for t in traces:
        m = re.match(r"(.+)_iter\d+_", os.path.basename(t))
        if m and filt in m[1]:
            tests.setdefault(m[1], []).append(t)
    if not tests:
        sys.exit(f"no traces under {root}")
    ensure_tp()
    print(f"{'test':28} {'frames':>6} {'appMiss%':>8} {'stuff%':>7} | "
          f"{'cpu P50':>7} {'P90':>5} {'P95':>5} {'P99':>5} | {'ui P50':>6} {'P90':>5} | {'rt P50':>6} {'P90':>5} | "
          f"{'macro P50':>9} {'P90':>5}")
    for name, files in sorted(tests.items()):
        rows = []
        with tempfile.TemporaryDirectory() as tmp:
            for i, f in enumerate(files):
                it = query(trace_pb(f, tmp), tmp)
                if os.environ.get("FR_ITER") == "1":
                    report(f"  iter{i}", it)
                rows += it
        report(name, rows)


def report(name, rows):
        if not rows:
            print(f"{name:28} no frames")
            return
        n = len(rows)
        miss = sum(1 for r in rows if "App Deadline Missed" in r[4])
        stuff = sum(1 for r in rows if "Buffer Stuffing" in r[4])
        cpu = [(ui + rt - w) / 1e6 for ui, rt, w, _, _ in rows]
        uiw = [(ui - uw) / 1e6 for ui, rt, w, uw, _ in rows]
        rtw = [(rt - (w - uw)) / 1e6 for ui, rt, w, uw, _ in rows]
        macro = [(ui + rt) / 1e6 for ui, rt, w, _, _ in rows]
        print(f"{name:28} {n:6} {100 * miss / n:8.2f} {100 * stuff / n:7.1f} | "
              f"{pct(cpu, 50):7.1f} {pct(cpu, 90):5.1f} {pct(cpu, 95):5.1f} {pct(cpu, 99):5.1f} | "
              f"{pct(uiw, 50):6.1f} {pct(uiw, 90):5.1f} | {pct(rtw, 50):6.1f} {pct(rtw, 90):5.1f} | "
              f"{pct(macro, 50):9.1f} {pct(macro, 90):5.1f}")


if __name__ == "__main__":
    main()
