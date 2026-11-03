'use strict';

function loadConfig(env = process.env) {
  const int = (v, d) => {
    const n = parseInt(v, 10);
    return Number.isFinite(n) && n > 0 ? n : d;
  };

  let provider = (env.PROVIDER || '').toLowerCase();
  if (!provider) {
    provider = env.GEMINI_API_KEY ? 'gemini'
      : env.ANTHROPIC_API_KEY ? 'anthropic'
      : env.OPENAI_API_KEY ? 'openai'
      : 'none';
  }

  const keys = {
    gemini: env.GEMINI_API_KEY || '',
    anthropic: env.ANTHROPIC_API_KEY || '',
    openai: env.OPENAI_API_KEY || ''
  };

  let model = '';
  if (provider === 'gemini') model = env.GEMINI_MODEL || 'gemini-2.5-flash';
  else if (provider === 'anthropic') model = env.ANTHROPIC_MODEL || 'claude-sonnet-5-5';
  else if (provider === 'openai') model = env.OPENAI_MODEL || '';

  return {
    appToken: env.APP_TOKEN || '',
    provider,
    apiKey: keys[provider] || '',
    model,
    port: int(env.PORT, 8787),
    ratePerMin: int(env.RATE_LIMIT_PER_MIN, 60),
    ratePerDay: int(env.RATE_LIMIT_PER_DAY, 1500),
    maxQuestions: int(env.MAX_QUESTIONS_PER_REQUEST, 10),
    timeoutMs: int(env.REQUEST_TIMEOUT_MS, 120000),
    maxBodyBytes: 32 * 1024
  };
}

function aiConfigured(cfg) {
  return Boolean(cfg.apiKey) &&
    (cfg.provider === 'gemini' ||
     cfg.provider === 'anthropic' ||
     (cfg.provider === 'openai' && Boolean(cfg.model)));
}

module.exports = { loadConfig, aiConfigured };
