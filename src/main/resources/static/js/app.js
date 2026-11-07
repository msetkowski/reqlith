// Global behaviour shared by every page: CSRF for HTMX requests and Mermaid rendering.
(function () {
  "use strict";

  // Send the Spring Security CSRF token with every HTMX request.
  document.addEventListener("htmx:configRequest", function (event) {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) {
      event.detail.headers[header.content] = token.content;
    }
  });

  var mermaidReady = false;
  function initMermaid() {
    if (mermaidReady || !window.mermaid) return !!window.mermaid;
    window.mermaid.initialize({
      startOnLoad: false,
      securityLevel: "strict",
      theme: "neutral",
      fontFamily: "IBM Plex Sans, system-ui, sans-serif"
    });
    mermaidReady = true;
    return true;
  }

  // Renders every not-yet-rendered <pre class="mermaid"> below root.
  // Server-side markdown emits mermaid fences as <pre class="mermaid">source</pre>.
  function renderMermaid(root) {
    var nodes = (root || document).querySelectorAll("pre.mermaid:not([data-processed])");
    if (!nodes.length || !initMermaid()) return Promise.resolve();
    return window.mermaid.run({ nodes: nodes, suppressErrors: true });
  }
  window.reqlith = { renderMermaid: renderMermaid };

  document.addEventListener("DOMContentLoaded", function () {
    renderMermaid(document).finally(function () { window.mermaidDone = true; });
  });
  document.addEventListener("htmx:afterSettle", function (event) {
    renderMermaid(event.detail.elt);
  });
})();
