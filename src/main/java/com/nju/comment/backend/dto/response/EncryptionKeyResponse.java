package com.nju.comment.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionKeyResponse {
    private String algorithm;
    private String publicKey;
}