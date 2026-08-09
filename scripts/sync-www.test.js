const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const { syncWww, REMOTE_INDEX_URL, WWW_INDEX } = require('./sync-www');

test('syncWww lädt die aktuelle index.html von GitHub und schreibt sie unverändert nach www/index.html', async () => {
  const target = await syncWww();
  assert.strictEqual(target, WWW_INDEX);
  const res = await fetch(REMOTE_INDEX_URL);
  const remoteContent = await res.text();
  const wwwContent = fs.readFileSync(WWW_INDEX, 'utf8');
  assert.strictEqual(wwwContent, remoteContent);
});
