# Warehouse Management UI – V8 Figma alignment

This update is focused on presentation and navigation configuration. The existing dashboard markup/controller, entity mappings, services, authorization checks, and business operations were intentionally left unchanged.

## UI changes
- Replaced the visible default Jmix Generic Filter cards with compact search-field controls placed in the list action toolbar.
- Added a consistent search icon, input shape, spacing, toolbar buttons, table headers, form surfaces, and mint/teal color palette.
- Added Vaadin icons to navigation groups and items.
- Disabled composite menu merging so framework-provided default menu groups do not get appended to the custom warehouse menu.
- Kept the existing single-button Vietnamese/English locale toggle next to the user menu.
- Added localized search/empty-state message keys for Vietnamese and English.

## Important validation note
The source XML was checked for well-formedness. A Gradle compile could not be run in this environment because the Gradle distribution could not be downloaded (network DNS unavailable). Please run `gradlew.bat compileJava` in Windows/IntelliJ before deployment.
