'use strict';
/**
 * OPTIONAL Telegram relay. The bot token lives only in this server's environment (TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID).
 * The Android app works fully without it; local alarms never depend on Telegram.
 */
function makeTelegram(env, fetchImpl = globalThis.fetch) {
  const token = env.TELEGRAM_BOT_TOKEN, chat = env.TELEGRAM_CHAT_ID;
  if (!token || !chat) return null;
  return { async send(text) {
    const r = await fetchImpl(`https://api.telegram.org/bot${token}/sendMessage`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ chat_id: chat, text: String(text).slice(0, 1000) }) });
    if (!r.ok) throw Object.assign(new Error('telegram HTTP ' + r.status), { upstream: r.status });
  } };
}
module.exports = { makeTelegram };
