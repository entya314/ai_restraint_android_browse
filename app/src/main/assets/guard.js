(() => {
  'use strict';
  // Replaced with a JSON string in native code, never with unescaped URL text.
  const pinnedOrigin = __PINNED_ORIGIN__;
  const bridge = globalThis.RestrictedNavigation;
  const isTop = window === window.top;
  const semantic = 'input[type="search" i], [role~="searchbox" i], [role~="search" i], search';
  const editable = 'input,textarea,select,button,[contenteditable]';
  const allowed = value => {
    try {
      const u = new URL(value, document.baseURI);
      return u.protocol === 'https:' && u.origin === pinnedOrigin && !u.username && !u.password;
    } catch (_) { return false; }
  };
  function semanticAncestor(node) {
    while (node) {
      if (node.nodeType === 1 && node.matches(semantic)) return true;
      node = node.parentElement || (node.getRootNode && node.getRootNode().host);
    }
    return false;
  }
  const changed = new WeakMap();
  function disable(node) {
    if (!node || node.nodeType !== 1) return;
    if (!semanticAncestor(node)) {
      const previous = changed.get(node);
      if (previous) {
        changed.delete(node);
        if ('disabled' in node) node.disabled = previous.disabled;
        for (const key of ['contenteditable','aria-disabled']) {
          if (previous[key] === null) node.removeAttribute(key); else node.setAttribute(key, previous[key]);
        }
      }
      return;
    }
    if (!changed.has(node)) changed.set(node, {disabled:node.disabled,
      contenteditable:node.getAttribute('contenteditable'), 'aria-disabled':node.getAttribute('aria-disabled')});
    if ('disabled' in node && !node.disabled) node.disabled = true;
    if (node.hasAttribute('contenteditable') && node.getAttribute('contenteditable') !== 'false')
      node.setAttribute('contenteditable', 'false');
    if (node.getAttribute('aria-disabled') !== 'true') node.setAttribute('aria-disabled', 'true');
  }
  const roots = new WeakSet();
  function sweep(root) {
    if (root.nodeType === 1) disable(root);
    if (!root.querySelectorAll) return;
    for (const node of root.querySelectorAll(editable + ',[role]')) disable(node);
    for (const node of root.querySelectorAll('*')) if (node.shadowRoot) observe(node.shadowRoot);
  }
  function observe(root) {
    if (roots.has(root)) return;
    roots.add(root);
    sweep(root);
    new MutationObserver(records => {
      for (const r of records) {
        if (r.type === 'attributes') sweep(r.target);
        else for (const node of r.addedNodes) sweep(node);
      }
    }).observe(root, { subtree: true, childList: true, attributes: true,
      attributeFilter: ['type','role','contenteditable','disabled','aria-disabled'] });
  }
  const attachShadow = Element.prototype.attachShadow;
  Element.prototype.attachShadow = function(options) {
    const root = attachShadow.call(this, options);
    observe(root); // Newly created closed roots are visible here as well.
    return root;
  };
  observe(document);
  for (const type of ['beforeinput','keydown','paste','drop','focusin']) {
    document.addEventListener(type, e => {
      if (semanticAncestor(e.composedPath()[0])) {
        e.preventDefault(); e.stopImmediatePropagation(); disable(e.composedPath()[0]);
        if (type === 'focusin' && document.activeElement) document.activeElement.blur();
      }
    }, true);
  }
  window.open = () => null;
  // Native document interception must not be bypassed by a service worker.
  if (navigator.serviceWorker) {
    try { Object.defineProperty(navigator.serviceWorker, 'register', {
      value: () => Promise.reject(new DOMException('Service workers are disabled', 'SecurityError'))
    }); } catch (_) { /* Native service-worker network blocking is also required. */ }
  }
  function refuse() {
    if (bridge) bridge.postMessage(JSON.stringify({kind:'blocked'}));
  }
  document.addEventListener('click', e => {
    const a = e.composedPath().find(n => n && n.matches && n.matches('a[href],area[href]'));
    if (a && (!allowed(a.href) || (a.target && a.target !== '_self') || a.hasAttribute('download'))) {
      e.preventDefault(); e.stopImmediatePropagation(); refuse();
    }
  }, true);
  function formInfo(form, submitter) {
    const action = submitter && submitter.hasAttribute('formaction') ? submitter.getAttribute('formaction') : form.getAttribute('action');
    const method = ((submitter && submitter.getAttribute('formmethod')) || form.getAttribute('method') || 'get').toLowerCase();
    const target = (submitter && submitter.getAttribute('formtarget')) || form.getAttribute('target');
    let url;
    try { url = new URL(action || location.href, document.baseURI).href; } catch (_) { url = ''; }
    return {url, method, target};
  }
  function sendForm(form, submitter) {
    const info = formInfo(form, submitter);
    if (!isTop || !bridge || !allowed(info.url) || (info.target && info.target !== '_self') ||
        !['get','post'].includes(info.method) || semanticAncestor(form)) { refuse(); return; }
    sweep(document);
    const data = new FormData(form);
    if (submitter && submitter.name && !submitter.disabled) {
      if (submitter.type === 'image') {
        data.append(submitter.name + '.x', '0'); data.append(submitter.name + '.y', '0');
      } else data.append(submitter.name, submitter.value);
    }
    const fields = [];
    for (const [key,value] of data) {
      if (typeof value !== 'string') {
        // Empty file inputs are harmless; actual file uploads are outside this version.
        if (value.size || value.name) { refuse(); return; }
        fields.push([key,'']);
      } else fields.push([key,value.replace(/\r?\n|\r/g, '\r\n')]);
    }
    const enctype = (submitter && submitter.getAttribute('formenctype')) || form.getAttribute('enctype') || 'application/x-www-form-urlencoded';
    bridge.postMessage(JSON.stringify({kind:'form', url:info.url, method:info.method,
      enctype, fields, referer:location.href}));
  }
  document.addEventListener('submit', e => {
    const form = e.target;
    const info = formInfo(form, e.submitter);
    if (info.method === 'dialog') return;
    if (!allowed(info.url) || (info.target && info.target !== '_self') || semanticAncestor(form) || !isTop) {
      e.preventDefault(); e.stopImmediatePropagation(); refuse(); return;
    }
    // Cancel the browser's native submission; let SPA handlers cancel our mediated submission too.
    if (e.defaultPrevented) return;
    let pageCanceled = false;
    const nativePrevent = e.preventDefault.bind(e);
    nativePrevent();
    e.preventDefault = () => { pageCanceled = true; nativePrevent(); };
    setTimeout(() => { if (!pageCanceled) sendForm(form, e.submitter); }, 0);
  }, true);
  HTMLFormElement.prototype.submit = function() { sendForm(this, null); };
})();
