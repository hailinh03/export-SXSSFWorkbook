package com.example.sxxsworkbook.controller;

import com.example.sxxsworkbook.entity.Transaction;
import com.example.sxxsworkbook.service.EmailService;
import com.example.sxxsworkbook.service.TransactionExcelService;
import com.example.sxxsworkbook.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transactions", description = "Transaction management & Excel export APIs")
public class TransactionController {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final TransactionService      transactionService;
    private final TransactionExcelService excelService;
    private final EmailService            emailService;

    // CRUD APIs
    @GetMapping
    @Operation(summary = "Lấy tất cả transactions")
    public ResponseEntity<List<Transaction>> findAll() {
        return ResponseEntity.ok(transactionService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy transaction theo ID")
    public ResponseEntity<Transaction> findById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.findById(id));
    }

    @GetMapping("/count")
    @Operation(summary = "Đếm tổng số transactions trong DB")
    public ResponseEntity<Map<String, Long>> count() {
        return ResponseEntity.ok(Map.of("totalRecords", transactionService.count()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa transaction theo ID")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        transactionService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Seed Data API
    @PostMapping("/seed")
    @Operation(
        summary     = "Tạo dữ liệu mẫu ngẫu nhiên",
        description = "Tạo N records transaction ngẫu nhiên để test xuất Excel. "
    )
    public ResponseEntity<Map<String, Object>> seedData(
            @Parameter(description = "Số records muốn tạo (mặc định 500)")
            @RequestParam(defaultValue = "500") int count) {

        if (count < 1 || count > 50_000) {
            return ResponseEntity.badRequest().body(Map.of("error", "count phải trong khoảng 1 - 50000"));
        }

        long before = transactionService.count();
        int inserted = transactionService.seedData(count);
        long after   = transactionService.count();

        return ResponseEntity.ok(Map.of(
            "message",         "Seed data completed",
            "requested",       count,
            "inserted",        inserted,
            "totalBefore",     before,
            "totalAfter",      after
        ));
    }

    // EXCEL EXPORT APIs
    /**
     * API 1: Xuất Excel bằng XSSFWorkbook.
     */
    @GetMapping("/export/xssf")
    @Operation(
        summary     = "Xuất Excel – XSSFWorkbook (OOM Risk)"
    )
    public ResponseEntity<byte[]> exportXssf(
            @Parameter(description = "Gửi email kèm file Excel sau khi xuất?")
            @RequestParam(defaultValue = "false") boolean sendEmail,
            @Parameter(description = "Email nhận (để trống = dùng default trong config)")
            @RequestParam(required = false) String toEmail) {

        log.warn("▶ [API] /export/xssf called | sendEmail={} | toEmail={}", sendEmail, toEmail);

        try {
            byte[] excelBytes = excelService.exportWithXssf();

            String filename = "transactions_XSSF_" + LocalDateTime.now().format(TS_FMT) + ".xlsx";

            if (sendEmail) {
                sendEmailWithReport(toEmail, excelBytes, filename, "XSSFWorkbook");
            }

            return buildExcelResponse(excelBytes, filename);

        } catch (OutOfMemoryError oom) {
            // ❌ Đây là lỗi mong đợi khi dùng -Xmx16m
            log.error("💥 [XSSF] OutOfMemoryError! JVM heap exhausted. Consider using /export/sxssf instead.", oom);
            return ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"error\":\"OutOfMemoryError – XSSFWorkbook không thể xuất file khi heap quá nhỏ. " +
                           "Hãy dùng /export/sxssf để tránh lỗi này.\",\"hint\":\"SXSSFWorkbook streaming mode\"}").getBytes());

        } catch (IOException e) {
            log.error("[XSSF] IOException during export", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * API 2: Xuất Excel bằng SXSSFWorkbook.
     */
    @GetMapping("/export/sxssf")
    @Operation(
        summary     = "Xuất Excel – SXSSFWorkbook (Streaming, OOM-Safe)",
        description = """
            Xuất toàn bộ transactions ra file Excel dùng **SXSSFWorkbook** (Streaming XSSF).
            """
    )
    public ResponseEntity<byte[]> exportSxssf(
            @Parameter(description = "Gửi email kèm file Excel sau khi xuất?")
            @RequestParam(defaultValue = "false") boolean sendEmail,
            @Parameter(description = "Email nhận (để trống = dùng default trong config)")
            @RequestParam(required = false) String toEmail) {

        log.info("▶ [API] /export/sxssf called | sendEmail={} | toEmail={}", sendEmail, toEmail);

        try {
            byte[] excelBytes = excelService.exportWithSxssf();

            String filename = "transactions_SXSSF_" + LocalDateTime.now().format(TS_FMT) + ".xlsx";

            if (sendEmail) {
                sendEmailWithReport(toEmail, excelBytes, filename, "SXSSFWorkbook");
            }

            return buildExcelResponse(excelBytes, filename);

        } catch (IOException e) {
            log.error("[SXSSF] IOException during export", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Private helpers
    private ResponseEntity<byte[]> buildExcelResponse(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private void sendEmailWithReport(String toEmail, byte[] excelBytes, String filename, String workbookType) {
        try {
            String subject  = "[Transaction Report] " + workbookType + " Export – " + LocalDateTime.now().format(TS_FMT);
            String htmlBody = buildEmailHtml(workbookType, filename, excelBytes.length);

            emailService.sendExcelReport(toEmail, subject, htmlBody, excelBytes, filename, workbookType);
            log.info("Email sent with attachment: {}", filename);

        } catch (jakarta.mail.MessagingException | java.io.UnsupportedEncodingException e) {
            // Không throw để không block download; chỉ log
            log.error("Failed to send email (export still succeeded): {}", e.getMessage());
        }
    }

    private String buildEmailHtml(String workbookType, String filename, int fileSizeBytes) {
        double fileSizeKb = fileSizeBytes / 1024.0;
        return """
            <html>
            <body style="font-family: Arial, sans-serif; color: #333;">
                <h2 style="color: #1a73e8;">Transaction Export Report</h2>
                <table style="border-collapse: collapse; width: 100%%;">
                    <tr>
                        <td style="padding: 8px; font-weight: bold;">Workbook Type</td>
                        <td style="padding: 8px;">%s</td>
                    </tr>
                    <tr style="background:#f5f5f5;">
                        <td style="padding: 8px; font-weight: bold;">File Name</td>
                        <td style="padding: 8px;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 8px; font-weight: bold;">File Size</td>
                        <td style="padding: 8px;">%.2f KB</td>
                    </tr>
                    <tr style="background:#f5f5f5;">
                        <td style="padding: 8px; font-weight: bold;">Export Time</td>
                        <td style="padding: 8px;">%s</td>
                    </tr>
                </table>
                <hr/>
                <p style="color: #666; font-size: 12px;">
                    Gửi tự động bởi Transaction Export System.<br/>
                    File đính kèm chứa dữ liệu transactions được xuất bởi <strong>%s</strong>.
                </p>
            </body>
            </html>
            """.formatted(
                workbookType, filename, fileSizeKb,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")),
                workbookType
        );
    }
}
