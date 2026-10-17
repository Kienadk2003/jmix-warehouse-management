# KhoFlow UI/UX patch report

## Source updated
Based on the uploaded `jmix-warehouse-management-v17-header-notifications-fixed(1).zip`. Existing entities, repositories, transaction/posting services, database changelog, and security role definitions were not intentionally changed.

## Changes applied
- Removed the explicit `vaadin:search` XML prefix icon from all 16 list/search XML screens. The existing Aura theme's single CSS search glyph remains; adjusted input text padding so the placeholder/value starts after the glyph.
- Added shared empty-state and pagination styling to the existing Aura theme.
- Transfer list: localized title/actions/column headings, added a localized empty/search-no-results state, and placed the existing `simplePagination` in a consistent footer row. Existing loader query and CRUD actions remain intact.
- Transfer detail: moved visible labels, item-grid headers, validation text and action captions to the message bundle; added English and Vietnamese translations.
- Pending approvals: added an icon to the empty state, and the “Open document” action now remains disabled until a row is selected. Fixed the no-results condition so a non-empty search term displays the search-empty message even when there are zero total rows.
- Localized status labels in transfer, export, adjustment, import receipt and stocktake detail screens using the existing Jmix message system. Added English/Vietnamese stocktake status labels without changing enum IDs or database values.
- Main dashboard labels now resolve from the existing message bundles in English and Vietnamese.
- Language selection is applied to the active UI and stored on the Vaadin session so the choice survives navigation/reloads within that session.

## Validation
- Parsed all 50 XML files successfully with an XML parser.
- Properties files contain duplicate keys that already existed in the uploaded project (Partner.email, several User keys and UserProductCategory keys); this patch did not remove them because that would broaden the change beyond the requested UI work.
- `./gradlew compileJava --no-daemon` could not start because the Gradle wrapper distribution `gradle-9.5.1-bin.zip` could not be downloaded in this environment (`UnknownHostException: services.gradle.org`). Compilation and live visual testing therefore remain unverified.

## Remaining coverage limits
This is a targeted UI/i18n patch, not a guarantee that every hard-coded string in every screen is translated. Some legacy list/detail views still contain literal strings and need a screen-by-screen pass with runtime verification. No screenshot-based browser validation was possible here. Review the changed project in a separate folder and run `gradlew.bat compileJava` before replacing your working copy.


### Bản sửa bổ sung v2 (16/10/2026)
- Sửa lỗi biên dịch trong `TransferListView.java`: bổ sung import `io.jmix.flowui.view.Subscribe.Target` cho annotation `@Subscribe(... target = Target.DATA_LOADER)`. Không thay đổi logic tìm kiếm, loader hoặc nghiệp vụ chuyển kho.
- Chưa thể xác nhận build trong môi trường tạo bản vá; cần chạy `gradlew.bat compileJava` tại máy phát triển.
