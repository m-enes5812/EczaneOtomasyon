package com.eczane.db;

/** Beklenmeyen veritabanı hatası (kontrolsüz). */
public class DataException extends RuntimeException {
    public DataException(String message, Throwable cause) {
        super(message, cause);
    }
}
