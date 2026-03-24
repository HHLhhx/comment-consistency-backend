package com.nju.comment.backend.service;

public interface MailService {

    void sendEmail(String toAddress, String subject, String content);
}
