# Master Data CSV / Excel Import

The Product Categories, Units, Products, Warehouses and Partners list views now have an **Import CSV / Excel** action. Accepted formats: `.csv` (UTF-8, comma-delimited) and `.xlsx` (first worksheet, first row is header). Max upload size is 15 MiB.

## Duplicate handling
- **Bỏ qua bản ghi trùng** (default): existing `code` is skipped.
- **Cập nhật bản ghi hiện có**: update the row matching `code`.

Each row is validated independently. The result dialog reports created/updated/skipped counts and the spreadsheet row number plus validation message for failed rows. For Products, `categoryCode` and `baseUnitCode` must match existing category/unit codes.

## Headers (case-insensitive; spaces/underscores ignored)
- ProductCategory: `code,name,description,active`
- Unit: `code,name,decimalScale,active`
- Product: `code,name,categoryCode,baseUnitCode,active`
- Warehouse: `code,name,address,active`
- Partner: `code,name,partnerType,email,active` (`partnerType`: `SUPPLIER`, `CUSTOMER`, `BOTH`)

`active` is optional and defaults to true; accepted values include true/false, 1/0, yes/no, active/inactive. Required fields: code and name; Products additionally require categoryCode and baseUnitCode.


Lưu ý kỹ thuật: bộ đọc CSV loại bỏ ký tự BOM UTF-8 ở đầu tiêu đề cột để file CSV do Excel lưu không làm mất nhận diện cột đầu tiên (ví dụ `code`).
