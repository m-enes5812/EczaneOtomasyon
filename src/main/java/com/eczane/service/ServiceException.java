package com.eczane.service;

/** Kullanıcıya olduğu gibi gösterilebilecek iş kuralı hatası. */
public class ServiceException extends RuntimeException {
    public ServiceException(String message) {
        super(message);
    }
}
