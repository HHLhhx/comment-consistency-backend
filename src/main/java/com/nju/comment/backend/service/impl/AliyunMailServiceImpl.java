package com.nju.comment.backend.service.impl;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dm.model.v20151123.SingleSendMailRequest;
import com.aliyuncs.dm.model.v20151123.SingleSendMailResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.nju.comment.backend.exception.ErrorCode;
import com.nju.comment.backend.exception.ServiceException;
import com.nju.comment.backend.service.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AliyunMailServiceImpl implements MailService {

    @Value("${app.mail.aliyun.access-key-id}")
    private String accessKeyId;

    @Value("${app.mail.aliyun.access-key-secret}")
    private String accessKeySecret;

    @Value("${app.mail.aliyun.account-name}")
    private String accountName;

    @Value("${app.mail.aliyun.region-id}")
    private String regionId;

    @Value("${app.mail.aliyun.address-type}")
    private int addressType;

    @Value("${app.mail.aliyun.from-alias}")
    private String fromAlias;


    public void sendEmail(String toAddress, String subject, String content) {
        DefaultProfile profile = DefaultProfile.getProfile(regionId, accessKeyId, accessKeySecret);
        IAcsClient client = new DefaultAcsClient(profile);

        SingleSendMailRequest request = new SingleSendMailRequest();
        request.setAccountName(accountName);
        request.setAddressType(addressType);
        request.setReplyToAddress(false);
        request.setToAddress(toAddress);
        request.setSubject(subject);
        request.setHtmlBody(content);
        request.setFromAlias(fromAlias);

        try {
            SingleSendMailResponse response = client.getAcsResponse(request);
            log.info("邮件发送成功, 请求ID: {}", response.getRequestId());
        } catch (ClientException e) {
            throw new ServiceException(ErrorCode.AUTH_EMAIL_SEND_FAILED, "邮件发送失败", e);
        }
    }
}
