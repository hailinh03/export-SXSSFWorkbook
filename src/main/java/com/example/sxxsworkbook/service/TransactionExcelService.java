package com.example.sxxsworkbook.service;

import com.example.sxxsworkbook.entity.Transaction;
import com.example.sxxsworkbook.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Service xuất Excel bằng 2 cơ chế:
 *
 * 1. XSSFWorkbook  – load toàn bộ records vào RAM
 * 2. SXSSFWorkbook – streaming, chỉ giữ N rows trong RAM
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionExcelService {

    private final TransactionRepository transactionRepository;

    @Value("${app.excel.sxssf.row-access-window-size:100}")
    private int rowAccessWindowSize;

    // Header columns
    private static final String[] HEADERS = {
        "ID", "Reference No", "Type", "Status",
        "Sender Account", "Sender Name", "Sender Bank", "Sender Branch",
        "Receiver Account", "Receiver Name", "Receiver Bank", "Receiver Branch",
        "Amount", "Currency", "Fee", "Exchange Rate", "Amount In VND",
        "Channel", "Description", "Order ID", "Batch ID",
        "IP Address", "Device Info", "OTP Ref",
        "Error Code", "Error Message",
        "Retry Count", "Original Txn Ref",
        "Created By", "Created At", "Updated At", "Completed At"
    };

    // 1. XSSF Export – loads ALL rows into memory (❌ OOM with -Xmx16m)

    /**
     * Xuất Excel dùng XSSFWorkbook.
     * Toàn bộ records được load vào memory cùng lúc.
     */
    public byte[] exportWithXssf() throws IOException {
        log.warn("[XSSF] ⚠ Starting XSSFWorkbook export – ALL records loaded into memory!");
        log.warn("[XSSF] ⚠ This WILL cause OutOfMemoryError if JVM heap is limited (e.g., -Xmx16m)");

        // Load ALL transactions into memory at once (❌ nguy hiểm với heap nhỏ)
        List<Transaction> transactions = transactionRepository.findAll();
        log.info("[XSSF] Fetched {} records from DB", transactions.size());

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Transactions");

            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, HEADERS.length - 1));
            // Header styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle   = createDataStyle(workbook);

            // Tạo header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Tạo data rows – toàn bộ ở trong heap!
            int rowNum = 1;
            for (Transaction txn : transactions) {
                Row row = sheet.createRow(rowNum++);
                fillRow(row, txn, dataStyle);
            }

            log.info("[XSSF] Written {} data rows", rowNum - 1);

            // Auto-size columns (cũng tốn thêm RAM với XSSF)
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            log.info("[XSSF] Export complete. File size: {} bytes", out.size());
            return out.toByteArray();
        }
    }

    // 2. SXSSF Export – streaming, giữ N rows trong RAM
    /**
     * Xuất Excel dùng SXSSFWorkbook (Streaming XSSF).
     */
    @Transactional(readOnly = true)
    public byte[] exportWithSxssf() throws IOException {
        log.info("[SXSSF] ✅ Starting SXSSFWorkbook streaming export | window size = {} rows",
                rowAccessWindowSize);

        // SXSSFWorkbook: chỉ giữ rowAccessWindowSize rows trong RAM
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(rowAccessWindowSize)) {
            workbook.setCompressTempFiles(true);  // nén temp file trên disk

            Sheet sheet = workbook.createSheet("Transactions");
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, HEADERS.length - 1));
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle   = createDataStyle(workbook);

            // Header
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Stream records từ DB – không load hết vào memory
            AtomicInteger rowNum = new AtomicInteger(1);
            try (Stream<Transaction> stream = transactionRepository.streamAllOrderByCreatedAtDesc()) {
                    stream.forEach(txn -> {
                    Row row = sheet.createRow(rowNum.getAndIncrement());
                    fillRow(row, txn, dataStyle);

                    if (rowNum.get() % 1000 == 0) {
                        log.debug("[SXSSF] Processed {} rows...", rowNum.get());
                    }
                });
            }

            log.info("[SXSSF] Written {} data rows. Flushing to output stream...",
                    rowNum.get() - 1);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
          //  workbook.dispose();  // QUAN TRỌNG: xóa temp files trên disk

            log.info("[SXSSF] Export complete. File size: {} bytes", out.size());
            return out.toByteArray();
        }
    }

    // Helper: điền dữ liệu vào 1 row
    private void fillRow(Row row, Transaction txn, CellStyle style) {
        int col = 0;
        setCellValue(row, col++, txn.getId() != null ? txn.getId().toString() : "", style);
        setCellValue(row, col++, txn.getReferenceNumber(), style);
        setCellValue(row, col++, txn.getTransactionType() != null ? txn.getTransactionType().name() : "", style);
        setCellValue(row, col++, txn.getStatus() != null ? txn.getStatus().name() : "", style);
        // Sender
        setCellValue(row, col++, txn.getSenderAccountNo(), style);
        setCellValue(row, col++, txn.getSenderName(), style);
        setCellValue(row, col++, txn.getSenderBank(), style);
        setCellValue(row, col++, txn.getSenderBranch(), style);
        // Receiver
        setCellValue(row, col++, txn.getReceiverAccountNo(), style);
        setCellValue(row, col++, txn.getReceiverName(), style);
        setCellValue(row, col++, txn.getReceiverBank(), style);
        setCellValue(row, col++, txn.getReceiverBranch(), style);
        // Money
        setCellValue(row, col++, txn.getAmount() != null ? txn.getAmount().toPlainString() : "", style);
        setCellValue(row, col++, txn.getCurrency(), style);
        setCellValue(row, col++, txn.getFee() != null ? txn.getFee().toPlainString() : "", style);
        setCellValue(row, col++, txn.getExchangeRate() != null ? txn.getExchangeRate().toPlainString() : "", style);
        setCellValue(row, col++, txn.getAmountInVnd() != null ? txn.getAmountInVnd().toPlainString() : "", style);
        // Misc
        setCellValue(row, col++, txn.getChannel() != null ? txn.getChannel().name() : "", style);
        setCellValue(row, col++, txn.getDescription(), style);
        setCellValue(row, col++, txn.getOrderId(), style);
        setCellValue(row, col++, txn.getBatchId(), style);
        setCellValue(row, col++, txn.getIpAddress(), style);
        setCellValue(row, col++, txn.getDeviceInfo(), style);
        setCellValue(row, col++, txn.getOtpReference(), style);
        // Error
        setCellValue(row, col++, txn.getErrorCode(), style);
        setCellValue(row, col++, txn.getErrorMessage(), style);
        // Audit
        setCellValue(row, col++, txn.getRetryCount() != null ? txn.getRetryCount().toString() : "0", style);
        setCellValue(row, col++, txn.getOriginalTransactionRef(), style);
        setCellValue(row, col++, txn.getCreatedBy(), style);
        setCellValue(row, col++, txn.getCreatedAt() != null ? txn.getCreatedAt().toString() : "", style);
        setCellValue(row, col++, txn.getUpdatedAt() != null ? txn.getUpdatedAt().toString() : "", style);
        setCellValue(row, col,   txn.getCompletedAt() != null ? txn.getCompletedAt().toString() : "", style);
    }

    private void setCellValue(Row row, int colIndex, String value, CellStyle style) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    // Style helpers – XSSF & SXSSF đều dùng Workbook interface
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
