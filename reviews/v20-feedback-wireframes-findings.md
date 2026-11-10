# v20-feedback-wireframes — Astra findings

review: calls=1 (1 full + ≤ 1 delta) effort=xhigh size=8.9 KB · verdict `concerns`, coverage full ·
input `reviews/v20-feedback-wireframes-astra-input.md`, verdict `reviews/v20-feedback-wireframes-astra-call1.txt`.
Wireframe: `design/2026-11-10-v20-feedback-wireframes.html`.

| ID | Grade | Conf. | Where | What | Fix | Closed by | Status |
|---|---|---|---|---|---|---|---|
| C1-1 | concerns | 99 | Item 1 | Two rows of 15 + 4 + 8 dp overflow 52 dp | none: the wireframe's row is name line 15 + 1 + bar 8 = 24, and 1 + 24 + 2 + 24 + 1 = 52; the review input mis-summarised the gap as 4 | wireframe §1 text | disputed |
| C1-2 | concerns | 97 | Item 7 leaders | "Nothing free: the right-hand spot stands" brings back a rejected, colliding spot | with nothing free the name is not drawn; the dot stays and still pins a callout that names it; the map-label fixtures include a dense 200 dp snapshot map | build: core `mapLabels` fixture | fixed (wireframe §7) |
| C1-3 | concerns | 94 | Item 7 callout | No callout state when there is no projection | none: §7 already says "no third line without a projection", and the spent callout is drawn | wireframe §7 text and drawing | disputed |
| C1-4 | concerns | 100 | Item 1 narrow | Two drawings give conflicting name-versus-tail drop orders | one order everywhere: the bar absorbs, the name ellipsizes to its 24 dp floor, then the state word drops its tail; the second drawing is now at 226 dp | wireframe §1 drawings and width table | fixed (wireframe §1) |
