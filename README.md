# 📊 XSSFWorkbook vs SXSSFWorkbook – Demo Project

Dự án Spring Boot minh họa sự khác biệt về **quản lý bộ nhớ** giữa `XSSFWorkbook` và `SXSSFWorkbook` (Apache POI) khi xuất file Excel từ cơ sở dữ liệu Oracle với số lượng bản ghi lớn.

---

## 🧰 Tech Stack

| Thành phần       | Công nghệ                                      |
|------------------|------------------------------------------------|
| Framework        | Spring Boot 4.0.6                              |
| Java             | Java 21                                        |
| Database         | Oracle Database (Docker – `ojdbc11`)           |
| Excel Library    | Apache POI `poi-ooxml` 5.4.1                   |
| API Docs         | SpringDoc OpenAPI (Swagger UI) 3.0.3           |
| Mail             | Spring Boot Starter Mail (Gmail SMTP)          |
| Config           | `spring-dotenv` (đọc file `.env`)              |
| ORM              | Spring Data JPA + Hibernate                    |
| Build Tool       | Maven                                          |
| Container        | Docker Compose (Oracle DB)                     |

---

## 🔍 Vấn đề được giải quyết

| Tiêu chí            | `XSSFWorkbook`                          | `SXSSFWorkbook`                          |
|---------------------|-----------------------------------------|------------------------------------------|
| Cơ chế              | Giữ toàn bộ dữ liệu trong **RAM**       | **Streaming** – ghi từng phần ra disk    |
| Bộ nhớ sử dụng      | O(n) – tỉ lệ với số dòng               | O(1) – hằng số (window size cố định)    |
| Rủi ro              | `OutOfMemoryError` với dữ liệu lớn     | An toàn với hàng triệu dòng             |
| Tốc độ              | Nhanh hơn với dataset nhỏ              | Tốt hơn cho dataset lớn                 |
| Hỗ trợ random access| ✅ Có                                   | ❌ Không (chỉ ghi tuần tự)              |

> **Kết luận:** Khi dữ liệu lớn (hàng chục nghìn đến hàng triệu dòng), hãy dùng `SXSSFWorkbook` để tránh OOM.

---

## ⚙️ Cấu hình

### 1. Biến môi trường (`.env`)

Tạo file `.env` ở thư mục gốc dự án với nội dung:

```env
SPRING_MAIL_USERNAME = your-email@gmail.com
SPRING_MAIL_PASSWORD = your-app-password
SPRING_MAIL_RECEIVED = receiver@gmail.com
```

> **Lưu ý:** `SPRING_MAIL_PASSWORD` là **App Password** của Gmail (không phải mật khẩu đăng nhập).  
> Xem hướng dẫn tạo App Password: [Google Account → Security → App Passwords](https://myaccount.google.com/apppasswords)

### 2. Khởi động Oracle Database (Docker)

```bash
docker-compose up -d
```

Oracle sẽ chạy tại `localhost:1521`.  
Script SQL trong thư mục `oracle-init/` sẽ tự động chạy khi container khởi động lần đầu.

| Thông số      | Giá trị        |
|---------------|----------------|
| Host          | `localhost`    |
| Port          | `1521`         |
| Password SYS  | `Oracle123`    |

### 3. Chạy ứng dụng

```bash
./mvnw spring-boot:run
```

---

## 📡 API Endpoints

Truy cập Swagger UI tại: **`http://localhost:8080/swagger-ui.html`**

### Quản lý dữ liệu

| Method   | Endpoint                  | Mô tả                                  |
|----------|---------------------------|----------------------------------------|
| `GET`    | `/transactions`           | Lấy toàn bộ transactions               |
| `GET`    | `/transactions/{id}`      | Lấy transaction theo ID                |
| `GET`    | `/transactions/count`     | Đếm tổng số records trong DB           |
| `DELETE` | `/transactions/{id}`      | Xóa transaction theo ID                |
| `POST`   | `/transactions/seed`      | Tạo dữ liệu mẫu ngẫu nhiên            |

#### Seed Data

```http
POST /transactions/seed?count=10000
```

| Tham số | Kiểu  | Mặc định | Giới hạn       |
|---------|-------|----------|----------------|
| `count` | `int` | `500`    | 1 – 50,000     |

---

### Xuất Excel

| Method | Endpoint                    | Workbook         | Ghi chú                     |
|--------|-----------------------------|------------------|-----------------------------|
| `GET`  | `/transactions/export/xssf` | `XSSFWorkbook`   | ⚠️ Có thể gây OOM           |
| `GET`  | `/transactions/export/sxssf`| `SXSSFWorkbook`  | ✅ Streaming, an toàn        |

**Query Parameters (chung cho cả 2 API):**

| Tham số     | Kiểu      | Mặc định | Mô tả                                          |
|-------------|-----------|----------|------------------------------------------------|
| `sendEmail` | `boolean` | `false`  | Gửi file Excel đính kèm qua email sau khi xuất |
| `toEmail`   | `string`  | *(trống)*| Email nhận (bỏ trống = dùng email trong `.env`)|

**Ví dụ – Xuất và gửi email:**
```http
GET /transactions/export/sxssf?sendEmail=true&toEmail=example@gmail.com
```

**Ví dụ – Chỉ tải file (không gửi email):**
```http
GET /transactions/export/sxssf
```

---

## 📁 Cấu trúc dự án

```
sxxsworkbook/
├── src/main/java/com/example/sxxsworkbook/
│   ├── controller/
│   │   └── TransactionController.java   # REST API endpoints
│   ├── service/
│   │   ├── TransactionService.java      # CRUD + seed data
│   │   ├── TransactionExcelService.java # Logic xuất Excel (XSSF & SXSSF)
│   │   └── EmailService.java            # Gửi email đính kèm Excel
│   ├── entity/
│   │   └── Transaction.java             # JPA Entity
│   ├── repository/
│   │   └── TransactionRepository.java  # Spring Data JPA Repository
│   ├── enums/
│   │   ├── TransactionType.java
│   │   ├── TransactionStatus.java
│   │   └── TransactionChannel.java
│   └── SxxsworkbookApplication.java    # Entry point
├── oracle-init/                         # SQL scripts khởi tạo DB
├── docker-compose.yml                   # Oracle DB container
├── .env                                 # Biến môi trường (không commit)
└── pom.xml
```

---

## 🧪 Kịch bản test

1. **Seed dữ liệu** – Tạo ít nhất 10,000 records:
   ```http
   POST /transactions/seed?count=10000
   ```

2. **Test XSSFWorkbook** – Dễ gặp OOM với heap nhỏ:
   ```http
   GET /transactions/export/xssf
   ```
   Nếu JVM heap bị giới hạn (ví dụ `-Xmx64m`), endpoint này sẽ trả về HTTP `507` kèm thông báo lỗi.

3. **Test SXSSFWorkbook** – Streaming, ổn định:
   ```http
   GET /transactions/export/sxssf
   ```

4. **So sánh** – Quan sát log để thấy sự khác biệt về thời gian xử lý và heap usage.

---

## 📧 Cấu hình Gmail SMTP

Để tính năng gửi email hoạt động:

1. Bật **2-Factor Authentication** trên tài khoản Google.
2. Tạo **App Password** tại: `Google Account → Security → App Passwords`.
3. Điền App Password vào biến `SPRING_MAIL_PASSWORD` trong file `.env`.

> ⚠️ **Không commit file `.env` lên Git.** File này đã được thêm vào `.gitignore`.

---

## 📝 License

Project này được dùng cho mục đích **học tập và demo**.
