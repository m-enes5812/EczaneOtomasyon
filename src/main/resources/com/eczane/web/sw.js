'use strict';
/*
 * Eczane Otomasyon — service worker.
 * Yalnızca sitenin dosyalarını saklar (uygulama hızlı ve çevrimdışıyken de açılsın diye).
 * /api istekleri ASLA saklanmaz: hasta ve reçete verileri cihazda tutulmaz.
 */
const CACHE = 'eczane-v1';
const SHELL = ['/', 'app.css', 'app.js', 'logo.png', 'favicon.png', 'icon-192.png', 'manifest.webmanifest'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

// Önce ağ, olmazsa saklanan kopya: sunucu güncellenince yeni sürüm hemen gelir.
self.addEventListener('fetch', (e) => {
  const req = e.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;
  e.respondWith(fetch(req).then((res) => {
    if (res.ok && res.type === 'basic') {
      const copy = res.clone();
      caches.open(CACHE).then((c) => c.put(req.mode === 'navigate' ? '/' : req, copy));
    }
    return res;
  }).catch(() => caches.match(req.mode === 'navigate' ? '/' : req)));
});
