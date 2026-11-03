'use strict';
const crypto = require('crypto');
/** Constant-time bearer-token check. NOTE: a shared app token can be extracted from an APK; it limits casual abuse only. */
function checkBearer(header, expected) {
  if (!expected) return false;
  const m = /^Bearer\s+(.+)$/i.exec(header || '');
  if (!m) return false;
  const a = crypto.createHash('sha256').update(m[1]).digest();
  const b = crypto.createHash('sha256').update(expected).digest();
  return crypto.timingSafeEqual(a, b);
}
module.exports = { checkBearer };
