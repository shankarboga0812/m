'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createApp } = require('../lib/app');
const { checkQuestion, extractJson, validateGenerateRequest } = require('../lib/validate');
const { loadConfig } = require('../lib/config');

const TOKEN = 'test-token-123';
const good = { statement: 'Which is a Coffman condition?', type: 'MCQ', options: ['Mutual exclusion', 'Preemption', 'Spooling', 'Paging'], correct: [0], solution: 'ME is necessary', concept: 'Coffman', optionExplanations: ['a', 'b', 'c', 'd'] };
const fakeProvider = (reply) => ({ name: 'fake', model: 'fake-1', calls: 0, async complete() { this.calls++; if (reply instanceof Error) throw reply; return typeof reply === 'string' ? reply : JSON.stringify(reply); } });

async function withServer(opts, fn) {
  const server = createApp({ env: { APP_TOKEN: TOKEN, RATE_LIMIT_PER_MIN: '100', ...(opts.env || {}) }, provider: opts.provider, now: opts.now });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const call = (path, { method = 'GET', body, token = TOKEN, raw } = {}) => fetch(base + path, { method, headers: { ...(token ? { authorization: `Bearer ${token}` } : {}), 'content-type': 'application/json' }, body: raw ?? (body ? JSON.stringify(body) : undefined) });
  try { await fn(call); } finally { await new Promise((r) => server.close(r)); }
}
const req = { subject: 'Operating Systems', topic: 'Deadlocks', count: 2, difficulty: 'EASY', questionType: 'MCQ' };

test('refuses to start without APP_TOKEN', () => { assert.throws(() => createApp({ env: {} }), /APP_TOKEN/); });

test('/health is open; every other route needs the token', async () => {
  await withServer({ provider: fakeProvider({ questions: [good] }) }, async (call) => {
    assert.equal((await call('/health', { token: null })).status, 200);
    assert.equal((await call('/v1/status', { token: null })).status, 401);
    assert.equal((await call('/v1/status', { token: 'wrong' })).status, 401);
    assert.equal((await call('/v1/generate-questions', { method: 'POST', body: req, token: 'wrong' })).status, 401);
    const st = await (await call('/v1/status')).json();
    assert.equal(st.aiConfigured, true); assert.equal(st.provider, 'fake');
  });
});

test('status reports honestly when no AI key is configured', async () => {
  await withServer({}, async (call) => {
    assert.equal((await (await call('/v1/status')).json()).aiConfigured, false);
    assert.equal((await call('/v1/generate-questions', { method: 'POST', body: req })).status, 503);
  });
});

test('generate returns validated questions and drops invalid ones', async () => {
  const bad = { ...good, correct: [9] };
  const p = fakeProvider('```json\n' + JSON.stringify({ questions: [good, bad] }) + '\n```');
  await withServer({ provider: p }, async (call) => {
    const r = await call('/v1/generate-questions', { method: 'POST', body: req });
    assert.equal(r.status, 200); const j = await r.json();
    assert.equal(j.questions.length, 1); assert.equal(j.dropped, 1); assert.equal(j.provider, 'fake');
    assert.ok(!JSON.stringify(j).includes(TOKEN));
  });
});

test('request validation', async () => {
  await withServer({ provider: fakeProvider({ questions: [good] }) }, async (call) => {
    for (const body of [{}, { ...req, count: 0 }, { ...req, count: 99 }, { ...req, difficulty: 'IMPOSSIBLE' }, { ...req, questionType: 'ESSAY' }, { ...req, marks: 5 }, { ...req, topic: '' }])
      assert.equal((await call('/v1/generate-questions', { method: 'POST', body })).status, 400, JSON.stringify(body));
    assert.equal((await call('/v1/generate-questions', { method: 'POST', raw: '{not json' })).status, 400);
  });
});

test('oversized bodies are rejected', async () => {
  await withServer({ provider: fakeProvider({ questions: [good] }) }, async (call) => {
    const big = JSON.stringify({ ...req, learningObjective: 'x'.repeat(40000) });
    let status; try { status = (await call('/v1/generate-questions', { method: 'POST', raw: big })).status; } catch { status = 413; }
    assert.equal(status, 413);
  });
});

test('provider failure is a 502 and never leaks secrets', async () => {
  const secret = 'sk-SECRET-KEY';
  await withServer({ provider: fakeProvider(Object.assign(new Error('boom ' + secret), { upstream: 500 })) }, async (call) => {
    const r = await call('/v1/generate-questions', { method: 'POST', body: req });
    assert.equal(r.status, 502); assert.ok(!(await r.text()).includes(secret));
  });
});

test('unusable AI output and all-invalid output are 502', async () => {
  await withServer({ provider: fakeProvider('I cannot help with that') }, async (call) => assert.equal((await call('/v1/generate-questions', { method: 'POST', body: req })).status, 502));
  await withServer({ provider: fakeProvider({ questions: [{ ...good, options: ['a', 'a', 'b', 'c'] }] }) }, async (call) => assert.equal((await call('/v1/generate-questions', { method: 'POST', body: req })).status, 502));
});

test('rate limit returns 429 with retry-after', async () => {
  let t = 0;
  await withServer({ provider: fakeProvider({ questions: [good] }), now: () => t, env: { RATE_LIMIT_PER_MIN: '3' } }, async (call) => {
    for (let i = 0; i < 3; i++) assert.equal((await call('/v1/status')).status, 200);
    const r = await call('/v1/status'); assert.equal(r.status, 429); assert.ok(Number((await r.json()).retryAfterSec) > 0);
    t += 61_000; assert.equal((await call('/v1/status')).status, 200);
  });
});

test('explain and lesson endpoints proxy structured JSON', async () => {
  await withServer({ provider: fakeProvider({ explanation: { whyWrong: 'x', misconception: 'y', steps: 'z', shortcut: 's', practiceNext: 'p' }, lesson: { definition: 'd' } }) }, async (call) => {
    assert.equal((await (await call('/v1/explain', { method: 'POST', body: { concept: 'c' } })).json()).explanation.whyWrong, 'x');
    assert.equal((await (await call('/v1/lesson', { method: 'POST', body: { topic: 't' } })).json()).lesson.definition, 'd');
  });
});

test('unit: question checker, json extraction, config', () => {
  assert.deepEqual(checkQuestion(good), []);
  assert.ok(checkQuestion({ ...good, options: good.options.slice(0, 3) }).length);
  assert.ok(checkQuestion({ type: 'NAT', statement: 's', solution: 's', concept: 'c' }).length);
  assert.deepEqual(checkQuestion({ type: 'NAT', statement: 's', solution: 's', concept: 'c', answer: { low: 1, high: 2 } }), []);
  assert.deepEqual(extractJson('noise {"a":{"b":"}"}} tail'), { a: { b: '}' } }); assert.equal(extractJson('none'), null);
  assert.equal(validateGenerateRequest({ ...req, marks: 2 }, 10).value.marks, 2);
  const c = loadConfig({ APP_TOKEN: 't', ANTHROPIC_API_KEY: 'k' }); assert.equal(c.provider, 'anthropic'); assert.equal(c.model, 'claude-sonnet-5-5');
  assert.equal(loadConfig({ OPENAI_API_KEY: 'k' }).provider, 'openai');
});

test('telegram relay is optional, authenticated and never leaks the bot token', async () => {
  const sent = [];
  const server = createApp({ env: { APP_TOKEN: TOKEN }, provider: null, telegram: { async send(t) { sent.push(t); } } });
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = (token, body) => fetch(base + '/v1/telegram/notify', { method: 'POST', headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, body: JSON.stringify(body) });
  assert.equal((await post('bad', { text: 'hi' })).status, 401);
  assert.equal((await post(TOKEN, { text: '  ' })).status, 400);
  assert.equal((await post(TOKEN, { text: 'Day 1 summary' })).status, 200); assert.deepEqual(sent, ['Day 1 summary']);
  await new Promise((r) => server.close(r));
  const off = createApp({ env: { APP_TOKEN: TOKEN }, provider: null, telegram: null });
  await new Promise((r) => off.listen(0, '127.0.0.1', r));
  assert.equal((await fetch(`http://127.0.0.1:${off.address().port}/v1/telegram/notify`, { method: 'POST', headers: { authorization: `Bearer ${TOKEN}` }, body: '{"text":"x"}' })).status, 503);
  await new Promise((r) => off.close(r));
});
