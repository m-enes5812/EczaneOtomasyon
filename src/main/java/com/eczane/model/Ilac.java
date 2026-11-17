package com.eczane.model;

public record Ilac(long id, String ad) {
    @Override
    public String toString() { return ad; }
}
