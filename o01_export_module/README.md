O01 - Xuất kho (Draft flow)

Files included:
- service/ExportIssueService.java
- view/export/ExportListView.java
- view/export/export-list-view.xml
- view/export/ExportDetailView.java
- view/export/export-detail-view.xml
- view/transactionitem/TransactionItemDetailView.java
- view/transactionitem/transaction-item-detail-view.xml

What this implements:
- Export list filtered to WarehouseTransaction.type = EXPORT.
- Create/edit/read export draft.
- Auto-generate document number EXP-YYYYMMDD-XXXXXXXX.
- Defaults: type=EXPORT, status=DRAFT, documentDate=today.
- Nested TransactionItem composition.
- UI required validation.
- Quantity: BigDecimal, positive, max 16 integer digits + 3 decimals.
- Server-side business validation: warehouse/partner/product/unit active, export partner type CUSTOMER/BOTH, at least one item, no duplicate product, quantity > 0, max lengths.
- No inventory update and no POST operation yet. This is intentional for O01; POST/Inventory will be wired in the next step after agreeing the Inventory/Movement contract with Person 4.

Important:
The files were generated from the code and Jmix View patterns you shared. They were not compiled inside your local D:\lab211\jmix-warehouse-management checkout, so after copying them into your repo, run `./gradlew clean build` and fix any project-specific naming differences (especially Unit/Partner fields) if your local files differ.
