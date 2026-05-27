package com.example.sxxsworkbook.service;

import com.example.sxxsworkbook.entity.Transaction;
import com.example.sxxsworkbook.enums.TransactionChannel;
import com.example.sxxsworkbook.enums.TransactionStatus;
import com.example.sxxsworkbook.enums.TransactionType;
import com.example.sxxsworkbook.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Service CRUD cho Transaction + seed data generator.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private static final Random RANDOM = new Random();

    // CRUD
    public List<Transaction> findAll() {
        return transactionRepository.findAll();
    }

    public Transaction findById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + id));
    }

    @Transactional
    public void deleteById(Long id) {
        transactionRepository.deleteById(id);
    }

    public long count() {
        return transactionRepository.count();
    }

    // Seed Data Generator

    /**
     * Tạo n records ngẫu nhiên để test xuất Excel.
     *
     * @param count số lượng records muốn tạo
     * @return số records đã insert
     */
    @Transactional
    public int seedData(int count) {
        log.info("[Seed] Generating {} transaction records...", count);

        String[] banks       = {"Vietcombank", "Techcombank", "BIDV", "Agribank", "VPBank", "MBBank", "ACB", "TPBank"};
        String[] branches    = {"HaNoi", "TP.HCM", "DaNang", "HaiPhong", "CanTho","BenTre"};
        String[] currencies  = {"VND", "USD", "EUR"};
        String[] channels    = {"MOBILE_APP", "INTERNET_BANKING", "ATM", "COUNTER", "API"};
        String[] txnTypes    = {"TRANSFER", "PAYMENT", "DEPOSIT", "WITHDRAWAL", "REFUND"};
        String[] statuses    = {"SUCCESS", "SUCCESS", "SUCCESS", "FAILED", "PENDING", "PROCESSING"};
        String[] devices     = {"iPhone 20 ProMax", "Samsung Galaxy Ultra S24", "Chrome/Windows", "Firefox/Ubuntu", "Edge/Win11"};

        List<Transaction> batch = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            TransactionType type   = TransactionType.valueOf(txnTypes[RANDOM.nextInt(txnTypes.length)]);
            TransactionStatus status = TransactionStatus.valueOf(statuses[RANDOM.nextInt(statuses.length)]);
            TransactionChannel channel = TransactionChannel.valueOf(channels[RANDOM.nextInt(channels.length)]);

            BigDecimal amount      = BigDecimal.valueOf(RANDOM.nextLong(100_000L, 500_000_000L));
            String     currency    = currencies[RANDOM.nextInt(currencies.length)];
            BigDecimal fee         = amount.multiply(BigDecimal.valueOf(0.001));
            BigDecimal exchRate    = currency.equals("VND") ? BigDecimal.ONE
                                  : currency.equals("USD") ? BigDecimal.valueOf(24800)
                                  : BigDecimal.valueOf(26500);
            BigDecimal amtInVnd   = amount.multiply(exchRate);

            boolean isFailed = status == TransactionStatus.FAILED;

            Transaction txn = Transaction.builder()
                .referenceNumber("TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase())
                .transactionType(type)
                .status(status)
                // Sender
                .senderAccountNo(randomAccountNo())
                .senderName(randomVietnameseName())
                .senderBank(banks[RANDOM.nextInt(banks.length)])
                .senderBranch(branches[RANDOM.nextInt(branches.length)])
                // Receiver
                .receiverAccountNo(randomAccountNo())
                .receiverName(randomVietnameseName())
                .receiverBank(banks[RANDOM.nextInt(banks.length)])
                .receiverBranch(branches[RANDOM.nextInt(branches.length)])
                // Money
                .amount(amount)
                .currency(currency)
                .fee(fee)
                .exchangeRate(exchRate)
                .amountInVnd(amtInVnd)
                // Info
                .description(randomDescription(type))
                .channel(channel)
                .ipAddress(randomIp())
                .deviceInfo(devices[RANDOM.nextInt(devices.length)])
                .otpReference(status == TransactionStatus.PENDING ? null : "OTP-" + RANDOM.nextInt(999999))
                .orderId(type == TransactionType.PAYMENT ? "ORD-" + RANDOM.nextInt(1_000_000) : null)
                .batchId(channel == TransactionChannel.BATCH ? "BATCH-2026-" + RANDOM.nextInt(9999) : null)
                .retryCount(isFailed ? RANDOM.nextInt(1, 4) : 0)
                .errorCode(isFailed ? "ERR_" + RANDOM.nextInt(1000, 9999) : null)
                .errorMessage(isFailed ? "Transaction declined: insufficient funds or timeout" : null)
                .originalTransactionRef(type == TransactionType.REFUND ? "TXN-ORIG-" + RANDOM.nextInt(1_000_000) : null)
                .completedAt(status == TransactionStatus.SUCCESS
                    ? LocalDateTime.now().minusMinutes(RANDOM.nextInt(60)) : null)
                .createdBy("SYSTEM")
                .build();

            batch.add(txn);

            // Insert theo batch 100 records để tránh memory spike trong seed
            if (batch.size() >= 100) {
                transactionRepository.saveAll(batch);
                batch.clear();
                log.debug("[Seed] Inserted up to {} records...", i + 1);
            }
        }

        // Insert phần còn lại
        if (!batch.isEmpty()) {
            transactionRepository.saveAll(batch);
        }

        log.info("[Seed] ✅ Done! Total inserted: {}", count);
        return count;
    }

    // Private helpers
    private String randomAccountNo() {
        return String.format("%014d", RANDOM.nextLong(10_000_000_000_000L, 99_999_999_999_999L));
    }

    private String randomIp() {
        return RANDOM.nextInt(256) + "." + RANDOM.nextInt(256) + "." +
               RANDOM.nextInt(256) + "." + RANDOM.nextInt(256);
    }

    private String randomVietnameseName() {
        String[] lastNames  = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Đặng", "Bùi"};
        String[] midNames   = {"Văn", "Thị", "Đức", "Minh", "Quốc", "Anh", "Hữu", "Thành", "Xuân"};
        String[] firstNames = {"An", "Bình", "Châu", "Dũng", "Hoa", "Hùng", "Lan", "Long", "Mai", "Nam",
                               "Ngọc", "Phúc", "Quân", "Sơn", "Thảo", "Tuấn", "Uyên", "Vinh", "Yến"};
        return lastNames[RANDOM.nextInt(lastNames.length)] + " " +
               midNames[RANDOM.nextInt(midNames.length)]  + " " +
               firstNames[RANDOM.nextInt(firstNames.length)];
    }

    private String randomDescription(TransactionType type) {
        return switch (type) {
            case TRANSFER   -> "Chuyển tiền người thân tháng " + (RANDOM.nextInt(12) + 1) + "/2024";
            case PAYMENT    -> "Thanh toán hóa đơn điện/nước/internet tháng " + (RANDOM.nextInt(12) + 1);
            case DEPOSIT    -> "Nạp tiền vào ví điện tử";
            case WITHDRAWAL -> "Rút tiền tại ATM " + ("HN,HCM,DN,HP".split(",")[RANDOM.nextInt(4)]);
            case REFUND     -> "Hoàn tiền đơn hàng bị hủy #" + RANDOM.nextInt(1_000_000);
        };
    }
}
