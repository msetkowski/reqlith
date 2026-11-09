// Global behaviour shared by every page: CSRF for HTMX requests, Mermaid rendering and the sticky project header.
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

  // <button data-copy="text"> copies its text to the clipboard.
  document.addEventListener("click", function (event) {
    var button = event.target.closest("[data-copy]");
    if (!button || !navigator.clipboard) return;
    navigator.clipboard.writeText(button.getAttribute("data-copy")).then(function () {
      var label = button.textContent;
      button.textContent = "Copied";
      setTimeout(function () { button.textContent = label; }, 1500);
    });
  });

  // The theme picked in the account menu (data-theme on <html>), else the OS preference.
  function darkTheme() {
    var theme = document.documentElement.getAttribute("data-theme");
    if (theme) return theme === "dark";
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
  }

  var mermaidReady = false;
  function initMermaid() {
    if (mermaidReady || !window.mermaid) return !!window.mermaid;
    window.mermaid.initialize({
      startOnLoad: false,
      securityLevel: "strict",
      theme: darkTheme() ? "dark" : "neutral",
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

  // On project pages the header is sticky (except on narrow screens); --header-h tells the side navigation
  // and anchor targets how much room it takes (it grows when the breadcrumbs wrap).
  function trackHeaderHeight() {
    var header = document.querySelector(".app-header");
    if (!header || !document.querySelector(".workbench")) return;
    var update = function () {
      var sticky = getComputedStyle(header).position === "sticky";
      document.documentElement.style.setProperty("--header-h", (sticky ? header.offsetHeight : 0) + "px");
    };
    update();
    if (window.ResizeObserver) new ResizeObserver(update).observe(header);
  }

  document.addEventListener("DOMContentLoaded", function () {
    trackHeaderHeight();
    renderMermaid(document).finally(function () { window.mermaidDone = true; });
  });
  document.addEventListener("htmx:afterSettle", function (event) {
    renderMermaid(event.detail.elt);
  });
})();
