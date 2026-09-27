const CACHE_NAME = 'workout-v76-local-fonts';
const ASSETS = [
  './',
  './index.html',
  './manifest.json',
  './icon-192.png',
  './icon-512.png',
  './fonts/fonts.css',
  './fonts/Fraunces-italic-latin-ext.woff2',
  './fonts/Fraunces-italic-latin.woff2',
  './fonts/Fraunces-normal-latin-ext.woff2',
  './fonts/Fraunces-normal-latin.woff2',
  './fonts/Geist-normal-latin-ext.woff2',
  './fonts/Geist-normal-latin.woff2',
  './fonts/GeistMono-normal-latin-ext.woff2',
  './fonts/GeistMono-normal-latin.woff2'
];

// Install: cache all assets
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(ASSETS))
  );
  self.skipWaiting();
});

// Activate: clean old caches
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// Fetch: cache-first strategy
self.addEventListener('fetch', event => {
  event.respondWith(
    caches.match(event.request).then(cached => {
      if (cached) return cached;
      return fetch(event.request).then(response => {
        // Cache new requests dynamically
        if (response.status === 200) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        }
        return response;
      });
    }).catch(() => {
      // Fallback for navigation requests
      if (event.request.mode === 'navigate') {
        return caches.match('./index.html');
      }
    })
  );
});

// Tapping the "Rest over" notification brings the app back to the front.
self.addEventListener('notificationclick', event => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(list => {
      const open = list.find(c => 'focus' in c);
      return open ? open.focus() : self.clients.openWindow('./');
    })
  );
});
