package com.example.sxxsworkbook.entity;

import com.example.sxxsworkbook.enums.TransactionChannel;
import com.example.sxxsworkbook.enums.TransactionStatus;
import com.example.sxxsworkbook.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity đại diện cho một giao dịch tài chính.
 * Bảng TRANSACTIONS sẽ được tạo tự động bởi Hibernate (ddl-auto=update).
 */
@Entity
@Table(
    name = "TRANSACTIONS",
        indexes = {
                @Index(name = "idx_txn_created_id",    columnList = "CREATED_AT DESC, ID DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Transaction {

    // Primary Key – dùng Oracle sequence
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transaction_seq_gen")
    @SequenceGenerator(
        name           = "transaction_seq_gen",
        sequenceName   = "TRANSACTION_SEQ",
        allocationSize = 1
    )
    @Column(name = "ID")
    private Long id;

    // Thông tin định danh giao dịch
    /** Mã tham chiếu duy nhất của giao dịch (UUID hoặc mã nội bộ) */
    @Column(name = "REFERENCE_NUMBER", nullable = false, unique = true, length = 64)
    private String referenceNumber;

    /** Loại giao dịch: TRANSFER, PAYMENT, DEPOSIT, WITHDRAWAL, REFUND */
    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSACTION_TYPE", nullable = false, length = 20)
    private TransactionType transactionType;

    /** Trạng thái giao dịch: PENDING, PROCESSING, SUCCESS, FAILED, CANCELLED, REVERSED */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private TransactionStatus status;

    // ─────────────────────────────────────────────
    // Thông tin người gửi
    // ─────────────────────────────────────────────

    /** Số tài khoản người gửi */
    @Column(name = "SENDER_ACCOUNT_NO", nullable = false, length = 30)
    private String senderAccountNo;

    /** Tên chủ tài khoản người gửi */
    @Column(name = "SENDER_NAME", nullable = false, length = 100)
    private String senderName;

    /** Tên ngân hàng người gửi */
    @Column(name = "SENDER_BANK", length = 100)
    private String senderBank;

    /** Tên chi nhánh ngân hàng người gửi */
    @Column(name = "SENDER_BRANCH", length = 100)
    private String senderBranch;

    // ─────────────────────────────────────────────
    // Thông tin người nhận
    // ─────────────────────────────────────────────

    /** Số tài khoản người nhận */
    @Column(name = "RECEIVER_ACCOUNT_NO", nullable = false, length = 30)
    private String receiverAccountNo;

    /** Tên chủ tài khoản người nhận */
    @Column(name = "RECEIVER_NAME", nullable = false, length = 100)
    private String receiverName;

    /** Tên ngân hàng người nhận */
    @Column(name = "RECEIVER_BANK", length = 100)
    private String receiverBank;

    /** Tên chi nhánh ngân hàng người nhận */
    @Column(name = "RECEIVER_BRANCH", length = 100)
    private String receiverBranch;

    // ─────────────────────────────────────────────
    // Thông tin tiền tệ và số tiền
    // ─────────────────────────────────────────────

    /** Số tiền giao dịch (gốc) */
    @Column(name = "AMOUNT", nullable = false, precision = 20, scale = 2)
    private BigDecimal amount;

    /** Loại tiền tệ (ISO 4217): VND, USD, EUR, ... */
    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    /** Phí giao dịch */
    @Column(name = "FEE", precision = 18, scale = 2)
    private BigDecimal fee;

    /** Tỉ giá áp dụng nếu cross-currency */
    @Column(name = "EXCHANGE_RATE", precision = 18, scale = 6)
    private BigDecimal exchangeRate;

    /** Số tiền quy đổi sang VND */
    @Column(name = "AMOUNT_IN_VND", precision = 20, scale = 2)
    private BigDecimal amountInVnd;

    // ─────────────────────────────────────────────
    // Thông tin phụ
    // ─────────────────────────────────────────────

    /** Nội dung chuyển khoản / ghi chú */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** Kênh thực hiện: MOBILE_APP, INTERNET_BANKING, ATM, COUNTER, API */
    @Enumerated(EnumType.STRING)
    @Column(name = "CHANNEL", length = 30)
    private TransactionChannel channel;

    /** Địa chỉ IP của người thực hiện */
    @Column(name = "IP_ADDRESS", length = 45)
    private String ipAddress;

    /** Thiết bị thực hiện giao dịch */
    @Column(name = "DEVICE_INFO", length = 200)
    private String deviceInfo;

    /** Mã OTP / xác thực đã dùng */
    @Column(name = "OTP_REFERENCE", length = 50)
    private String otpReference;

    /** Mã lỗi nếu giao dịch thất bại */
    @Column(name = "ERROR_CODE", length = 20)
    private String errorCode;

    /** Thông điệp lỗi nếu giao dịch thất bại */
    @Column(name = "ERROR_MESSAGE", length = 500)
    private String errorMessage;

    /** Mã đơn hàng (nếu liên quan đến thanh toán thương mại) */
    @Column(name = "ORDER_ID", length = 100)
    private String orderId;

    /** Batch / lô xử lý (nếu giao dịch thuộc batch) */
    @Column(name = "BATCH_ID", length = 50)
    private String batchId;

    /** Số lần thử lại (retry count) */
    @Column(name = "RETRY_COUNT", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /** Giao dịch gốc (nếu đây là giao dịch hoàn/đảo) */
    @Column(name = "ORIGINAL_TRANSACTION_REF", length = 64)
    private String originalTransactionRef;

    // Timestamp
    @CreationTimestamp
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "COMPLETED_AT")
    private LocalDateTime completedAt;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

}
