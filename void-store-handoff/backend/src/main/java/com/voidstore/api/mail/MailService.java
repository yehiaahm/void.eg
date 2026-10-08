package com.voidstore.api.mail;

import com.voidstore.api.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Sends transactional email in the background; failures are logged, never thrown to the shopper. */
@Service
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private final JavaMailSender sender;
	private final String from;

	public MailService(JavaMailSender sender, AppProperties props) {
		this.sender = sender;
		this.from = props.mailFrom();
	}

	@Async
	public void send(String to, String subject, String html) {
		try {
			MimeMessage msg = sender.createMimeMessage();
			var h = new MimeMessageHelper(msg, false, "UTF-8");
			h.setFrom(from);
			h.setTo(to);
			h.setSubject(subject);
			h.setText(html, true);
			sender.send(msg);
		} catch (Exception e) {
			log.warn("Email to {} failed: {}", to, e.getMessage());
		}
	}
}
