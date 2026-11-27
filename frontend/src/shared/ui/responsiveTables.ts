/**
 * Phones show every `table.table-ui` as one card per row (see index.css). Each cell needs
 * the name of its column for that, so this copies the header text into `data-label` on
 * every cell. It runs once and again whenever the page adds rows, so pages need no change.
 */
export function labelTableCells(root: ParentNode = document) {
  root.querySelectorAll<HTMLTableElement>("table.table-ui").forEach(table => {
    const heads = Array.from(table.querySelectorAll("thead th")).map(th => th.textContent?.trim() ?? "");
    table.querySelectorAll<HTMLTableRowElement>("tbody tr").forEach(row => {
      let column = 0;
      for (const cell of Array.from(row.cells)) {
        const label = heads[column] ?? "";
        if (cell.getAttribute("data-label") !== label) cell.setAttribute("data-label", label);
        column += cell.colSpan || 1;
      }
    });
  });
}

/** Watches the page for new tables or rows; returns a function that stops watching. */
export function watchResponsiveTables(): () => void {
  let queued = false;
  const run = () => { queued = false; labelTableCells(); };
  // Only child-list changes are observed, so setting the attributes cannot retrigger it.
  const observer = new MutationObserver(() => {
    if (!queued) { queued = true; requestAnimationFrame(run); }
  });
  observer.observe(document.body, { childList: true, subtree: true });
  run();
  return () => observer.disconnect();
}
