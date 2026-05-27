package com.example.sxxsworkbook.enums;

public enum TransactionStatus {
    PENDING,       // Chờ xử lý
    PROCESSING,    // Đang xử lý
    SUCCESS,       // Thành công
    FAILED,        // Thất bại
    CANCELLED,     // Đã hủy
    REVERSED       // Đã đảo chiều
}
