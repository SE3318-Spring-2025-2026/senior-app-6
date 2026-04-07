package com.senior.spm.converter;

import com.senior.spm.service.SymmetricEncryptionService;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AccessTokenEncryptionConverter implements AttributeConverter<String, String> {

    private final SymmetricEncryptionService encryptionService;

    public AccessTokenEncryptionConverter(SymmetricEncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        try {
            return encryptionService.encrypt(attribute);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        try {
            return encryptionService.decrypt(dbData);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
