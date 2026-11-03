'use strict';

function withTimeout(ms) {
  const c = new AbortController();
  const t = setTimeout(() => c.abort(), ms);
  return { signal: c.signal, done: () => clearTimeout(t) };
}

function makeProvider(cfg, fetchImpl = globalThis.fetch) {
  if (cfg.provider === 'gemini') {
    return {
      name: 'gemini',
      model: cfg.model,
      async complete(system, user, maxTokens = 6000) {
        const to = withTimeout(cfg.timeoutMs);
        try {
          const endpoint =
            'https://generativelanguage.googleapis.com/v1beta/models/' +
            encodeURIComponent(cfg.model) + ':generateContent';

          const r = await fetchImpl(endpoint, {
            method: 'POST',
            signal: to.signal,
            headers: {
              'content-type': 'application/json',
              'x-goog-api-key': cfg.apiKey
            },
            body: JSON.stringify({
              systemInstruction: { parts: [{ text: system }] },
              contents: [{
                role: 'user',
                parts: [{ text: user }]
              }],
              generationConfig: { maxOutputTokens: maxTokens }
            })
          });

          if (!r.ok) {
            throw Object.assign(new Error(`provider HTTP ${r.status}`), {
              upstream: r.status
            });
          }

          const j = await r.json();
          return (j.candidates?.[0]?.content?.parts || [])
            .map(p => p.text || '')
            .join('');
        } finally {
          to.done();
        }
      }
    };
  }

  if (cfg.provider === 'anthropic') {
    return {
      name: 'anthropic',
      model: cfg.model,
      async complete(system, user, maxTokens = 6000) {
        const to = withTimeout(cfg.timeoutMs);
        try {
          const r = await fetchImpl('https://api.anthropic.com/v1/messages', {
            method: 'POST',
            signal: to.signal,
            headers: {
              'content-type': 'application/json',
              'x-api-key': cfg.apiKey,
              'anthropic-version': '2023-06-01'
            },
            body: JSON.stringify({
              model: cfg.model,
              max_tokens: maxTokens,
              system,
              messages: [{ role: 'user', content: user }]
            })
          });
          if (!r.ok) {
            throw Object.assign(new Error(`provider HTTP ${r.status}`), {
              upstream: r.status
            });
          }
          const j = await r.json();
          return (j.content || [])
            .filter(b => b.type === 'text')
            .map(b => b.text)
            .join('');
        } finally {
          to.done();
        }
      }
    };
  }

  if (cfg.provider === 'openai') {
    return {
      name: 'openai',
      model: cfg.model,
      async complete(system, user, maxTokens = 6000) {
        const to = withTimeout(cfg.timeoutMs);
        try {
          const r = await fetchImpl('https://api.openai.com/v1/chat/completions', {
            method: 'POST',
            signal: to.signal,
            headers: {
              'content-type': 'application/json',
              authorization: `Bearer ${cfg.apiKey}`
            },
            body: JSON.stringify({
              model: cfg.model,
              max_tokens: maxTokens,
              messages: [
                { role: 'system', content: system },
                { role: 'user', content: user }
              ]
            })
          });
          if (!r.ok) {
            throw Object.assign(new Error(`provider HTTP ${r.status}`), {
              upstream: r.status
            });
          }
          const j = await r.json();
          return j.choices?.[0]?.message?.content || '';
        } finally {
          to.done();
        }
      }
    };
  }

  return null;
}

module.exports = { makeProvider };
