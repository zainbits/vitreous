import sys
from perfetto.trace_processor import TraceProcessor
trace, uid, label = sys.argv[1], int(sys.argv[2]), sys.argv[3]
tp = TraceProcessor(trace=trace)
q = f"""
select s.ts, s.dur, cast(substr(s.name, 2) as real) pct
from slice s join track t on s.track_id = t.id
where t.type = 'android_gpu_work_period'
  and (select int_value from args where arg_set_id = t.dimension_arg_set_id and key = 'uid') = {uid}
"""
rows = list(tp.query(q))
tot = sum(r.dur for r in rows)
busy = sum(r.dur * r.pct / 100 for r in rows)
# time-weighted GPU frequency
fq = list(tp.query("select c.ts, c.value from counter c join counter_track t on c.track_id=t.id where t.name='gpufreq' order by c.ts"))
end = list(tp.query("select max(ts + dur) e from slice"))[0].e
fw = 0; ft = 0
for i, r in enumerate(fq):
    nxt = fq[i + 1].ts if i + 1 < len(fq) else end
    if nxt and nxt > r.ts:
        fw += r.value * (nxt - r.ts); ft += nxt - r.ts
f = fw / ft / 1e3 if ft else 0
util = 100 * busy / tot if tot else 0
print(f"{label:30s} gpu_busy={util:5.1f}%  freq={f:4.0f}MHz  work={util * f / 100:6.1f} (busy%*MHz/100)")
