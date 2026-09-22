/**
 * Standalone generator for bank_confirmation.xlsx synthetic seed file.
 * Run with: java -cp poi-ooxml-5.2.5.jar:commons-collections4-4.4.jar generate_bank_xlsx.java
 *
 * Alternatively, the BankXLSXAdapter also accepts a CSV fallback (bank_confirmation.csv).
 * This file is provided for completeness; the application works with CSV during tests.
 */
import org.apache.poi.xssf.usermodel.*;
import java.io.FileOutputStream;

public class generate_bank_xlsx {
    public static void main(String[] args) throws Exception {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("BankConfirmation");

        String[] headers = {"CLIENT_ID","UTR_REFERENCE","DIRECTION","AMOUNT_PAISE","BANK_STATUS","VALUE_DATE","NARRATION"};
        XSSFRow hdr = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) hdr.createCell(i).setCellValue(headers[i]);

        Object[][] data = {
            {"CLI-001","UTR2024091601","CREDIT",500000000L,"CONFIRMED","2024-09-16","Inward NEFT"},
            {"CLI-001","UTR2024091602","DEBIT", 150000000L,"CONFIRMED","2024-09-16","Purchase settlement"},
            {"CLI-002","UTR2024091603","CREDIT",1000000000L,"CONFIRMED","2024-09-15","Opening transfer"},
            {"CLI-002","UTR2024091604","DEBIT", 200000000L,"CONFIRMED","2024-09-16","Purchase settlement"},
            {"CLI-002","UTR2024091605","CREDIT",50000000L, "CONFIRMED","2024-09-16","Dividend credit INFY"},
            {"CLI-003","UTR2024091606","CREDIT",300000000L,"CONFIRMED","2024-09-16","Inward transfer"},
            {"CLI-003","UTR2024091607","DEBIT", 100000000L,"CONFIRMED","2024-09-16","Purchase debit"},
            {"CLI-004","UTR2024091608","CREDIT",120000000L,"CONFIRMED","2024-09-16","Redemption proceeds"},
            {"CLI-004","UTR2024091609","DEBIT", 80000000L, "CONFIRMED","2024-09-16","Purchase debit"},
            // Duplicate UTR - intentional for CONFLICTING_EVIDENCE test
            {"CLI-004","UTR2024091610","CREDIT",30000000L, "PENDING",  "2024-09-16","Pending transfer"},
            {"CLI-004","UTR2024091610","CREDIT",30000000L, "PENDING",  "2024-09-16","Duplicate UTR same reference"},
        };

        for (int r = 0; r < data.length; r++) {
            XSSFRow row = sheet.createRow(r + 1);
            for (int c = 0; c < data[r].length; c++) {
                if (data[r][c] instanceof Long) row.createCell(c).setCellValue((Long) data[r][c]);
                else row.createCell(c).setCellValue(data[r][c].toString());
            }
        }
        try (FileOutputStream out = new FileOutputStream("bank_confirmation.xlsx")) {
            wb.write(out);
        }
        wb.close();
        System.out.println("Generated bank_confirmation.xlsx");
    }
}
