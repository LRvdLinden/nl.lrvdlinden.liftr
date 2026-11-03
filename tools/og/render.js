const { chromium } = require('playwright');
const path = require('path');
// Run from tools/og after `python3 make_og_html.py`, with the site served on http://localhost:8765 (python3 -m http.server 8765 in website/).
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 1 });
  for (const l of ['nl','en']) {
    await p.goto('file://' + path.join(__dirname, `og-${l}.html`), { waitUntil: 'networkidle' });
    await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(400);
    await p.screenshot({ path: path.join(__dirname, '../../website/og', `home-${l}.png`) });
  }
  await b.close();
})();
