package com.company.warehousemanagement.service;

import com.company.warehousemanagement.entity.*;
import io.jmix.core.DataManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Imports CSV and XLSX master-data files. Headers are case-insensitive; first row is the header. */
@Component
public class MasterDataImportService {
    public enum DuplicateMode { SKIP, UPDATE, REPLACE_ALL }
    public record RowError(int row, String message) {}
    public record ImportResult(int created, int updated, int skipped, List<RowError> errors) {}
    private final DataManager dataManager;
    public MasterDataImportService(DataManager dataManager) { this.dataManager = dataManager; }

    public List<Map<String,String>> read(InputStream input, String filename) throws IOException {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) return readCsv(input);
        if (lower.endsWith(".xlsx")) return readXlsx(input);
        throw new IllegalArgumentException("Chỉ hỗ trợ file .csv hoặc .xlsx");
    }
    private List<Map<String,String>> readCsv(InputStream in) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) rows.add(parseCsvLine(line));
        }
        return maps(rows);
    }
    private List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted=false;
        for (int i=0;i<line.length();i++) { char c=line.charAt(i);
            if (c=='"' && quoted && i+1<line.length() && line.charAt(i+1)=='"') { cell.append('"'); i++; }
            else if (c=='"') quoted=!quoted;
            else if (c==',' && !quoted) { cells.add(cell.toString().trim()); cell.setLength(0); }
            else cell.append(c);
        }
        cells.add(cell.toString().trim()); return cells;
    }
    private List<Map<String,String>> readXlsx(InputStream in) throws IOException {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet=wb.getSheetAt(0); List<List<String>> rows=new ArrayList<>(); DataFormatter fmt=new DataFormatter();
            for (Row row:sheet) { List<String> cells=new ArrayList<>(); int n=row.getLastCellNum(); for(int i=0;i<Math.max(0,n);i++) cells.add(fmt.formatCellValue(row.getCell(i)).trim()); rows.add(cells); }
            return maps(rows);
        }
    }
    private List<Map<String,String>> maps(List<List<String>> rows) {
        if(rows.isEmpty()) throw new IllegalArgumentException("File không có dữ liệu");
        List<String> headers=rows.get(0).stream()
                .map(s -> s.replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT)
                        .replace(" ", "").replace("_", ""))
                .toList();
        List<Map<String,String>> out=new ArrayList<>();
        for(int r=1;r<rows.size();r++) { List<String> vals=rows.get(r); if(vals.stream().allMatch(String::isBlank)) continue; Map<String,String> m=new HashMap<>(); for(int c=0;c<headers.size();c++) m.put(headers.get(c), c<vals.size()?vals.get(c).trim():""); m.put("__row",String.valueOf(r+1)); out.add(m); }
        return out;
    }
    private String v(Map<String,String> m,String key) { return m.getOrDefault(key.toLowerCase(Locale.ROOT).replace(" ","").replace("_", ""), "").trim(); }
    private boolean active(Map<String,String> m) { String a=v(m,"active"); if(a.isBlank()) return true; if(List.of("true","1","yes","y","active","hoạt động").contains(a.toLowerCase(Locale.ROOT))) return true; if(List.of("false","0","no","n","inactive","ngừng hoạt động").contains(a.toLowerCase(Locale.ROOT))) return false; throw new IllegalArgumentException("active phải là true/false, 1/0, yes/no"); }
    private void required(String value,String field) { if(value==null||value.isBlank()) throw new IllegalArgumentException("Thiếu trường bắt buộc: "+field); }
    private String normalizeType(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").trim();
    }

    /** Reject a file created for another master-data screen before changing any data. */
    private void validateFileType(String type, List<Map<String,String>> rows) {
        if (rows == null || rows.isEmpty()) throw new IllegalArgumentException("File không có dòng dữ liệu.");
        Set<String> headers = new HashSet<>(rows.get(0).keySet());
        headers.remove("__row");
        String normalized = normalizeType(type);
        Set<String> requiredHeaders = switch (normalized) {
            case "productcategory", "productcategorys", "category", "categories" -> Set.of("code", "name", "description");
            case "unit", "units" -> Set.of("code", "name", "decimalscale");
            case "product", "products" -> Set.of("code", "name", "categorycode", "baseunitcode");
            case "warehouse", "warehouses" -> Set.of("code", "name", "address");
            case "partner", "partners" -> Set.of("code", "name", "partnertype");
            default -> throw new IllegalArgumentException("Loại master data không được hỗ trợ: " + type);
        };
        if (!headers.containsAll(requiredHeaders)) {
            Set<String> missing = new TreeSet<>(requiredHeaders);
            missing.removeAll(headers);
            throw new IllegalArgumentException("File không đúng mẫu của " + type + ". Thiếu cột nhận diện: " + String.join(", ", missing));
        }
    }

    @Transactional
    public ImportResult importRows(String type,List<Map<String,String>> rows,DuplicateMode mode) {
        validateFileType(type, rows);
        if (mode == DuplicateMode.REPLACE_ALL) {
            // Delete and re-import inside this transaction. Any row error or FK constraint
            // failure aborts the operation, so Spring rolls the deletion back as well.
            clearType(type);
        }
        int created=0,updated=0,skipped=0; List<RowError> errors=new ArrayList<>(); int i=0;
        for(Map<String,String> m:rows) { i++; int row; try { row=Integer.parseInt(m.getOrDefault("__row",String.valueOf(i+1))); } catch(Exception e){row=i+1;}
            try {
                String code=v(m,"code"), name=v(m,"name"); required(code,"code"); required(name,"name");
                switch(type.toLowerCase(Locale.ROOT)) {
                    case "productcategory", "productcategorys", "category" -> { ProductCategory old=dataManager.load(ProductCategory.class).query("select e from ProductCategory e where e.code = :code").parameter("code",code).optional().orElse(null); if(old!=null&&mode==DuplicateMode.SKIP){skipped++;break;} ProductCategory e=old==null?dataManager.create(ProductCategory.class):old; e.setCode(code);e.setName(name);e.setDescription(v(m,"description"));e.setActive(active(m));dataManager.save(e);if(old==null)created++;else updated++; }
                    case "unit", "units" -> { Unit old=dataManager.load(Unit.class).query("select e from Unit e where e.code = :code").parameter("code",code).optional().orElse(null); if(old!=null&&mode==DuplicateMode.SKIP){skipped++;break;} Unit e=old==null?dataManager.create(Unit.class):old; e.setCode(code);e.setName(name);String ds=v(m,"decimalscale");if(!ds.isBlank()){int scale=Integer.parseInt(ds);if(scale<0||scale>10)throw new IllegalArgumentException("decimalScale phải từ 0 đến 10");e.setDecimalScale(scale);}e.setActive(active(m));dataManager.save(e);if(old==null)created++;else updated++; }
                    case "warehouse", "warehouses" -> { Warehouse old=dataManager.load(Warehouse.class).query("select e from Warehouse e where e.code = :code").parameter("code",code).optional().orElse(null); if(old!=null&&mode==DuplicateMode.SKIP){skipped++;break;} Warehouse e=old==null?dataManager.create(Warehouse.class):old;e.setCode(code);e.setName(name);e.setAddress(v(m,"address"));e.setActive(active(m));dataManager.save(e);if(old==null)created++;else updated++; }
                    case "partner", "partners" -> { Partner old=dataManager.load(Partner.class).query("select e from Partner e where e.code = :code").parameter("code",code).optional().orElse(null); if(old!=null&&mode==DuplicateMode.SKIP){skipped++;break;} Partner e=old==null?dataManager.create(Partner.class):old;e.setCode(code);e.setName(name);String t=v(m,"partnertype").toUpperCase(Locale.ROOT);required(t,"partnerType (SUPPLIER/CUSTOMER/BOTH)");PartnerType pt=PartnerType.fromId(t);if(pt==null)throw new IllegalArgumentException("partnerType phải là SUPPLIER, CUSTOMER hoặc BOTH");e.setPartnerType(pt);e.setEmail(v(m,"email").isBlank()?null:v(m,"email"));e.setActive(active(m));dataManager.save(e);if(old==null)created++;else updated++; }
                    case "product", "products" -> { Product old=dataManager.load(Product.class).query("select e from Product e where e.code = :code").parameter("code",code).optional().orElse(null); if(old!=null&&mode==DuplicateMode.SKIP){skipped++;break;} String cat=v(m,"categorycode"),unit=v(m,"baseunitcode");required(cat,"categoryCode");required(unit,"baseUnitCode");ProductCategory category=dataManager.load(ProductCategory.class).query("select e from ProductCategory e where e.code = :code").parameter("code",cat).optional().orElseThrow(()->new IllegalArgumentException("Không tìm thấy categoryCode: "+cat));Unit baseUnit=dataManager.load(Unit.class).query("select e from Unit e where e.code = :code").parameter("code",unit).optional().orElseThrow(()->new IllegalArgumentException("Không tìm thấy baseUnitCode: "+unit));Product e=old==null?dataManager.create(Product.class):old;e.setCode(code);e.setName(name);e.setCategory(category);e.setBaseUnit(baseUnit);e.setActive(active(m));dataManager.save(e);if(old==null)created++;else updated++; }
                    default -> throw new IllegalArgumentException("Loại master data không được hỗ trợ: "+type);
                }
            } catch(Exception ex) { errors.add(new RowError(row, ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage())); }
        }
        if (mode == DuplicateMode.REPLACE_ALL && !errors.isEmpty()) {
            RowError first = errors.get(0);
            throw new IllegalArgumentException("Không thể thay thế toàn bộ dữ liệu. Dòng " + first.row() + ": " + first.message() + ". Toàn bộ thao tác đã bị hủy; dữ liệu cũ được giữ nguyên.");
        }
        return new ImportResult(created,updated,skipped,List.copyOf(errors));
    }

    private void clearType(String type) {
        switch (normalizeType(type)) {
            case "productcategory", "productcategorys", "category", "categories" ->
                    dataManager.load(ProductCategory.class).all().list().forEach(dataManager::remove);
            case "unit", "units" ->
                    dataManager.load(Unit.class).all().list().forEach(dataManager::remove);
            case "product", "products" ->
                    dataManager.load(Product.class).all().list().forEach(dataManager::remove);
            case "warehouse", "warehouses" ->
                    dataManager.load(Warehouse.class).all().list().forEach(dataManager::remove);
            case "partner", "partners" ->
                    dataManager.load(Partner.class).all().list().forEach(dataManager::remove);
            default -> throw new IllegalArgumentException("Loại master data không được hỗ trợ: " + type);
        }
    }
}
