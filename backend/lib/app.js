'use strict';
const http = require('http');
const { loadConfig, aiConfigured } = require('./config');
const { checkBearer } = require('./auth');
const { RateLimiter } = require('./ratelimit');
const { validateGenerateRequest, checkQuestion, extractJson } = require('./validate');
const { SYSTEM, generatePrompt, explainPrompt, lessonPrompt } = require('./prompts');
const { makeProvider } = require('./providers');
const { makeTelegram } = require('./telegram');

function send(res, code, obj, extra = {}) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff', ...extra });
  res.end(body);
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => { size += c.length; if (size > limit) { reject(Object.assign(new Error('too large'), { code: 413 })); req.destroy(); } else chunks.push(c); });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

/** createApp({env, provider, now}) -> http.Server. `provider` can be injected for tests. */
function createApp({ env = process.env, provider, now, telegram, log = () => {} } = {}) {
  const cfg = loadConfig(env);
  if (!cfg.appToken) throw new Error('APP_TOKEN is required: refusing to start an unauthenticated server');
  const ai = provider !== undefined ? provider : (aiConfigured(cfg) ? makeProvider(cfg) : null);
  const tg = telegram !== undefined ? telegram : makeTelegram(env);
  const limiter = new RateLimiter({ perMin: cfg.ratePerMin, perDay: cfg.ratePerDay, now });

  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, 'http://localhost');
      if (req.method === 'GET' && url.pathname === '/health') return send(res, 200, { ok: true });
      if (!checkBearer(req.headers.authorization, cfg.appToken)) return send(res, 401, { error: 'unauthorized' });
      const clientKey = (req.headers['x-forwarded-for'] || req.socket.remoteAddress || 'unknown').toString().split(',')[0].trim();
      const rl = limiter.check(clientKey);
      if (!rl.ok) return send(res, 429, { error: 'rate limited', retryAfterSec: rl.retryAfterSec }, { 'retry-after': String(rl.retryAfterSec) });

      if (req.method === 'GET' && url.pathname === '/v1/status')
        return send(res, 200, { ok: true, aiConfigured: Boolean(ai), provider: ai ? ai.name : null, model: ai ? ai.model : null, telegramConfigured: Boolean(tg), version: '1.0.0' });

      if (req.method !== 'POST') return send(res, 404, { error: 'not found' });
      let body;
      try { body = JSON.parse(await readBody(req, cfg.maxBodyBytes) || 'null'); }
      catch (e) { return send(res, e.code === 413 ? 413 : 400, { error: e.code === 413 ? 'request too large' : 'invalid JSON' }); }
      if (url.pathname === '/v1/telegram/notify') {
        if (!tg) return send(res, 503, { error: 'Telegram is not configured on the server (optional feature)' });
        if (!body || typeof body.text !== 'string' || !body.text.trim()) return send(res, 400, { error: 'text is required' });
        try { await tg.send(body.text); return send(res, 200, { ok: true }); } catch (e) { log('telegram failure', e.upstream || e.name); return send(res, 502, { error: 'Telegram request failed' }); }
      }
      if (!ai) return send(res, 503, { error: 'AI provider is not configured on the server' });

      if (url.pathname === '/v1/generate-questions') {
        const v = validateGenerateRequest(body, cfg.maxQuestions);
        if (!v.ok) return send(res, 400, { error: v.error });
        let text;
        try { text = await ai.complete(SYSTEM, generatePrompt(v.value), v.value.mode ? 9000 : 6000); } catch (e) { log('provider failure', e.upstream || e.name); return send(res, 502, { error: 'AI provider request failed' }); }
        const parsed = extractJson(text);
        if (!parsed || !Array.isArray(parsed.questions)) return send(res, 502, { error: 'AI returned an unusable response' });
        const good = []; let dropped = 0;
        for (const q of parsed.questions) { if (checkQuestion(q).length === 0) good.push(q); else dropped++; }
        if (good.length === 0) return send(res, 502, { error: 'AI returned no valid questions', dropped });
        return send(res, 200, { provider: ai.name, model: ai.model, dropped, questions: good.slice(0, v.value.count) });
      }
      if (url.pathname === '/v1/explain' || url.pathname === '/v1/lesson') {
        if (!body || typeof body !== 'object') return send(res, 400, { error: 'Body must be a JSON object' });
        const small = JSON.parse(JSON.stringify(body, (k, v) => (typeof v === 'string' ? v.slice(0, 2000) : v)));
        let text;
        try { text = await ai.complete(SYSTEM, url.pathname === '/v1/explain' ? explainPrompt(small) : lessonPrompt(small), 4000); }
        catch (e) { log('provider failure', e.upstream || e.name); return send(res, 502, { error: 'AI provider request failed' }); }
        const parsed = extractJson(text);
        const key = url.pathname === '/v1/explain' ? 'explanation' : 'lesson';
        if (!parsed || typeof parsed[key] !== 'object') return send(res, 502, { error: 'AI returned an unusable response' });
        return send(res, 200, { provider: ai.name, model: ai.model, [key]: parsed[key] });
      }
      return send(res, 404, { error: 'not found' });
    } catch (e) { log('unhandled', e.name); return send(res, 500, { error: 'internal error' }); }
  });
  return server;
}
module.exports = { createApp };
