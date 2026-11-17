package com.eczane.security;

/** T.C. Kimlik numarası doğrulama (resmi algoritma). */
public final class TcKimlik {

    private TcKimlik() {
    }

    /** Yalnızca biçim kontrolü: 11 hane, hepsi rakam. */
    public static boolean isWellFormed(String tc) {
        if (tc == null || tc.length() != 11) return false;
        for (int i = 0; i < 11; i++) {
            char c = tc.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /**
     * Tam doğrulama: ilk hane 0 olamaz; 10. hane = ((1,3,5,7,9. hanelerin toplamı × 7) − (2,4,6,8. hanelerin
     * toplamı)) mod 10; 11. hane = ilk 10 hanenin toplamı mod 10.
     */
    public static boolean isValid(String tc) {
        if (!isWellFormed(tc) || tc.charAt(0) == '0') return false;
        int[] d = new int[11];
        for (int i = 0; i < 11; i++) d[i] = tc.charAt(i) - '0';
        int odd = d[0] + d[2] + d[4] + d[6] + d[8];
        int even = d[1] + d[3] + d[5] + d[7];
        int sum10 = 0;
        for (int i = 0; i < 10; i++) sum10 += d[i];
        return d[9] == Math.floorMod(odd * 7 - even, 10) && d[10] == sum10 % 10;
    }
}
