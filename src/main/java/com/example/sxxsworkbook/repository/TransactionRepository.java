package com.example.sxxsworkbook.repository;

import com.example.sxxsworkbook.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.stream.Stream;

/**
 * Repository cho Transaction.
 * Dùng Stream để lazy-load khi xuất Excel (tránh load toàn bộ vào heap).
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Stream tất cả transactions – dùng với @Transactional(readOnly=true)
     * để Hibernate scroll cursor thay vì load hết vào RAM.
     */
    @Query("SELECT t FROM Transaction t ORDER BY t.createdAt DESC, t.id DESC")
    Stream<Transaction> streamAllOrderByCreatedAtDesc();
}
