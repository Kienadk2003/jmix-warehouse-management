# V16 UI Fix Notes (based on V15)

## Changes
- Removed the default `genericFilter` blocks from the requested list views where the main search toolbar is used, including `/warehouses` and the import/export and stocktake/adjustment composite view.
- Kept the existing search fields and their query-backed filtering for warehouse, inventory, product, category, unit, partner, import/export, transfer, stocktake/adjustment composite, pending approvals, users, employee assignment, user-to-warehouse assignment, and user-to-product-category assignment.
- Added query-backed search fields to the standalone `/stocktakes` and `/adjustments` list views, using existing entity properties (`documentNo`, warehouse name/code, and adjustment reason/source warehouse).
- Added Vietnamese and English search placeholder messages for the standalone stocktake and adjustment views.
- Removed custom `vaadin-details-summary::before` SVG group icons from both Aura and Lumo main-view CSS. The menu already declares icons in `menu.xml`; using both caused duplicate icons. The menu XML icon declarations and routes remain unchanged.

## Validation performed
- Parsed all modified view XML files and `menu.xml` successfully with an XML parser.
- Confirmed no `genericFilter` remains in the requested list-view XML files.
- Confirmed no custom menu `summary::before` SVG icon rules remain in the Aura/Lumo main-view CSS.
- Could not run Gradle compilation: the wrapper attempted to download Gradle 9.5.1 from services.gradle.org, but this environment has no network access (`UnknownHostException`).
- Application runtime and visual checks must still be performed locally.

## Scope notes
- No entity, database, service, security, role, approval workflow, or dashboard logic was intentionally changed.
- ZIP source does not contain Git metadata, so `git status`/`git diff` could not be run against this archive.
