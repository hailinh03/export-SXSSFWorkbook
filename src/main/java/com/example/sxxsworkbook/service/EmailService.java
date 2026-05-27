package com.example.sxxsworkbook.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Service gửi email với file đính kèm (Excel).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${app.mail.sender-name}")
    private String senderName;

    @Value("${app.mail.default-recipient}")
    private String defaultRecipient;

    /**
     * Gửi email với file Excel đính kèm.
     *
     * @param toEmail       Địa chỉ email nhận (nếu null thì dùng default)
     * @param subject       Tiêu đề email
     * @param bodyHtml      Nội dung HTML của email
     * @param excelBytes    Nội dung file Excel dạng byte[]
     * @param fileName      Tên file đính kèm
     * @param workbookType  "XSSF" hoặc "SXSSF" (dùng để log)
     */
    public void sendExcelReport(
            String toEmail,
            String subject,
            String bodyHtml,
            byte[] excelBytes,
            String fileName,
            String workbookType
    ) throws MessagingException, java.io.UnsupportedEncodingException {
        String recipient = (toEmail != null && !toEmail.isBlank()) ? toEmail : defaultRecipient;

        log.info("[EmailService] Sending {} Excel report to: {}, file: {}, size: {} bytes",
                workbookType, recipient, fileName, excelBytes.length);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromAddress, senderName);
        helper.setTo(recipient);
        helper.setSubject(subject);
        helper.setText(bodyHtml, true);  // true = HTML

        // Đính kèm file Excel
        helper.addAttachment(fileName, new ByteArrayResource(excelBytes));

        mailSender.send(message);

        log.info("[EmailService] Email sent successfully to: {} | workbook: {}", recipient, workbookType);
    }
}
