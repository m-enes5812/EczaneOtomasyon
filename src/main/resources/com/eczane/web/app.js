'use strict';
/* Eczane Otomasyon — web istemcisi. Sunucudaki /api uçlarıyla konuşan tek sayfalık uygulama. */

const state = { user: null, meta: null, unread: 0, timers: [], ilaclar: null, installEvt: null };
const app = document.getElementById('app');

// ================================================================ yardımcılar

const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

const ICONS = {
  home: '<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"/>',
  file: '<path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><path d="M14 3v6h6M8 13h8M8 17h5"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  box: '<path d="M21 8 12 3 3 8v8l9 5 9-5z"/><path d="m3 8 9 5 9-5M12 13v8"/>',
  history: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5M12 7v5l3 3"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-7 8-7s8 3 8 7"/>',
  logout: '<path d="M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4M10 17l5-5-5-5M15 12H3"/>',
  moon: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>',
  msg: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
  alert: '<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/><path d="M12 9v4M12 17h.01"/>',
  send: '<path d="M22 2 11 13M22 2l-7 20-4-9-9-4z"/>',
  menu: '<path d="M3 6h18M3 12h18M3 18h18"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
  lock: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
  doktor: '<path d="M6 3v6a6 6 0 0 0 12 0V3"/><path d="M12 15v2a4 4 0 0 0 8 0v-3"/><circle cx="20" cy="12" r="2"/>',
  eczaci: '<path d="M10.5 20.5a5 5 0 0 1-7-7l10-10a5 5 0 0 1 7 7z"/><path d="m8.5 8.5 7 7"/>',
  hasta: '<path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  trash: '<path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"/>',
  db: '<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/>',
  download: '<path d="M12 3v12M7 10l5 5 5-5M4 19h16"/>',
  phone: '<rect x="6" y="2" width="12" height="20" rx="2"/><path d="M11 18h2"/>',
  monitor: '<rect x="2" y="4" width="20" height="13" rx="2"/><path d="M8 21h8M12 17v4"/>',
  wifi: '<path d="M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5h.01M2 9a15 15 0 0 1 20 0"/>',
};
const icon = (name) => `<svg class="i" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name] || ''}</svg>`;

const DURUM = { AKTIF: 'Bekliyor', VERILDI: 'Verildi', IPTAL: 'İptal' };
const durumBadge = (d) => `<span class="badge ${esc(d)}">${esc(DURUM[d] || d)}</span>`;
const KULLANIM = ['Günde 1 kez 1 adet', 'Günde 2 kez 1 adet', 'Günde 3 kez 1 adet', 'Sabah aç karnına 1 adet',
  'Akşam tok karnına 1 adet', 'Gerektiğinde 1 adet', 'Haftada 1 kez', 'Günde 2 kez sürülecek'];

function fmtDate(iso, withTime = true) {
  if (!iso) return '—';
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2}))?/.exec(iso);
  if (!m) return esc(iso);
  const d = `${m[3]}.${m[2]}.${m[1]}`;
  return withTime && m[4] ? `${d} ${m[4]}:${m[5]}` : d;
}
function relDate(iso) {
  if (!iso) return '';
  const t = new Date(iso);
  const now = new Date();
  const sameDay = t.toDateString() === now.toDateString();
  if (sameDay) return `${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}`;
  const y = new Date(now); y.setDate(now.getDate() - 1);
  if (t.toDateString() === y.toDateString()) return 'Dün';
  return fmtDate(iso, false);
}
const maskTc = (tc) => (tc && tc.length >= 5 ? `${tc.slice(0, 3)}******${tc.slice(-2)}` : tc || '');

class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

async function api(method, path, body) {
  const opts = { method, headers: { Accept: 'application/json' }, credentials: 'same-origin' };
  if (method !== 'GET') {
    opts.headers['Content-Type'] = 'application/json';
    opts.headers['X-Eczane'] = '1';
    opts.body = JSON.stringify(body || {});
  }
  let res;
  try {
    res = await fetch(path, opts);
  } catch {
    throw new ApiError(0, 'Sunucuya ulaşılamıyor. Bağlantını kontrol et.');
  }
  let data = null;
  try { data = await res.json(); } catch { /* boş yanıt */ }
  if (!res.ok) {
    const msg = (data && data.error) || `İstek başarısız (${res.status}).`;
    if (res.status === 401 && state.user) {
      state.user = null;
      toast(msg, 'error');
      go('#/');
    }
    throw new ApiError(res.status, msg);
  }
  return data;
}

function toast(text, kind = '') {
  const el = document.createElement('div');
  el.className = `toast ${kind}`;
  el.textContent = text;
  $('#toasts').append(el);
  setTimeout(() => el.remove(), kind === 'error' ? 6000 : 3500);
}

/** Onay penceresi. input verilirse girilen metni döndürür; vazgeçilirse null. */
function ask({ title, text, ok = 'Tamam', danger = false, input = null }) {
  return new Promise((resolve) => {
    const d = document.createElement('dialog');
    d.innerHTML = `<form method="dialog" class="form">
      <div><h3>${esc(title)}</h3>${text ? `<p>${esc(text)}</p>` : ''}</div>
      ${input ? `<label class="field">${esc(input)}<input name="v" maxlength="200" autocomplete="off"></label>` : ''}
      <div class="row end"><button value="no" class="btn">Vazgeç</button>
      <button value="yes" class="btn ${danger ? 'danger' : 'primary'}">${esc(ok)}</button></div></form>`;
    document.body.append(d);
    d.addEventListener('close', () => {
      const yes = d.returnValue === 'yes';
      const v = input ? $('input', d).value : true;
      d.remove();
      resolve(yes ? v : null);
    });
    d.showModal();
  });
}

/** Form gönderiminde düğmeyi kilitler, hata olursa formun üstünde gösterir. */
function bindForm(form, handler) {
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = $('button[type=submit]', form);
    const err = $('.form-error', form);
    if (err) err.classList.add('hidden');
    if (btn) { btn.disabled = true; btn.dataset.label = btn.innerHTML; btn.innerHTML = '<span class="spinner sm"></span>'; }
    try {
      await handler(Object.fromEntries(new FormData(form)), form);
    } catch (ex) {
      if (err) { err.textContent = ex.message; err.classList.remove('hidden'); } else toast(ex.message, 'error');
    } finally {
      if (btn && btn.isConnected) { btn.disabled = false; btn.innerHTML = btn.dataset.label; }
    }
  });
}

function strength(pw) {
  let s = 0;
  if (pw.length >= 8) s++;
  if (pw.length >= 12) s++;
  if (/[a-zçğıöşü]/i.test(pw) && /\d/.test(pw)) s++;
  if (/[a-zçğıöşü]/.test(pw) && /[A-ZÇĞİÖŞÜ]/.test(pw)) s++;
  if (/[^\p{L}\d]/u.test(pw)) s++;
  return Math.min(4, s);
}
function bindStrength(input, bar) {
  const colors = ['var(--danger)', 'var(--danger)', 'var(--warn)', 'var(--ok)', 'var(--ok)'];
  input.addEventListener('input', () => {
    const s = input.value ? strength(input.value) : 0;
    bar.style.width = `${input.value ? (s + 1) * 20 : 0}%`;
    bar.style.background = colors[s];
  });
}
const pwField = (name, label, hint = '') => `<label class="field">${esc(label)}
  <input type="password" name="${name}" required autocomplete="new-password" maxlength="128">
  <div class="strength"><i></i></div>${hint ? `<span class="hint">${esc(hint)}</span>` : ''}</label>`;
const tcField = (name = 'tc', label = 'T.C. Kimlik No') => `<label class="field">${esc(label)}
  <input name="${name}" required inputmode="numeric" pattern="\\d{11}" maxlength="11" autocomplete="username" placeholder="11 haneli"></label>`;
const empty = (ico, text) => `<div class="empty">${icon(ico)}<p>${esc(text)}</p></div>`;

function every(ms, fn) {
  const id = setInterval(() => { if (!document.hidden) fn(); }, ms);
  state.timers.push(id);
}
function clearTimers() { state.timers.forEach(clearInterval); state.timers = []; }

// ================================================================ uygulama olarak yükleme (PWA)

const ua = navigator.userAgent;
const platform = /iPhone|iPad|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1) ? 'ios'
  : /Android/.test(ua) ? 'android' : 'pc';
const installed = () => matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;

window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  state.installEvt = e;
});
window.addEventListener('appinstalled', () => {
  state.installEvt = null;
  toast('Uygulama yüklendi. Ana ekranından açabilirsin.', 'ok');
  $$('[data-install-item]').forEach((x) => x.remove());
});

function installHtml() {
  const steps = {
    android: ['Android', 'phone', ['Siteyi <b>Chrome</b> ile aç.', 'Sağ üstteki <b>⋮</b> menüsüne dokun.', '<b>Uygulamayı yükle</b> ya da <b>Ana ekrana ekle</b> seçeneğine dokun.']],
    ios: ['iPhone / iPad', 'phone', ['Siteyi <b>Safari</b> ile aç.', 'Alttaki <b>Paylaş</b> düğmesine (kare ve yukarı ok) dokun.', '<b>Ana Ekrana Ekle</b> seçeneğine, sonra <b>Ekle</b> düğmesine dokun.']],
    pc: ['Bilgisayar', 'monitor', ['Siteyi <b>Chrome</b> ya da <b>Edge</b> ile aç.', 'Adres çubuğunun sağındaki <b>yükle</b> simgesine tıkla.', '<b>Yükle</b> düğmesine bas; masaüstüne kısayol eklenir.']],
  };
  const adres = (state.meta.agAdresleri || [])[0];
  return `<div class="install">
    <div class="install-main">
      <img src="icon-192.png" alt="" class="app-ico">
      <div class="grow"><h3>Eczane Otomasyon</h3>
        <p class="muted">Telefon, tablet ve bilgisayara uygulama olarak kurulur. Ücretsiz, birkaç saniye sürer, ekstra yer kaplamaz.</p></div>
      <button class="btn primary lg" data-install>${icon('download')}Uygulamayı yükle</button>
    </div>
    ${adres ? `<div class="alert info">${icon('wifi')} Telefonunu aynı Wi-Fi ağına bağla ve tarayıcıya şunu yaz: <b class="addr">${esc(adres)}</b></div>` : ''}
    <div class="install-steps">${Object.entries(steps).map(([k, [title, ic, list]]) => `
      <div class="install-step ${k === platform ? 'on' : ''}" data-platform="${k}">
        <div class="row">${icon(ic)}<b>${title}</b>${k === platform ? '<span class="badge info">Senin cihazın</span>' : ''}</div>
        <ol>${list.map((x) => `<li>${x}</li>`).join('')}</ol></div>`).join('')}
    </div></div>`;
}

async function doInstall() {
  if (installed()) return toast('Uygulama zaten yüklü ve şu an onu kullanıyorsun.', 'ok');
  if (state.installEvt) {
    const e = state.installEvt;
    state.installEvt = null;
    e.prompt();
    const r = await e.userChoice.catch(() => null);
    if (r && r.outcome === 'dismissed') state.installEvt = e;
    return;
  }
  // Tarayıcı otomatik kurulum penceresi sunmuyorsa adımları göster.
  const el = $(`.install-step[data-platform="${platform}"]`);
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    el.classList.remove('flash'); void el.offsetWidth; el.classList.add('flash');
  }
  toast({ ios: 'Safari: Paylaş → Ana Ekrana Ekle', android: 'Chrome menüsü (⋮) → Ana ekrana ekle', pc: 'Adres çubuğundaki yükle simgesine tıkla.' }[platform]);
}

function showInstallDialog() {
  const d = document.createElement('dialog');
  d.className = 'wide';
  d.innerHTML = `<div class="row between"><h3>Uygulamayı yükle</h3><button class="btn icon ghost" data-close aria-label="Kapat">${icon('x')}</button></div>${installHtml()}`;
  document.body.append(d);
  d.addEventListener('close', () => d.remove());
  $('[data-close]', d).addEventListener('click', () => d.close());
  d.showModal();
}

document.addEventListener('click', (e) => {
  if (e.target.closest('[data-install]')) { e.preventDefault(); doInstall(); }
  if (e.target.closest('[data-install-dialog]')) { e.preventDefault(); showInstallDialog(); }
});

// ================================================================ tema

function currentTheme() {
  const t = document.documentElement.dataset.theme;
  if (t) return t;
  return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}
function setTheme(t) {
  document.documentElement.dataset.theme = t;
  try { localStorage.setItem('tema', t); } catch { /* gizli pencere */ }
}
try { const t = localStorage.getItem('tema'); if (t) document.documentElement.dataset.theme = t; } catch { /* yok */ }
const themeBtn = () => `<button class="btn icon ghost" data-theme-toggle title="Tema değiştir" aria-label="Tema değiştir">${icon(currentTheme() === 'dark' ? 'sun' : 'moon')}</button>`;
document.addEventListener('click', (e) => {
  const b = e.target.closest('[data-theme-toggle]');
  if (!b) return;
  setTheme(currentTheme() === 'dark' ? 'light' : 'dark');
  $$('[data-theme-toggle]').forEach((x) => { x.innerHTML = icon(currentTheme() === 'dark' ? 'sun' : 'moon'); });
});

// ================================================================ yönlendirme

const go = (hash) => { if (location.hash === hash) render(); else location.hash = hash; };
window.addEventListener('hashchange', render);

async function render() {
  clearTimers();
  // #indir, #ozellikler gibi bölüm bağlantıları: tanıtım sayfasında o bölüme kaydır.
  if (location.hash && !location.hash.startsWith('#/') && !state.user) {
    renderLanding('giris', true);
    document.getElementById(location.hash.slice(1))?.scrollIntoView();
    return;
  }
  const hash = location.hash.startsWith('#/') ? location.hash : '#/';
  const parts = hash.slice(2).split('/');
  const page = parts[0] || '';
  const publicPages = ['', 'giris', 'kayit', 'sifre'];

  if (!state.user) {
    if (!publicPages.includes(page)) return go('#/giris');
    return renderLanding(page || 'giris', !page);
  }
  if (publicPages.includes(page)) return go('#/panel');

  const pages = {
    panel: pagePanel, receteler: pageReceteler, yeni: pageYeni, stok: pageStok, hareketler: pageHareketler, hesap: pageHesap,
  };
  const allowed = { yeni: 'DOKTOR', stok: 'ECZACI', hareketler: 'ECZACI' };
  const fn = pages[page];
  if (!fn || (allowed[page] && allowed[page] !== state.user.role)) return go('#/panel');
  renderShell(page);
  try {
    await fn(parts.slice(1));
  } catch (ex) {
    if (ex.status !== 401) $('#content').innerHTML = `<div class="alert error">${esc(ex.message)}</div>`;
  }
}

// ================================================================ tanıtım + giriş

function renderLanding(mode, scrollTop) {
  app.innerHTML = `
  <header class="site-nav">
    <a class="brand" href="#/"><img src="logo.png" alt="">Eczane Otomasyon</a>
    <nav>
      <a class="link hide-sm" href="#ozellikler" data-scroll>Özellikler</a>
      <a class="link hide-sm" href="#nasil" data-scroll>Nasıl çalışır?</a>
      <a class="link hide-sm" href="#guvenlik" data-scroll>Güvenlik</a>
      <a class="btn sm" href="#indir" data-scroll>${icon('download')}<span><span class="hide-xs">Uygulamayı </span>İndir</span></a>
      ${themeBtn()}
      <a class="btn primary sm" href="#/giris">Giriş yap</a>
    </nav>
  </header>
  <main class="wrap">
    <section class="hero">
      <div>
        <span class="pill">${icon('wifi')} Artık tarayıcıdan da erişilebilir</span>
        <h1>Reçeteden teslime,<br><em>tek bir sistemde.</em></h1>
        <p class="lead">Doktor reçeteyi yazar, hasta takip eder, eczacı stoğu kontrol edip ilaçları teslim eder.
        Hepsi aynı veritabanında, gerçek zamanlı mesajlaşmayla.</p>
        <div class="row">
          <a class="btn primary lg" href="#/kayit">Ücretsiz hesap oluştur</a>
          <a class="btn lg" href="#ozellikler" data-scroll>Neler yapabilir?</a>
        </div>
        <div class="hero-points">
          <span>${icon('check')} Masaüstü uygulamasıyla aynı veriler</span>
          <span>${icon('check')} Telefon ve tablette çalışır</span>
          <span>${icon('check')} Kurulum gerektirmez</span>
        </div>
      </div>
      <div class="auth-card" id="auth"></div>
    </section>

    <section class="section" id="ozellikler">
      <h2>Her rol için ayrı bir ekran</h2>
      <p class="lead">Herkes yalnızca kendi işiyle ilgili bilgileri görür. Yetki kontrolleri sunucuda yapılır.</p>
      <div class="features">
        <div class="feature"><div class="ico">${icon('doktor')}</div><h3>Doktor</h3><ul>
          <li>T.C. girince hastayı anında doğrular</li><li>İlaç adı otomatik tamamlama, hazır kullanım talimatları</li>
          <li>Reçeteyi istediği eczaneye yönlendirir</li><li>Bekleyen reçeteyi sebep belirterek iptal eder</li></ul></div>
        <div class="feature"><div class="ico">${icon('hasta')}</div><h3>Hasta</h3><ul>
          <li>Tüm reçetelerini ve durumlarını görür</li><li>İlaçların kullanım talimatlarını okur</li>
          <li>Doktoru ve eczacısıyla reçete üzerinden yazışır</li><li>Teslim ve iptal anında reçetede görünür</li></ul></div>
        <div class="feature"><div class="ico">${icon('eczaci')}</div><h3>Eczacı</h3><ul>
          <li>Teslimde her ilacın stok durumu tek bakışta</li><li>Teslim tek işlemde: ya hepsi ya hiçbiri</li>
          <li>Stok giriş/çıkışı sebebiyle kaydedilir</li><li>Kritik seviye uyarıları ve hareket geçmişi</li></ul></div>
      </div>
    </section>

    <section class="section" id="nasil">
      <h2>Nasıl çalışır?</h2>
      <p class="lead">Eczane bilgisayarında sunucu çalışır; aynı ağdaki herkes tarayıcıdan bağlanır.</p>
      <div class="steps">
        <div class="step"><h3>Hesap oluştur</h3><p>Rolünü seç, T.C. Kimlik No ve güçlü bir şifreyle kaydol.</p></div>
        <div class="step"><h3>Doktor yazar</h3><p>Hasta, eczane ve ilaçlar seçilir; reçete anında eczaneye düşer.</p></div>
        <div class="step"><h3>Eczacı hazırlar</h3><p>Stok kontrol edilir, eksik varsa reçete üzerinden mesaj atılır.</p></div>
        <div class="step"><h3>Teslim edilir</h3><p>Stok otomatik düşer, hasta ve doktor bilgilendirilir.</p></div>
      </div>
    </section>

    <section class="section" id="guvenlik">
      <h2>Verileriniz güvende</h2>
      <div class="security">
        <div>${icon('lock')}<b>Güçlü şifreleme</b><span>Şifreler PBKDF2-SHA256 ile 600.000 tur özetlenir, hiçbir yerde açık tutulmaz.</span></div>
        <div>${icon('shield')}<b>Deneme sınırı</b><span>Hatalı girişlerde artan süreli kilit; kaba kuvvet denemeleri durdurulur.</span></div>
        <div>${icon('user')}<b>T.C. doğrulama</b><span>Kayıtta resmi T.C. Kimlik No algoritmasıyla kontrol yapılır.</span></div>
        <div>${icon('db')}<b>Otomatik yedek</b><span>Veritabanının her gün yedeği alınır, son 14 yedek saklanır.</span></div>
      </div>
    </section>

    <section class="section" id="indir">
      <h2>Uygulamayı indir</h2>
      <p class="lead">Telefonuna ya da bilgisayarına kur, ana ekrandan tek dokunuşla aç. Veriler sunucuda kalır; her cihazda aynı hesabı kullanırsın.</p>
      ${installed() ? `<div class="alert ok">${icon('check')} Uygulama bu cihazda yüklü.</div>` : installHtml()}
    </section>

    <div class="cta">
      <div><h2>Hemen başla</h2><p>Hesabını oluştur, birkaç dakika içinde ilk reçeteni yaz.</p></div>
      <a class="btn lg" href="#/kayit">Kayıt ol</a>
    </div>
  </main>
  <footer class="site"><div class="wrap row between">
    <span>© ${new Date().getFullYear()} Eczane Otomasyon</span><span>Doktor · Hasta · Eczacı</span></div></footer>`;

  $$('[data-scroll]', app).forEach((a) => a.addEventListener('click', (e) => {
    e.preventDefault();
    document.getElementById(a.getAttribute('href').slice(1))?.scrollIntoView({ behavior: 'smooth' });
  }));
  renderAuth(mode);
  if (scrollTop) window.scrollTo(0, 0);
  else if (innerWidth < 900) $('#auth').scrollIntoView();
}

function renderAuth(mode) {
  const box = $('#auth');
  const tabs = `<div class="tabs" role="tablist">
    <button data-go="#/giris" class="${mode === 'giris' ? 'on' : ''}">Giriş</button>
    <button data-go="#/kayit" class="${mode === 'kayit' ? 'on' : ''}">Kayıt ol</button></div>`;

  if (mode === 'giris') {
    box.innerHTML = `${tabs}<h2>Tekrar hoş geldin</h2><p class="sub">Hesabına giriş yap.</p>
      <form class="form" id="f">
        <div class="alert error form-error hidden"></div>
        ${tcField()}
        <label class="field">Şifre<input type="password" name="password" required autocomplete="current-password" maxlength="128"></label>
        <button class="btn primary block lg" type="submit">Giriş yap</button>
        <a href="#/sifre" class="small">Şifremi unuttum</a>
      </form>`;
    bindForm($('#f', box), async (v) => {
      const r = await api('POST', '/api/auth/login', v);
      state.user = r.user;
      toast(`Hoş geldin, ${r.user.fullName}`, 'ok');
      go('#/panel');
    });
  } else if (mode === 'kayit') {
    const ecz = state.meta.eczaneler.map((e) => `<option value="${e.id}">${esc(e.ad)}</option>`).join('');
    box.innerHTML = `${tabs}<h2>Hesap oluştur</h2><p class="sub">Bilgilerin yalnızca bu sistemde saklanır.</p>
      <form class="form" id="f">
        <div class="alert error form-error hidden"></div>
        <div class="roles">
          <label><input type="radio" name="role" value="hasta" checked>${icon('hasta')}Hasta</label>
          <label><input type="radio" name="role" value="doktor">${icon('doktor')}Doktor</label>
          <label><input type="radio" name="role" value="eczaci">${icon('eczaci')}Eczacı</label>
        </div>
        <label class="field">Ad Soyad<input name="fullName" required maxlength="80" autocomplete="name"></label>
        ${tcField()}
        <div id="eczBox" class="form hidden">
          <label class="field">Eczane<select name="eczaneId"><option value="">Seç…</option>${ecz}<option value="yeni">+ Yeni eczane ekle</option></select></label>
          <label class="field hidden" id="yeniEcz">Yeni eczane adı<input name="yeniEczaneAdi" maxlength="80"></label>
        </div>
        ${pwField('password', 'Şifre', 'En az 8 karakter, harf ve rakam içermeli.')}
        <label class="field">Şifre (tekrar)<input type="password" name="passwordAgain" required autocomplete="new-password" maxlength="128"></label>
        <label class="field">Güvenlik sorusu <span class="hint">Şifreni unutursan sorulur.</span>
          <select name="securityQuestion" required><option value="">Seç…</option>
          ${state.meta.guvenlikSorulari.map((q) => `<option>${esc(q)}</option>`).join('')}</select></label>
        <label class="field">Cevap<input name="securityAnswer" required maxlength="80" autocomplete="off"></label>
        <button class="btn primary block lg" type="submit">Hesabı oluştur</button>
      </form>`;
    const f = $('#f', box);
    const sync = () => {
      const isEcz = f.role.value === 'eczaci';
      $('#eczBox', f).classList.toggle('hidden', !isEcz);
      $('#yeniEcz', f).classList.toggle('hidden', !(isEcz && f.eczaneId.value === 'yeni'));
    };
    f.addEventListener('change', sync);
    bindStrength(f.password, $('.strength i', f));
    bindForm(f, async (v) => {
      if (v.role !== 'eczaci') { delete v.eczaneId; delete v.yeniEczaneAdi; }
      else if (v.eczaneId === 'yeni') delete v.eczaneId;
      else delete v.yeniEczaneAdi;
      const r = await api('POST', '/api/auth/register', v);
      state.user = r.user;
      state.meta = await api('GET', '/api/meta');
      toast('Hesabın oluşturuldu.', 'ok');
      go('#/panel');
    });
  } else {
    box.innerHTML = `${tabs}<h2>Şifreni sıfırla</h2><p class="sub">Güvenlik sorunu cevaplayarak yeni şifre belirle.</p>
      <form class="form" id="f1">
        <div class="alert error form-error hidden"></div>
        ${tcField()}
        <button class="btn primary block" type="submit">Devam</button>
        <a href="#/giris" class="small">Girişe dön</a>
      </form>`;
    bindForm($('#f1', box), async (v) => {
      const r = await api('POST', '/api/auth/forgot/question', v);
      box.innerHTML = `${tabs}<h2>Şifreni sıfırla</h2><p class="sub">T.C.: ${esc(maskTc(v.tc))}</p>
        <form class="form" id="f2">
          <div class="alert error form-error hidden"></div>
          <div class="alert info">${esc(r.question)}</div>
          <label class="field">Cevabın<input name="answer" required maxlength="80" autocomplete="off"></label>
          ${pwField('password', 'Yeni şifre', 'En az 8 karakter, harf ve rakam içermeli.')}
          <label class="field">Yeni şifre (tekrar)<input type="password" name="passwordAgain" required autocomplete="new-password"></label>
          <button class="btn primary block" type="submit">Şifreyi değiştir</button>
        </form>`;
      const f2 = $('#f2', box);
      bindStrength(f2.password, $('.strength i', f2));
      bindForm(f2, async (w) => {
        await api('POST', '/api/auth/forgot/reset', { ...w, tc: v.tc });
        toast('Şifren değiştirildi. Yeni şifrenle giriş yapabilirsin.', 'ok');
        go('#/giris');
      });
    });
  }
  $$('[data-go]', box).forEach((b) => b.addEventListener('click', () => go(b.dataset.go)));
}

// ================================================================ uygulama kabuğu

function renderShell(page) {
  const u = state.user;
  const items = [
    ['panel', 'home', 'Genel Bakış'],
    ['receteler', 'file', 'Reçeteler', true],
    u.role === 'DOKTOR' && ['yeni', 'plus', 'Yeni Reçete'],
    u.role === 'ECZACI' && ['stok', 'box', 'Stok'],
    u.role === 'ECZACI' && ['hareketler', 'history', 'Stok Hareketleri'],
    ['hesap', 'user', 'Hesabım'],
  ].filter(Boolean);
  app.innerHTML = `<div class="shell" id="shell">
    <aside class="sidebar">
      <a class="brand" href="#/panel"><img src="logo.png" alt="">Eczane Otomasyon</a>
      ${items.map(([p, ic, label, cnt]) => `<a class="nav-item ${p === page ? 'on' : ''}" href="#/${p}">${icon(ic)}${label}
        ${cnt ? `<span class="count ${state.unread ? '' : 'hidden'}" id="unread">${state.unread}</span>` : ''}</a>`).join('')}
      <div class="spacer"></div>
      ${installed() ? '' : `<a class="nav-item" href="#" data-install-dialog data-install-item>${icon('download')}Uygulamayı yükle</a>`}
      <div class="me"><div class="avatar">${esc(u.initials)}</div>
        <div class="who"><b>${esc(u.fullName)}</b><span class="muted small">${esc(u.roleLabel)}${u.eczaneAdi ? ` · ${esc(u.eczaneAdi)}` : ''}</span></div></div>
      <div class="row">${themeBtn()}<button class="btn ghost" id="logout">${icon('logout')}Çıkış</button></div>
    </aside>
    <main class="main">
      <div class="topbar"><button class="btn icon menu-btn" id="menu" aria-label="Menü">${icon('menu')}</button>
        <div><h1 id="title"></h1><div class="sub" id="subtitle"></div></div><div class="actions" id="actions"></div></div>
      <div id="content"><div class="boot"><div class="spinner"></div></div></div>
    </main></div>`;
  $('#logout').addEventListener('click', async () => {
    try { await api('POST', '/api/auth/logout'); } catch { /* yine de çık */ }
    state.user = null;
    go('#/');
  });
  const shell = $('#shell');
  $('#menu').addEventListener('click', () => shell.classList.toggle('menu-open'));
  shell.addEventListener('click', (e) => { if (e.target === shell) shell.classList.remove('menu-open'); });
  $$('.nav-item', shell).forEach((a) => a.addEventListener('click', () => shell.classList.remove('menu-open')));
  every(15000, refreshUnread);
}

function setHeader(title, subtitle = '', actions = '') {
  $('#title').textContent = title;
  $('#subtitle').textContent = subtitle;
  $('#actions').innerHTML = actions;
  document.title = `${title} · Eczane Otomasyon`;
}

async function refreshUnread() {
  try {
    const r = await api('GET', '/api/okunmamis');
    state.unread = r.sayi;
    const el = $('#unread');
    if (el) { el.textContent = r.sayi; el.classList.toggle('hidden', !r.sayi); }
  } catch { /* sessiz */ }
}

const karsiTaraf = (r) => (state.user.role === 'HASTA' ? `Dr. ${r.doktorAdi}` : r.hastaAdi);

// ================================================================ genel bakış

async function pagePanel() {
  const u = state.user;
  const hour = new Date().getHours();
  const selam = hour < 6 ? 'İyi geceler' : hour < 12 ? 'Günaydın' : hour < 18 ? 'İyi günler' : 'İyi akşamlar';
  setHeader(`${selam}, ${u.fullName.split(' ')[0]}`, new Date().toLocaleDateString('tr-TR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }),
    u.role === 'DOKTOR' ? `<a class="btn primary" href="#/yeni">${icon('plus')}Yeni reçete</a>` : '');
  const o = await api('GET', '/api/ozet');
  const s = o.stats;
  state.unread = s.okunmamis;
  refreshUnread();
  const bugunLabel = { ECZACI: 'Bugün teslim edilen', DOKTOR: 'Bugün yazılan', HASTA: 'Bugün gelen' }[u.role];
  const stat = (cls, ic, n, label) => `<div class="stat ${cls}"><div class="ico">${icon(ic)}</div><div><b>${n}</b><span>${label}</span></div></div>`;
  const kisiCol = u.role === 'HASTA' ? 'Doktor' : 'Hasta';

  const rows = o.sonReceteler.map((r) => `<tr class="click" data-id="${r.id}"><td>#${r.id}</td><td>${esc(karsiTaraf(r))}</td>
    <td class="muted">${fmtDate(r.tarih)}</td><td>${durumBadge(r.durum)}
    ${r.okunmamis ? `<span class="badge count">${r.okunmamis}</span>` : ''}</td></tr>`).join('');

  let side;
  if (u.role === 'ECZACI') {
    const k = o.kritikStok || [];
    side = `<div class="card"><div class="card-h"><h2>Kritik stok</h2><a href="#/stok" class="small">Tüm stok</a></div>
      ${k.length ? `<table><tbody>${k.map((x) => `<tr><td>${esc(x.ilacAdi)}</td><td class="num">${x.miktar} / ${x.minMiktar}</td>
      <td class="num">${x.tukendi ? '<span class="badge danger">Tükendi</span>' : '<span class="badge warn">Kritik</span>'}</td></tr>`).join('')}</tbody></table>`
      : empty('check', 'Tüm ilaçlar yeterli seviyede.')}</div>`;
  } else if (u.role === 'DOKTOR') {
    side = `<div class="card"><div class="card-h"><h2>Hızlı işlemler</h2></div><div class="form">
      <a class="btn primary block" href="#/yeni">${icon('plus')}Yeni reçete yaz</a>
      <a class="btn block" href="#/receteler">${icon('file')}Reçetelerim</a>
      <p class="muted small">Hastanın T.C. Kimlik No'sunu girdiğinde adı otomatik doğrulanır. Aynı ilacı iki kez eklersen adetler birleştirilir.</p></div></div>`;
  } else {
    side = `<div class="card"><div class="card-h"><h2>Bilgi</h2></div><div class="form">
      <p class="muted small">Doktorun reçete yazdığında burada görünür. Reçetenin içinden doktorunla ve eczacınla mesajlaşabilirsin.</p>
      <a class="btn block" href="#/receteler">${icon('file')}Reçetelerim</a></div></div>`;
  }

  $('#content').innerHTML = `<div class="stats">
      ${stat('info', 'file', s.toplam, 'Toplam reçete')}
      ${stat('warn', 'clock', s.bekleyen, 'Bekleyen')}
      ${stat('ok', 'check', s.verilen, 'Teslim edilen')}
      ${stat('danger', 'x', s.iptal, 'İptal')}
      ${stat('', 'msg', s.okunmamis, 'Okunmamış mesaj')}
      ${stat('', 'history', s.bugun, bugunLabel)}
      ${u.role === 'ECZACI' ? stat(s.kritikStok ? 'danger' : 'ok', 'alert', s.kritikStok, 'Kritik stok') : ''}
    </div>
    <div class="two">
      <div class="card"><div class="card-h"><h2>Son reçeteler</h2><a href="#/receteler" class="small">Tümü</a></div>
        ${rows ? `<div class="table-wrap"><table><thead><tr><th>No</th><th>${kisiCol}</th><th>Tarih</th><th>Durum</th></tr></thead><tbody>${rows}</tbody></table></div>`
        : empty('file', 'Henüz reçete yok.')}</div>
      ${side}
    </div>`;
  $$('tr.click', app).forEach((tr) => tr.addEventListener('click', () => go(`#/receteler/${tr.dataset.id}`)));
}

// ================================================================ reçeteler

async function pageReceteler([idStr]) {
  setHeader('Reçeteler', state.user.role === 'ECZACI' ? `${state.user.eczaneAdi || ''} eczanesine gelen reçeteler` : '',
    state.user.role === 'DOKTOR' ? `<a class="btn primary" href="#/yeni">${icon('plus')}Yeni reçete</a>` : '');
  const selected = idStr ? Number(idStr) : null;
  let list = await api('GET', '/api/receteler');
  const ui = { q: '', durum: '' };

  $('#content').innerHTML = `<div class="md">
    <div class="card">
      <div class="filters">
        <input type="search" id="q" placeholder="Ara: no, hasta, doktor, ilaç notu…" aria-label="Ara">
        <div class="chips">${[['', 'Tümü'], ['AKTIF', 'Bekliyor'], ['VERILDI', 'Verildi'], ['IPTAL', 'İptal']]
          .map(([v, l]) => `<button class="chip ${v === '' ? 'on' : ''}" data-durum="${v}">${l}</button>`).join('')}</div>
      </div>
      <div class="list" id="list"></div>
    </div>
    <div id="detail">${selected ? '<div class="card"><div class="boot"><div class="spinner"></div></div></div>'
      : `<div class="card">${empty('file', 'Ayrıntılarını görmek için soldan bir reçete seç.')}</div>`}</div>
  </div>`;

  const drawList = () => {
    const q = ui.q.toLocaleLowerCase('tr');
    const rows = list.filter((r) => (!ui.durum || r.durum === ui.durum)
      && (!q || `#${r.id} ${r.hastaAdi} ${r.doktorAdi} ${r.eczaneAdi} ${r.aciklama || ''}`.toLocaleLowerCase('tr').includes(q)));
    $('#list').innerHTML = rows.length ? rows.map((r) => `<div class="list-item ${r.id === selected ? 'on' : ''}" data-id="${r.id}" tabindex="0">
      <div class="t">#${r.id} · ${esc(karsiTaraf(r))}<span class="date">${relDate(r.tarih)}</span></div>
      <div class="m">${r.kalemSayisi} ilaç · ${esc(r.eczaneAdi || '—')}
        ${r.okunmamis ? `<span class="badge count" title="Okunmamış mesaj">${r.okunmamis}</span>` : ''}${durumBadge(r.durum)}</div>
    </div>`).join('') : empty('search', list.length ? 'Aramana uyan reçete yok.' : 'Henüz reçete yok.');
    $$('.list-item', $('#list')).forEach((el) => {
      const open = () => go(`#/receteler/${el.dataset.id}`);
      el.addEventListener('click', open);
      el.addEventListener('keydown', (e) => { if (e.key === 'Enter') open(); });
    });
  };
  $('#q').addEventListener('input', (e) => { ui.q = e.target.value; drawList(); });
  $$('.chip').forEach((c) => c.addEventListener('click', () => {
    ui.durum = c.dataset.durum;
    $$('.chip').forEach((x) => x.classList.toggle('on', x === c));
    drawList();
  }));
  drawList();
  every(20000, async () => { try { list = await api('GET', '/api/receteler'); drawList(); } catch { /* sessiz */ } });

  if (selected) {
    await drawDetail(selected, async () => { list = await api('GET', '/api/receteler'); drawList(); });
    if (innerWidth < 1100) $('#detail').scrollIntoView({ behavior: 'smooth' });
  }
}

async function drawDetail(id, reloadList) {
  const box = $('#detail');
  let r;
  try {
    r = await api('GET', `/api/receteler/${id}`);
  } catch (ex) {
    box.innerHTML = `<div class="card"><div class="alert error">${esc(ex.message)}</div></div>`;
    return;
  }
  const d = r.detay;
  const u = state.user;
  const stok = r.stok || null;
  const eksik = stok ? stok.filter((x) => !x.yeterli).length : 0;

  const kalemRows = d.kalemler.map((k, i) => {
    const st = stok && stok[i];
    return `<tr><td><b>${esc(k.ilacAdi)}</b></td><td class="num">${k.adet}</td><td class="muted">${esc(k.kullanim || '—')}</td>
      ${stok ? `<td class="num">${st.stok} ${st.yeterli ? '<span class="badge ok">Var</span>' : '<span class="badge danger">Eksik</span>'}</td>` : ''}</tr>`;
  }).join('');

  box.innerHTML = `<div class="card">
    <div class="card-h"><h2>Reçete #${d.id} ${durumBadge(d.durum)}</h2>
      <div class="row">${r.iptalEdilebilir ? `<button class="btn danger sm" id="iptal">${icon('x')}İptal et</button>` : ''}
      ${r.verilebilir ? `<button class="btn primary sm" id="ver" ${eksik ? 'disabled title="Stokta eksik ilaç var"' : ''}>${icon('check')}İlaçları teslim et</button>` : ''}</div></div>
    <div class="kv">
      <div><span>Tarih</span><b>${fmtDate(d.tarih)}</b></div>
      <div><span>Hasta</span><b>${esc(d.hastaAdi)}</b>${u.role !== 'HASTA' ? `<div class="muted small">${esc(maskTc(d.hastaTc))}</div>` : ''}</div>
      <div><span>Doktor</span><b>Dr. ${esc(d.doktorAdi)}</b></div>
      <div><span>Eczane</span><b>${esc(d.eczaneAdi || '—')}</b></div>
    </div>
    ${d.aciklama ? `<div class="note">${esc(d.aciklama)}</div>` : ''}
    ${eksik ? `<div class="alert error">${eksik} ilaç stokta yetersiz. Stok sayfasından giriş yapabilir ya da hastaya mesaj atabilirsin.</div>` : ''}
    <div class="table-wrap"><table><thead><tr><th>İlaç</th><th class="num">Adet</th><th>Kullanım</th>${stok ? '<th class="num">Stokta</th>' : ''}</tr></thead>
    <tbody>${kalemRows}</tbody></table></div>
  </div>
  <div class="card"><div class="card-h"><h2>${icon('msg')} Mesajlar</h2><span class="muted small">Doktor, hasta ve eczacı görebilir</span></div>
    <div class="chat" id="chat"></div>
    <form class="chat-form" id="chatForm"><input name="metin" maxlength="${state.meta.mesajMax}" placeholder="Mesaj yaz…" autocomplete="off" required>
      <button class="btn primary icon" type="submit" aria-label="Gönder">${icon('send')}</button></form>
  </div>`;

  $('#ver')?.addEventListener('click', async () => {
    if (!(await ask({ title: 'İlaçları teslim et', text: `Reçete #${d.id} hastaya teslim edilecek ve ilaçlar stoktan düşülecek.`, ok: 'Teslim et' }))) return;
    try {
      await api('POST', `/api/receteler/${d.id}/ver`);
      toast('İlaçlar teslim edildi.', 'ok');
      await reloadList();
      await drawDetail(id, reloadList);
    } catch (ex) { toast(ex.message, 'error'); }
  });
  $('#iptal')?.addEventListener('click', async () => {
    const sebep = await ask({ title: 'Reçeteyi iptal et', text: 'Bu işlem geri alınamaz.', ok: 'İptal et', danger: true, input: 'Sebep (isteğe bağlı)' });
    if (sebep === null) return;
    try {
      await api('POST', `/api/receteler/${d.id}/iptal`, { sebep });
      toast('Reçete iptal edildi.');
      await reloadList();
      await drawDetail(id, reloadList);
    } catch (ex) { toast(ex.message, 'error'); }
  });

  let last = '';
  const drawChat = (msgs) => {
    const sig = msgs.map((m) => m.id).join(',');
    if (sig === last) return;
    last = sig;
    const chat = $('#chat');
    if (!chat) return;
    chat.innerHTML = msgs.length ? msgs.map((m) => {
      if (m.sistem) return `<div class="msg sys">${esc(m.metin)}<div class="small">${fmtDate(m.tarih)}</div></div>`;
      const mine = m.gonderenId === u.id;
      const rol = { DOKTOR: 'Doktor', HASTA: 'Hasta', ECZACI: 'Eczacı' }[m.gonderenRol] || '';
      return `<div class="msg ${mine ? 'mine' : ''}"><div class="h">${mine ? 'Sen' : `${esc(m.gonderenAdi)} · ${rol}`} · ${relDate(m.tarih)}</div>${esc(m.metin)}</div>`;
    }).join('') : empty('msg', 'Henüz mesaj yok.');
    chat.scrollTop = chat.scrollHeight;
  };
  const loadChat = async () => {
    try { drawChat(await api('GET', `/api/receteler/${id}/mesajlar`)); refreshUnread(); } catch { /* sessiz */ }
  };
  await loadChat();
  clearInterval(state.chatTimer);
  state.chatTimer = setInterval(() => { if (!document.hidden && $('#chat')) loadChat(); }, 5000);
  state.timers.push(state.chatTimer);
  bindForm($('#chatForm'), async (v, form) => {
    drawChat(await api('POST', `/api/receteler/${id}/mesajlar`, v));
    form.reset();
    form.metin.focus();
  });
}

// ================================================================ yeni reçete (doktor)

async function ilaclar() {
  if (!state.ilaclar) state.ilaclar = await api('GET', '/api/ilaclar');
  return state.ilaclar;
}

async function pageYeni() {
  setHeader('Yeni Reçete', 'Hastayı doğrula, ilaçları ekle ve eczaneye gönder.');
  const list = await ilaclar();
  const ecz = state.meta.eczaneler.map((e) => `<option value="${e.id}">${esc(e.ad)}</option>`).join('');
  $('#content').innerHTML = `<form class="form" id="f">
    <div class="card form">
      <div class="alert error form-error hidden"></div>
      <div class="grid2">
        <div class="form">${tcField('hastaTc', 'Hasta T.C. Kimlik No')}<div class="hasta-ok" id="hastaOk"></div></div>
        <label class="field">Eczane<select name="eczaneId" required><option value="">Seç…</option>${ecz}</select></label>
      </div>
    </div>
    <div class="card form">
      <div class="card-h"><h2>İlaçlar</h2><button type="button" class="btn sm" id="add">${icon('plus')}İlaç ekle</button></div>
      <div class="kalem kalem-h"><span>İlaç adı</span><span>Adet</span><span>Kullanım</span><span></span></div>
      <div class="form" id="kalemler"></div>
      <datalist id="ilacList">${list.map((i) => `<option value="${esc(i.ad)}">`).join('')}</datalist>
      <datalist id="kulList">${KULLANIM.map((k) => `<option value="${esc(k)}">`).join('')}</datalist>
    </div>
    <div class="card form">
      <label class="field">Açıklama <span class="hint">İsteğe bağlı, en fazla 500 karakter. Tanı, uyarılar vb.</span>
        <textarea name="aciklama" maxlength="500"></textarea></label>
      <div class="row end"><a class="btn" href="#/receteler">Vazgeç</a><button class="btn primary lg" type="submit">${icon('send')}Reçeteyi gönder</button></div>
    </div></form>`;

  const f = $('#f');
  const box = $('#kalemler');
  const addRow = () => {
    if (box.children.length >= state.meta.maxKalem) return toast(`En fazla ${state.meta.maxKalem} ilaç eklenebilir.`, 'error');
    const row = document.createElement('div');
    row.className = 'kalem';
    row.innerHTML = `<input class="ad" list="ilacList" placeholder="İlaç adı" maxlength="100" aria-label="İlaç adı">
      <input class="adet" type="number" min="1" max="${state.meta.maxAdet}" value="1" aria-label="Adet">
      <input class="kul" list="kulList" placeholder="Kullanım talimatı" maxlength="200" aria-label="Kullanım">
      <button type="button" class="btn icon ghost" aria-label="Sil">${icon('trash')}</button>`;
    $('button', row).addEventListener('click', () => { row.remove(); if (!box.children.length) addRow(); });
    box.append(row);
    $('.ad', row).focus();
  };
  $('#add').addEventListener('click', addRow);
  addRow();
  if (state.meta.eczaneler.length === 1) f.eczaneId.value = state.meta.eczaneler[0].id;

  let timer;
  f.hastaTc.addEventListener('input', () => {
    clearTimeout(timer);
    const tc = f.hastaTc.value.replace(/\D/g, '');
    const out = $('#hastaOk');
    out.className = 'hasta-ok';
    out.textContent = '';
    if (tc.length !== 11) return;
    timer = setTimeout(async () => {
      try {
        const r = await api('GET', `/api/hasta?tc=${encodeURIComponent(tc)}`);
        if (r.hasta) out.innerHTML = `${icon('check')} ${esc(r.hasta.fullName)}`;
        else { out.className = 'hasta-ok bad'; out.innerHTML = `${icon('alert')} Bu T.C. ile kayıtlı hasta yok.`; }
      } catch { /* sessiz */ }
    }, 250);
  });

  bindForm(f, async (v) => {
    const kalemler = $$('.kalem:not(.kalem-h)', box).map((row) => ({
      ilacAdi: $('.ad', row).value.trim(), adet: Number($('.adet', row).value), kullanim: $('.kul', row).value.trim(),
    })).filter((k) => k.ilacAdi);
    const r = await api('POST', '/api/receteler', { ...v, kalemler });
    state.ilaclar = null;
    toast(`Reçete #${r.id} oluşturuldu.`, 'ok');
    go(`#/receteler/${r.id}`);
  });
}

// ================================================================ stok (eczacı)

async function pageStok() {
  setHeader('Stok', state.user.eczaneAdi || '');
  let list = await api('GET', '/api/stok');
  const ilacOps = (await ilaclar()).map((i) => `<option value="${esc(i.ad)}">`).join('');
  const sebepler = (giris) => (giris ? state.meta.girisSebepleri : state.meta.cikisSebepleri).map((s) => `<option>${esc(s)}</option>`).join('');
  const ui = { q: '', kritik: false };

  $('#content').innerHTML = `<div class="card"><div class="card-h"><h2>Stok giriş / çıkış</h2></div>
    <form class="form" id="f">
      <div class="alert error form-error hidden"></div>
      <div class="grid2">
        <label class="field">İlaç<input name="ilacAdi" list="ilacList2" required maxlength="100" autocomplete="off"></label>
        <div class="grid2">
          <label class="field">İşlem<select name="yon"><option value="+">Giriş (+)</option><option value="-">Çıkış (−)</option></select></label>
          <label class="field">Miktar<input name="miktar" type="number" min="1" max="100000" required value="1"></label>
        </div>
      </div>
      <div class="form"><label class="field">Sebep<select name="sebep" id="sebep">${sebepler(true)}</select></label></div>
      <div class="row end"><button class="btn primary" type="submit">Kaydet</button></div>
      <datalist id="ilacList2">${ilacOps}</datalist>
    </form></div>
    <div class="card"><div class="card-h"><h2>İlaçlar</h2>
      <div class="row"><input type="search" id="q" placeholder="İlaç ara…" aria-label="İlaç ara">
      <button class="chip" id="onlyKritik">Yalnızca kritik</button></div></div>
      <div class="table-wrap" id="tbl"></div></div>`;

  const f = $('#f');
  f.yon.addEventListener('change', () => { $('#sebep').innerHTML = sebepler(f.yon.value === '+'); });

  const draw = () => {
    const q = ui.q.toLocaleLowerCase('tr');
    const rows = list.filter((k) => (!ui.kritik || k.kritik) && (!q || k.ilacAdi.toLocaleLowerCase('tr').includes(q)));
    $('#tbl').innerHTML = rows.length ? `<table><thead><tr><th>İlaç</th><th class="num">Miktar</th><th class="num">Kritik seviye</th><th>Durum</th><th>Son güncelleme</th></tr></thead>
      <tbody>${rows.map((k) => `<tr><td><b>${esc(k.ilacAdi)}</b></td><td class="num">${k.miktar}</td>
        <td class="num"><input class="inline-num" type="number" min="0" max="100000" value="${k.minMiktar}" data-min="${k.ilacId}" aria-label="Kritik seviye"></td>
        <td>${k.tukendi ? '<span class="badge danger">Tükendi</span>' : k.kritik ? '<span class="badge warn">Kritik</span>' : '<span class="badge ok">Yeterli</span>'}</td>
        <td class="muted">${fmtDate(k.sonGuncelleme)}</td></tr>`).join('')}</tbody></table>`
      : empty('box', list.length ? 'Aramana uyan ilaç yok.' : 'Stokta henüz ilaç yok. Yukarıdan giriş yap.');
    $$('[data-min]').forEach((inp) => inp.addEventListener('change', async () => {
      try {
        await api('POST', `/api/stok/${inp.dataset.min}/min`, { min: inp.value });
        toast('Kritik seviye kaydedildi.', 'ok');
        list = await api('GET', '/api/stok');
        draw();
      } catch (ex) { toast(ex.message, 'error'); }
    }));
  };
  $('#q').addEventListener('input', (e) => { ui.q = e.target.value; draw(); });
  $('#onlyKritik').addEventListener('click', (e) => { ui.kritik = !ui.kritik; e.target.classList.toggle('on', ui.kritik); draw(); });
  draw();

  bindForm(f, async (v) => {
    const delta = Number(v.miktar) * (v.yon === '-' ? -1 : 1);
    const r = await api('POST', '/api/stok/hareket', { ilacAdi: v.ilacAdi, delta, sebep: v.sebep });
    toast(`${v.ilacAdi}: yeni miktar ${r.yeniMiktar}`, 'ok');
    state.ilaclar = null;
    list = await api('GET', '/api/stok');
    f.ilacAdi.value = '';
    f.miktar.value = 1;
    draw();
  });
}

async function pageHareketler() {
  setHeader('Stok Hareketleri', 'Son 300 hareket: kim, ne zaman, neden.');
  const list = await api('GET', '/api/stok/hareketler');
  $('#content').innerHTML = `<div class="card">${list.length ? `<div class="table-wrap"><table>
    <thead><tr><th>Tarih</th><th>İlaç</th><th class="num">Değişim</th><th class="num">Yeni miktar</th><th>Sebep</th><th>Kullanıcı</th></tr></thead>
    <tbody>${list.map((h) => `<tr><td class="muted">${fmtDate(h.tarih)}</td><td><b>${esc(h.ilacAdi)}</b></td>
      <td class="num"><span class="badge ${h.degisim > 0 ? 'ok' : 'danger'}">${h.degisim > 0 ? '+' : ''}${h.degisim}</span></td>
      <td class="num">${h.yeniMiktar}</td>
      <td>${h.receteId ? `<a href="#/receteler/${h.receteId}">${esc(h.sebep)}</a>` : esc(h.sebep)}</td>
      <td class="muted">${esc(h.kullaniciAdi || '—')}</td></tr>`).join('')}</tbody></table></div>`
    : empty('history', 'Henüz stok hareketi yok.')}</div>`;
}

// ================================================================ hesap

async function pageHesap() {
  const u = state.user;
  setHeader('Hesabım', 'Profil, şifre ve güvenlik ayarları');
  $('#content').innerHTML = `<div class="two">
    <div>
      <div class="card"><div class="card-h"><h2>Profil</h2></div>
        <div class="row"><div class="avatar">${esc(u.initials)}</div><div><b>${esc(u.fullName)}</b><div class="muted small">${esc(u.roleLabel)}</div></div></div>
        <div class="kv"><div><span>T.C. Kimlik No</span><b>${esc(u.maskedTc)}</b></div>
          ${u.eczaneAdi ? `<div><span>Eczane</span><b>${esc(u.eczaneAdi)}</b></div>` : ''}
          <div><span>Kayıt tarihi</span><b>${fmtDate(u.createdAt, false)}</b></div></div>
        <form class="form" id="fAd"><div class="alert error form-error hidden"></div>
          <label class="field">Ad Soyad<input name="fullName" required maxlength="80" value="${esc(u.fullName)}"></label>
          <div class="row end"><button class="btn" type="submit">Adı güncelle</button></div></form></div>
      <div class="card"><div class="card-h"><h2>Güvenlik sorusu</h2></div>
        <form class="form" id="fSec"><div class="alert error form-error hidden"></div>
          <label class="field">Soru<select name="question" required><option value="">Seç…</option>
            ${state.meta.guvenlikSorulari.map((q) => `<option>${esc(q)}</option>`).join('')}</select></label>
          <label class="field">Cevap<input name="answer" required maxlength="80" autocomplete="off"></label>
          <label class="field">Mevcut şifre<input type="password" name="current" required autocomplete="current-password"></label>
          <div class="row end"><button class="btn" type="submit">Kaydet</button></div></form></div>
    </div>
    <div class="card"><div class="card-h"><h2>Şifre değiştir</h2></div>
      <form class="form" id="fPw"><div class="alert error form-error hidden"></div>
        <label class="field">Mevcut şifre<input type="password" name="current" required autocomplete="current-password"></label>
        ${pwField('password', 'Yeni şifre', 'En az 8 karakter, harf ve rakam içermeli.')}
        <label class="field">Yeni şifre (tekrar)<input type="password" name="passwordAgain" required autocomplete="new-password"></label>
        <p class="muted small">Şifreni değiştirdiğinde diğer cihazlardaki oturumların kapanır.</p>
        <div class="row end"><button class="btn primary" type="submit">Şifreyi değiştir</button></div></form></div>
  </div>`;
  bindForm($('#fAd'), async (v) => {
    const r = await api('POST', '/api/hesap/ad', v);
    state.user = r.user;
    toast('Adın güncellendi.', 'ok');
    render();
  });
  const fPw = $('#fPw');
  bindStrength(fPw.password, $('.strength i', fPw));
  bindForm(fPw, async (v, form) => {
    await api('POST', '/api/hesap/sifre', v);
    form.reset();
    toast('Şifren değiştirildi.', 'ok');
  });
  bindForm($('#fSec'), async (v, form) => {
    await api('POST', '/api/hesap/guvenlik', v);
    form.reset();
    toast('Güvenlik sorusu güncellendi.', 'ok');
  });
}

// ================================================================ başlangıç

(async function boot() {
  try {
    const [meta, me] = await Promise.all([api('GET', '/api/meta'), api('GET', '/api/auth/me')]);
    state.meta = meta;
    state.user = me.user;
  } catch (ex) {
    app.innerHTML = `<div class="boot"><p>${esc(ex.message)}</p><button class="btn" id="retry">Tekrar dene</button></div>`;
    $('#retry').addEventListener('click', () => location.reload());
    return;
  }
  render();
  if ('serviceWorker' in navigator && window.isSecureContext) {
    navigator.serviceWorker.register('sw.js').catch(() => { /* zorunlu değil */ });
  }
})();
