package com.example.sxxsworkbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo: XSSFWorkbook vs SXSSFWorkbook với Oracle Database
 *
 * Chạy với JVM flag: -Xmx16m để thấy lỗi OOM với XSSF
 * IntelliJ: Run → Edit Configurations → VM options → -Xmx16m
 *
 * Swagger UI: http://localhost:8080/api/swagger-ui.html
 */
@SpringBootApplication
public class SxxsworkbookApplication {

    public static void main(String[] args) {
        SpringApplication.run(SxxsworkbookApplication.class, args);
    }
}
