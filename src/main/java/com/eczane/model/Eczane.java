package com.eczane.model;

public record Eczane(long id, String ad) {
    @Override
    public String toString() { return ad; }
}
