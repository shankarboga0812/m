'use strict';
const { createApp } = require('./lib/app');
const { loadConfig, aiConfigured } = require('./lib/config');
const cfg = loadConfig();
const server = createApp({ log: (...a) => console.error('[backend]', ...a) });   // logs never include headers, bodies or keys
server.listen(cfg.port, () => console.log(`GATE AI backend listening on :${cfg.port} (AI ${aiConfigured(cfg) ? 'configured: ' + cfg.provider : 'NOT configured'})`));
