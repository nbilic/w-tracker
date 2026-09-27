// Copies the web app into www/ (Capacitor's webDir). www/ is a build output and gitignored.
// sw.js and manifest.json are left out: the native app bundles its files and needs neither.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const out = path.join(root, 'www');

fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out);

for (const f of ['index.html', 'icon-192.png', 'icon-512.png']) {
  fs.copyFileSync(path.join(root, f), path.join(out, f));
}
fs.cpSync(path.join(root, 'fonts'), path.join(out, 'fonts'), { recursive: true });

// The manifest link would 404 inside the APK; drop it.
// Also drop viewport-fit=cover so Capacitor pads the WebView below the status bar
// (SystemBars insetsHandling "native") instead of drawing the app underneath it.
const htmlPath = path.join(out, 'index.html');
const html = fs.readFileSync(htmlPath, 'utf8')
  .replace(/^\s*<link rel="manifest"[^>]*>\r?\n/m, '')
  .replace(', viewport-fit=cover', '');
fs.writeFileSync(htmlPath, html);

console.log('Built www/');
