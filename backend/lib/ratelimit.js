'use strict';
/** In-memory fixed-window limiter (per client key). Fine for one instance; use a shared store if you scale out. */
class RateLimiter {
  constructor({ perMin, perDay, now = () => Date.now() }) { this.perMin = perMin; this.perDay = perDay; this.now = now; this.hits = new Map(); }
  check(key) {
    const t = this.now();
    let e = this.hits.get(key);
    if (!e) { e = { minStart: t, minCount: 0, dayStart: t, dayCount: 0 }; this.hits.set(key, e); }
    if (t - e.minStart >= 60_000) { e.minStart = t; e.minCount = 0; }
    if (t - e.dayStart >= 86_400_000) { e.dayStart = t; e.dayCount = 0; }
    if (e.minCount >= this.perMin) return { ok: false, retryAfterSec: Math.ceil((60_000 - (t - e.minStart)) / 1000) };
    if (e.dayCount >= this.perDay) return { ok: false, retryAfterSec: Math.ceil((86_400_000 - (t - e.dayStart)) / 1000) };
    e.minCount++; e.dayCount++;
    if (this.hits.size > 5000) for (const [k, v] of this.hits) if (t - v.dayStart > 86_400_000) this.hits.delete(k);
    return { ok: true };
  }
}
module.exports = { RateLimiter };
