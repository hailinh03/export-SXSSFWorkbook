# 📊 XSSFWorkbook vs SXSSFWorkbook Demo — Oracle + Spring Boot

> **Mục đích**: Demo trực quan sự khác biệt giữa `XSSFWorkbook` và `SXSSFWorkbook` (Apache POI)
> khi xuất Excel với JVM bị giới hạn heap (`-Xmx256m`).

---

## 🏗️ Tech Stack

| Thành phần     | Chi tiết                                      |
|----------------|-----------------------------------------------|
| **Backend**    | Spring Boot 3.3.5, Java 21                    |
| **Database**   | Oracle 23ai Free (`oracle23:latest`)          |
| **ORM**        | Spring Data JPA + Hibernate (Oracle Dialect)  |
| **Excel**      | Apache POI 5.3.0 (`poi-ooxml`)                |
| **Email**      | Spring Boot Mail (Gmail SMTP + App Password)  |
| **API Docs**   | Springdoc OpenAPI 2.6.0 (Swagger UI)          |
| **Container**  | Docker + Docker Compose                       |

---

## 🚀 Khởi động nhanh

### 1. Start Oracle Database

```bash
# Ở thư mục gốc project
docker-compose up -d

# Kiểm tra logs Oracle (chờ ~2-3 phút để Oracle sẵn sàng)
docker logs -f oracle23-transaction-db
```

> **Lưu ý**: Oracle 23ai mất ~2-3 phút để khởi động lần đầu. Chờ thấy dòng:
> `DATABASE IS READY TO USE!`

---

### 2. Cấu hình Email (Environment Variables)

Project sử dụng **biến môi trường** để bảo mật thông tin email, không hardcode vào `application.yml`.

Tạo file `.env` ở thư mục gốc project:

```env
SPRING_MAIL_USERNAME=your-email@gmail.com
SPRING_MAIL_PASSWORD=xxxx xxxx xxxx xxxx
SPRING_MAIL_RECEIVED=recipient@gmail.com
```

`application.yml` sẽ đọc các biến này tự động:

```yaml
spring:
  mail:
    username: ${SPRING_MAIL_USERNAME}
    password: ${SPRING_MAIL_PASSWORD}

app:
  mail:
    default-recipient: ${SPRING_MAIL_RECEIVED}
```

> **Cách lấy App Password**: Google Account → Security → 2-Step Verification → App passwords
>
> **Lưu ý**: File `.env` đã được thêm vào `.gitignore`. **Không commit file này lên Git.**

---

### 3. Cấu hình IntelliJ IDEA với `-Xmx16m`

```
Run → Edit Configurations → VM Options:
-Xmx16m -Xms8m
```

> Đây là bước quan trọng để tái hiện lỗi `OutOfMemoryError` với `XSSFWorkbook`.

### 4. Chạy ứng dụng

```bash
./mvnw spring-boot:run
```

Hoặc trong IntelliJ: `Run SxxsworkbookApplication`

---

## 📡 API Endpoints

**Base URL**: `http://localhost:8080/api`  
**Swagger UI**: `http://localhost:8080/api/swagger-ui.html`

### CRUD

| Method   | Endpoint                          | Mô tả                          |
|----------|-----------------------------------|--------------------------------|
| `GET`    | `/transactions`                   | Lấy tất cả transactions        |
| `GET`    | `/transactions/{id}`              | Lấy transaction theo ID        |
| `GET`    | `/transactions/count`             | Đếm tổng số records            |
| `DELETE` | `/transactions/{id}`              | Xóa transaction theo ID        |

### Seed Data

```http
POST /api/transactions/seed?count=500
```

Tạo `N` records ngẫu nhiên để test xuất Excel (mặc định 500, tối đa 50.000).

---

### 🔴 API 1: Xuất Excel với XSSFWorkbook (OOM Demo)

```http
GET /api/transactions/export/xssf
GET /api/transactions/export/xssf?sendEmail=true
GET /api/transactions/export/xssf?sendEmail=true&toEmail=your@email.com
```

| Tham số     | Kiểu      | Mặc định | Mô tả                                       |
|-------------|-----------|----------|---------------------------------------------|
| `sendEmail` | `boolean` | `false`  | Gửi email kèm file sau khi xuất             |
| `toEmail`   | `String`  | *(trống)*| Email nhận – nếu trống dùng `default-recipient` trong config |

**⚠️ Kết quả dự kiến với `-Xmx16m`:**

```
java.lang.OutOfMemoryError: Java heap space
    at org.apache.poi.xssf.usermodel.XSSFWorkbook...
```

**Tại sao bị OOM?**

```
XSSFWorkbook giữ TOÀN BỘ workbook trong heap:
┌─────────────────────────────────────────────────┐
│  JVM Heap (giới hạn -Xmx16m = 16 MB)           │
│  ┌─────────────────────────────────────────┐    │
│  │  XSSFWorkbook object                    │    │
│  │  ├── Sheet[0]                           │    │
│  │  │   ├── Row[0..n] ← TẤT CẢ ROWS       │    │
│  │  │   │   ├── Cell[0..31]               │    │
│  │  │   │   └── Style, Font objects...    │    │
│  │  │   └── Column width metadata         │    │
│  │  └── Shared Strings Table              │    │
│  └─────────────────────────────────────────┘    │
│  💥 500 records × 32 cols × ~500B ≈ 8MB+       │
│     + Overhead = OutOfMemoryError!              │
└─────────────────────────────────────────────────┘
```

---

### 🟢 API 2: Xuất Excel với SXSSFWorkbook (Streaming - An toàn)

```http
GET /api/transactions/export/sxssf
GET /api/transactions/export/sxssf?sendEmail=true
GET /api/transactions/export/sxssf?sendEmail=true&toEmail=your@email.com
```

| Tham số     | Kiểu      | Mặc định | Mô tả                                       |
|-------------|-----------|----------|---------------------------------------------|
| `sendEmail` | `boolean` | `false`  | Gửi email kèm file sau khi xuất             |
| `toEmail`   | `String`  | *(trống)*| Email nhận – nếu trống dùng `default-recipient` trong config |

**✅ Kết quả: File Excel tải xuống thành công, không OOM!**

**Tại sao không bị OOM?**

```
SXSSFWorkbook dùng Sliding Window + Disk Temp File:
┌────────────────────────────────────────────────────┐
│  JVM Heap (chỉ 16MB)                              │
│  ┌─────────────────────────────────────┐           │
│  │  SXSSFWorkbook (window = 100 rows)  │           │
│  │  ├── Row[401..500] ← chỉ 100 rows  │           │  ← RAM
│  │  └── Row[0..400]  ← đã flush xuống │           │
│  └─────────────────────────────────────┘           │
└────────────────────────────────────────────────────┘
         ↓ flush khi vượt window
┌──────────────────────────────────┐
│  Disk Temp File (nén gzip)       │  ← Disk
│  Row[0..400] serialized...       │
└──────────────────────────────────┘
```

**Cơ chế SXSSFWorkbook trong code:**

```java
// Window size = 100: chỉ 100 rows cuối trong RAM
SXSSFWorkbook workbook = new SXSSFWorkbook(100);

// Nén temp file trên disk để tiết kiệm dung lượng
workbook.setCompressTempFiles(true);

// Stream từ DB thay vì findAll() → không load hết vào heap
try (Stream<Transaction> stream = repository.streamAllOrderByCreatedAtDesc()) {
    stream.forEach(txn -> {
        Row row = sheet.createRow(rowNum++);
        fillRow(row, txn, style);
        // Khi rowNum > 100 → row[0] tự động flush xuống disk
    });
}

// QUAN TRỌNG: dọn temp files sau khi xong
workbook.dispose();
```

---

## 🗄️ Database Schema

**Connection string:**

```
jdbc:oracle:thin:@//localhost:1521/FREEPDB1
Username: report_excel_user
Password: report_excel
```

```sql
-- Bảng TRANSACTIONS (tự tạo bởi Hibernate ddl-auto=update)
CREATE TABLE TRANSACTIONS (
    ID                      NUMBER          PRIMARY KEY,  -- Oracle Sequence
    REFERENCE_NUMBER        VARCHAR2(64)    UNIQUE NOT NULL,
    TRANSACTION_TYPE        VARCHAR2(20)    NOT NULL,  -- TRANSFER|PAYMENT|DEPOSIT|WITHDRAWAL|REFUND
    STATUS                  VARCHAR2(20)    NOT NULL,  -- PENDING|PROCESSING|SUCCESS|FAILED|CANCELLED|REVERSED

    -- Sender
    SENDER_ACCOUNT_NO       VARCHAR2(30)    NOT NULL,
    SENDER_NAME             VARCHAR2(100)   NOT NULL,
    SENDER_BANK             VARCHAR2(100),
    SENDER_BRANCH           VARCHAR2(100),

    -- Receiver
    RECEIVER_ACCOUNT_NO     VARCHAR2(30)    NOT NULL,
    RECEIVER_NAME           VARCHAR2(100)   NOT NULL,
    RECEIVER_BANK           VARCHAR2(100),
    RECEIVER_BRANCH         VARCHAR2(100),

    -- Money
    AMOUNT                  NUMBER(20,2)    NOT NULL,
    CURRENCY                VARCHAR2(3)     NOT NULL,  -- VND|USD|EUR
    FEE                     NUMBER(18,2),
    EXCHANGE_RATE           NUMBER(18,6),
    AMOUNT_IN_VND           NUMBER(20,2),

    -- Metadata
    DESCRIPTION             VARCHAR2(500),
    CHANNEL                 VARCHAR2(30),   -- MOBILE_APP|INTERNET_BANKING|ATM|COUNTER|API|BATCH
    IP_ADDRESS              VARCHAR2(45),
    DEVICE_INFO             VARCHAR2(200),
    OTP_REFERENCE           VARCHAR2(50),
    ORDER_ID                VARCHAR2(100),
    BATCH_ID                VARCHAR2(50),
    RETRY_COUNT             NUMBER          DEFAULT 0,
    ERROR_CODE              VARCHAR2(20),
    ERROR_MESSAGE           VARCHAR2(500),
    ORIGINAL_TRANSACTION_REF VARCHAR2(64),

    -- Timestamps
    CREATED_AT              TIMESTAMP       NOT NULL,
    UPDATED_AT              TIMESTAMP,
    COMPLETED_AT            TIMESTAMP,
    CREATED_BY              VARCHAR2(100)
);

-- Indexes (tự tạo bởi Hibernate)
CREATE INDEX idx_txn_ref_no        ON TRANSACTIONS(REFERENCE_NUMBER);
CREATE INDEX idx_txn_sender_acct   ON TRANSACTIONS(SENDER_ACCOUNT_NO);
CREATE INDEX idx_txn_receiver_acct ON TRANSACTIONS(RECEIVER_ACCOUNT_NO);
CREATE INDEX idx_txn_status        ON TRANSACTIONS(STATUS);
CREATE INDEX idx_txn_created_at    ON TRANSACTIONS(CREATED_AT);
```

---

## 📊 So sánh XSSF vs SXSSF

| Tiêu chí                    | XSSFWorkbook         | SXSSFWorkbook              |
|-----------------------------|----------------------|----------------------------|
| **Memory usage**            | Cao (toàn bộ vào RAM)| Thấp (window N rows)       |
| **OOM với -Xmx16m**         | ❌ Có                | ✅ Không                   |
| **Auto-size columns**       | ✅ Hỗ trợ            | ❌ Không hỗ trợ            |
| **Cell style access**       | ✅ Tất cả rows       | ⚠️ Chỉ rows trong window  |
| **Temp disk file**          | ❌ Không             | ✅ Có (có thể nén)         |
| **Phù hợp số records**      | < 10.000             | Hàng triệu                |
| **Streaming từ DB**         | ❌ Phải findAll()    | ✅ Dùng Stream\<T\>          |
| **workbook.dispose()**      | Không cần            | ✅ Bắt buộc (dọn temp)    |

---

## 🔧 Cấu hình Oracle Docker

```yaml
# docker-compose.yml
services:
  oracle-db:
    image: oracle23:latest           # Image Oracle 23ai Free local
    container_name: oracle23-transaction-db
    ports:
      - "1521:1521"                  # JDBC port
      - "5500:5500"                  # EM Express
    environment:
      ORACLE_PWD: "Oracle123#"       # SYS/SYSTEM password
      ORACLE_CHARACTERSET: "AL32UTF8"
    volumes:
      - oracle-data:/opt/oracle/oradata
      - ./oracle-init:/docker-entrypoint-initdb.d
```

> **Lưu ý**: PDB mặc định của Oracle 23ai Free là `FREEPDB1` (khác với `XEPDB1` của XE).

---

## 📧 Cấu hình Email

```yaml
# application.yml – đọc từ biến môi trường
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${SPRING_MAIL_USERNAME}
    password: ${SPRING_MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true
          connectiontimeout: 10000
          timeout: 60000
          writetimeout: 60000

app:
  mail:
    sender-name: "Transaction Export System"
    default-recipient: ${SPRING_MAIL_RECEIVED}
```

**Cách tạo Google App Password:**
1. Vào Google Account → Security
2. Bật 2-Step Verification (nếu chưa bật)
3. Tìm "App passwords" → Tạo mới
4. Copy 16 ký tự → dán vào file `.env`

---

## 📁 Cấu trúc Project

```
sxxsworkbook/
├── .env                               ← Biến môi trường (KHÔNG commit lên Git!)
├── docker-compose.yml
├── oracle-init/
│   └── 01_init.sql                    ← Tạo report_excel_user + sequence
├── pom.xml
└── src/
    └── main/
        ├── java/com/example/sxxsworkbook/
        │   ├── SxxsworkbookApplication.java
        │   ├── controller/
        │   │   └── TransactionController.java   ← 6 REST APIs
        │   ├── entity/
        │   │   └── Transaction.java             ← 32 trường chi tiết
        │   ├── enums/
        │   │   ├── TransactionType.java         ← TRANSFER|PAYMENT|DEPOSIT|...
        │   │   ├── TransactionStatus.java       ← PENDING|SUCCESS|FAILED|...
        │   │   └── Channel.java                 ← MOBILE_APP|ATM|API|...
        │   ├── repository/
        │   │   └── TransactionRepository.java   ← JPA + Stream<T>
        │   └── service/
        │       ├── TransactionService.java      ← CRUD + seed data
        │       ├── TransactionExcelService.java ← XSSF & SXSSF export
        │       └── EmailService.java            ← Gửi email + attachment
        └── resources/
            └── application.yml                  ← Oracle + Mail config
```

---

## 🧪 Test Flow đầy đủ

```bash
# 1. Start Oracle
docker-compose up -d

# 2. Tạo file .env với thông tin email
echo "SPRING_MAIL_USERNAME=your@gmail.com" > .env
echo "SPRING_MAIL_PASSWORD=xxxx xxxx xxxx xxxx" >> .env
echo "SPRING_MAIL_RECEIVED=recipient@gmail.com" >> .env

# 3. Chạy app với -Xmx16m trong IntelliJ
#    VM Options: -Xmx16m -Xms8m

# 4. Tạo 500 records test
curl -X POST "http://localhost:8080/api/transactions/seed?count=500"

# 5. Thử XSSF → Sẽ bị OOM (với -Xmx16m)
curl "http://localhost:8080/api/transactions/export/xssf" -o test_xssf.xlsx

# 6. Thử SXSSF → Thành công
curl "http://localhost:8080/api/transactions/export/sxssf" -o test_sxssf.xlsx

# 7. Xuất + gửi email (thay email của bạn)
curl "http://localhost:8080/api/transactions/export/sxssf?sendEmail=true&toEmail=your@email.com" -o report.xlsx
```

---

## 💡 Lưu ý quan trọng

> [!IMPORTANT]
> Nhớ gọi `workbook.dispose()` sau khi dùng `SXSSFWorkbook` để xóa temp files trên disk.
> Nếu không, các file temp sẽ tích lũy trong `/tmp/` của hệ thống.

> [!WARNING]
> `SXSSFWorkbook` **không hỗ trợ** `sheet.autoSizeColumn()` vì các rows cũ đã bị flush xuống disk.
> Cần set column width thủ công: `sheet.setColumnWidth(i, 5000)`.

> [!WARNING]
> **Không commit file `.env`** lên Git. File này chứa App Password Gmail của bạn.
> Kiểm tra `.gitignore` đã có entry cho `.env`.

> [!TIP]
> Với dataset lớn (> 100K records), kết hợp `SXSSFWorkbook` + `Stream<T>` từ JPA + `@Transactional(readOnly=true)`
> để tối ưu cả memory lẫn DB cursor.

---

*Transaction Export Demo | Spring Boot 3.3.5 + Apache POI 5.3.0 + Oracle 23ai Free*
