// Renders the social banners listed in tools/social/_build/jobs.json with Playwright (scale 1).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const jobs = JSON.parse(fs.readFileSync(path.join(__dirname, '_build/jobs.json'), 'utf8'));
  const b = await chromium.launch();
  for (const j of jobs) {
    const p = await b.newPage({ viewport: { width: j.width, height: j.height }, deviceScaleFactor: 1 });
    await p.goto('file://' + j.html, { waitUntil: 'load' });
    await p.evaluate(() => document.fonts.ready);
    await p.waitForFunction(() => document.body.dataset.fit === '1', null, { timeout: 5000 }).catch(() => {});
    fs.mkdirSync(path.dirname(j.out), { recursive: true });
    await p.screenshot({ path: j.out });
    await p.close();
  }
  await b.close();
  console.log('rendered', jobs.length);
})();
