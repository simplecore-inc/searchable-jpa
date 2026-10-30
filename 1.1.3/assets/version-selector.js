/* Shared by current and archived documentation. No framework dependency. */
(function (root, factory) {
  var api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else api.start(root);
})(typeof window === 'undefined' ? globalThis : window, function () {
  'use strict';
  var releasePattern = /^\d+\.\d+\.\d+$/;
  var folderPattern = /^(?:snapshot|\d+\.\d+\.\d+(?:-SNAPSHOT)?)$/;

  function context(href) {
    var directory = new URL('./', href);
    var parts = directory.pathname.split('/').filter(Boolean);
    var current = parts[parts.length - 1];
    if (!folderPattern.test(current || '')) return null;
    return { current: current, base: new URL('../', directory) };
  }

  function versions(value) {
    if (!value || !Array.isArray(value.releases)) throw new Error('Invalid versions manifest');
    var releases = Array.from(new Set(value.releases.filter(function (v) {
      return typeof v === 'string' && releasePattern.test(v);
    })));
    releases.sort(function (a, b) {
      var left = a.split('.').map(BigInt), right = b.split('.').map(BigInt);
      for (var i = 0; i < 3; i++) if (left[i] !== right[i]) return left[i] > right[i] ? -1 : 1;
      return 0;
    });
    var snapshot = typeof value.snapshot === 'string' && folderPattern.test(value.snapshot)
      && !releasePattern.test(value.snapshot) ? value.snapshot : null;
    return (snapshot ? [snapshot] : []).concat(releases);
  }

  function route(hash) {
    var value = (hash || '#/en/').replace(/^#/, '');
    var path = value.split('?')[0];
    var match = /^\/(en|ko)\/(?:([A-Za-z0-9_-]+)(?:\.md)?)?$/.exec(path);
    if (!match) return { hash: '#/en/', file: 'en/README.md', language: 'en' };
    var name = match[2] || 'README';
    return { hash: '#' + value, file: match[1] + '/' + name + '.md', language: match[1] };
  }

  function target(base, version, hash) {
    if (!folderPattern.test(version)) throw new Error('Invalid version folder');
    var url = new URL(version + '/', base);
    url.hash = route(hash).hash;
    return url;
  }

  function start(win) {
    var location = context(win.location.href);
    if (!location) return; // A standalone local preview has no sibling versions.
    var doc = win.document, entries = [], error = false, busy = false, pending = null, navigationFailed = false;
    var text = {
      en: { label: 'Version', development: 'Development', current: 'Current document', retry: 'Retry',
        unavailable: 'Unable to load versions.', switching: 'Opening version…', failed: 'Unable to open this version. Please try again.' },
      ko: { label: '버전', development: '개발 버전', current: '현재 문서', retry: '다시 시도',
        unavailable: '버전 목록을 불러올 수 없습니다.', switching: '버전을 여는 중입니다.', failed: '버전을 열 수 없습니다. 다시 시도해 주세요.' }
    };
    function messages() { return text[route(win.location.hash).language]; }
    async function request(url) {
      var controller = new AbortController();
      var timeout = win.setTimeout(function () { controller.abort(); }, 8000);
      try { return await win.fetch(url, { cache: 'no-cache', signal: controller.signal }); }
      finally { win.clearTimeout(timeout); }
    }
    function render() {
      var sidebar = doc.querySelector('.sidebar');
      if (!sidebar) return;
      var previous = doc.querySelector('.version-selector');
      if (previous) previous.remove();
      var box = doc.createElement('div'); box.className = 'version-selector';
      var label = doc.createElement('label'); label.htmlFor = 'version-select'; label.textContent = messages().label;
      var select = doc.createElement('select'); select.id = 'version-select'; select.disabled = busy || !entries.length;
      var options = entries.slice();
      if (!options.includes(location.current)) options.unshift(location.current);
      options.forEach(function (version) {
        var option = doc.createElement('option'); option.value = version;
        option.textContent = version === 'snapshot' || version.endsWith('-SNAPSHOT')
          ? version + ' (' + messages().development + ')' : version;
        if (!entries.includes(version)) option.textContent += ' (' + messages().current + ')';
        option.selected = version === (pending || location.current);
        select.appendChild(option);
      });
      var status = doc.createElement('p'); status.className = 'version-status'; status.setAttribute('role', 'status');
      status.setAttribute('aria-live', 'polite');
      if (error) status.textContent = messages().unavailable;
      else if (busy) status.textContent = messages().switching;
      else if (navigationFailed) status.textContent = messages().failed;
      select.addEventListener('change', async function () {
        var selected = select.value;
        if (selected === location.current || !entries.includes(selected)) return;
        busy = true; pending = selected; navigationFailed = false; render();
        try {
          var requested = route(win.location.hash);
          var destination = target(location.base, selected, requested.hash);
          var response = await request(new URL(requested.file, destination));
          if (response.status === 404) {
            var fallback = requested.language + '/README.md';
            response = await request(new URL(fallback, destination));
            if (response.status === 404 && requested.language !== 'en') {
              response = await request(new URL('en/README.md', destination));
              destination.hash = '#/en/';
            } else destination.hash = '#/' + requested.language + '/';
          }
          if (!response.ok) throw new Error('Document unavailable');
          win.location.assign(destination.href);
        } catch (_) {
          busy = false; pending = null; navigationFailed = true; render();
        }
      });
      box.append(label, select, status);
      if (error) {
        var retry = doc.createElement('button'); retry.type = 'button'; retry.textContent = messages().retry;
        retry.addEventListener('click', load); box.appendChild(retry);
      }
      var appName = sidebar.querySelector('.app-name');
      if (appName) appName.insertAdjacentElement('afterend', box); else sidebar.prepend(box);
    }
    async function load() {
      error = false;
      try {
        var response = await request(new URL('versions.json', location.base));
        if (!response.ok) throw new Error('Versions unavailable');
        entries = versions(await response.json());
        if (!entries.length) throw new Error('Empty versions manifest');
      } catch (_) { error = true; }
      render();
    }
    win.$docsify = win.$docsify || {};
    win.$docsify.plugins = (win.$docsify.plugins || []).concat(function (hook) { hook.doneEach(render); });
    win.addEventListener('hashchange', render);
    doc.addEventListener('DOMContentLoaded', render, { once: true });
    load();
  }
  return { context: context, versions: versions, route: route, target: target, start: start };
});
