// Markdown editor island: toolbar actions on the textarea marked with data-md-editor.
// The live preview itself is server rendered (HTMX), so it matches the read view exactly.
(function () {
  "use strict";

  var MERMAID_SNIPPET = "```mermaid\nflowchart LR\n  A[Start] --> B{Decision}\n  B -- yes --> C[Do it]\n  B -- no --> D[Stop]\n```\n";

  function surround(area, before, after, placeholder) {
    var start = area.selectionStart, end = area.selectionEnd;
    var selected = area.value.slice(start, end) || placeholder;
    area.setRangeText(before + selected + after, start, end, "end");
    area.selectionStart = start + before.length;
    area.selectionEnd = start + before.length + selected.length;
  }

  function insertBlock(area, text) {
    var start = area.selectionStart;
    var prefix = start > 0 && area.value[start - 1] !== "\n" ? "\n\n" : "";
    area.setRangeText(prefix + text, start, area.selectionEnd, "end");
  }

  function headingAtLine(area) {
    var start = area.value.lastIndexOf("\n", area.selectionStart - 1) + 1;
    area.setRangeText("## ", start, start, "end");
  }

  function changed(area) {
    area.focus();
    area.dispatchEvent(new Event("input", { bubbles: true }));
  }

  document.addEventListener("click", function (event) {
    var button = event.target.closest("[data-md]");
    if (!button) return;
    var area = document.querySelector("[data-md-editor]");
    if (!area) return;
    event.preventDefault();
    switch (button.getAttribute("data-md")) {
      case "heading": headingAtLine(area); break;
      case "bold": surround(area, "**", "**", "bold text"); break;
      case "italic": surround(area, "_", "_", "italic text"); break;
      case "mermaid": insertBlock(area, MERMAID_SNIPPET); break;
    }
    changed(area);
  });

  // Picking an element in the "+ Reference" select inserts `KEY` at the cursor.
  document.addEventListener("change", function (event) {
    var select = event.target.closest("[data-md-reference]");
    if (!select || !select.value) return;
    var area = document.querySelector("[data-md-editor]");
    area.setRangeText("`" + select.value + "`", area.selectionStart, area.selectionEnd, "end");
    select.value = "";
    changed(area);
  });

  // Warn before leaving with unsaved changes.
  var dirty = false;
  document.addEventListener("input", function (event) {
    if (event.target.closest("form[data-guard]")) dirty = true;
  });
  document.addEventListener("submit", function () { dirty = false; });
  window.addEventListener("beforeunload", function (event) {
    if (dirty) { event.preventDefault(); event.returnValue = ""; }
  });
})();
