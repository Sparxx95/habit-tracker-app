const fs = require('fs');
const path = require('path');

const REMOTE_INDEX_URL = 'https://raw.githubusercontent.com/Sparxx95/habit-tracker/main/index.html';
const WWW_DIR = path.join(__dirname, '..', 'www');
const WWW_INDEX = path.join(WWW_DIR, 'index.html');

async function syncWww() {
  const res = await fetch(REMOTE_INDEX_URL);
  if (!res.ok) {
    throw new Error(`index.html konnte nicht geladen werden: HTTP ${res.status} von ${REMOTE_INDEX_URL}`);
  }
  const html = await res.text();
  fs.mkdirSync(WWW_DIR, { recursive: true });
  fs.writeFileSync(WWW_INDEX, html);
  return WWW_INDEX;
}

module.exports = { syncWww, REMOTE_INDEX_URL, WWW_INDEX };

if (require.main === module) {
  syncWww()
    .then((target) => console.log('Synchronisiert: ' + target))
    .catch((err) => {
      console.error(err.message);
      process.exit(1);
    });
}
