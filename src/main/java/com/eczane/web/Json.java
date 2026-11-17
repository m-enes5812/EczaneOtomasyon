package com.eczane.web;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Küçük, bağımlılıksız JSON okuyucu/yazıcı.
 *
 * <p>Yazarken: Map, Collection, record, enum (adıyla), tarih/saat (ISO), sayı, boolean, String desteklenir.
 * Okurken: nesneler {@code Map<String,Object>}, diziler {@code List<Object>}, sayılar {@link BigDecimal} olur.
 */
public final class Json {

    private Json() {
    }

    // ---------------------------------------------------------------- yazma

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(256);
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        switch (v) {
            case null -> sb.append("null");
            case Optional<?> o -> write(sb, o.orElse(null));
            case String s -> string(sb, s);
            case Boolean b -> sb.append(b);
            case Number n -> {
                if (n instanceof Double d && (d.isNaN() || d.isInfinite())) sb.append("null");
                else sb.append(n);
            }
            case Enum<?> e -> string(sb, e.name());
            case TemporalAccessor t -> string(sb, t.toString());
            case Map<?, ?> m -> {
                sb.append('{');
                boolean first = true;
                for (var e : m.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    string(sb, String.valueOf(e.getKey()));
                    sb.append(':');
                    write(sb, e.getValue());
                }
                sb.append('}');
            }
            case Collection<?> c -> {
                sb.append('[');
                boolean first = true;
                for (Object o : c) {
                    if (!first) sb.append(',');
                    first = false;
                    write(sb, o);
                }
                sb.append(']');
            }
            case Record r -> {
                sb.append('{');
                boolean first = true;
                for (RecordComponent rc : r.getClass().getRecordComponents()) {
                    if (!first) sb.append(',');
                    first = false;
                    string(sb, rc.getName());
                    sb.append(':');
                    try {
                        var acc = rc.getAccessor();
                        acc.setAccessible(true);
                        write(sb, acc.invoke(r));
                    } catch (ReflectiveOperationException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
                sb.append('}');
            }
            default -> string(sb, v.toString());
        }
    }

    private static void string(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                // HTML içine gömülse bile güvenli kalsın
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case '&' -> sb.append("\\u0026");
                default -> {
                    if (c < 0x20 || c == ' ' || c == ' ') sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- okuma

    /** Hatalı JSON'da {@link IllegalArgumentException} fırlatır. */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.ws();
        Object v = p.value(0);
        p.ws();
        if (p.i != text.length()) throw p.error("Fazladan karakter");
        return v;
    }

    private static final class Parser {
        private static final int MAX_DEPTH = 32;
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String msg) {
            return new IllegalArgumentException("Geçersiz JSON (" + msg + ", konum " + i + ")");
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        char peek() {
            if (i >= s.length()) throw error("Beklenmeyen son");
            return s.charAt(i);
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) throw error("Çok derin");
            char c = peek();
            return switch (c) {
                case '{' -> object(depth);
                case '[' -> array(depth);
                case '"' -> str();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        Object literal(String word, Object v) {
            if (!s.startsWith(word, i)) throw error("Bilinmeyen değer");
            i += word.length();
            return v;
        }

        Map<String, Object> object(int depth) {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (peek() == '}') {
                i++;
                return m;
            }
            while (true) {
                ws();
                if (peek() != '"') throw error("Anahtar bekleniyor");
                String k = str();
                ws();
                if (peek() != ':') throw error("':' bekleniyor");
                i++;
                ws();
                m.put(k, value(depth + 1));
                ws();
                char c = peek();
                i++;
                if (c == '}') return m;
                if (c != ',') throw error("',' bekleniyor");
            }
        }

        List<Object> array(int depth) {
            List<Object> l = new ArrayList<>();
            i++;
            ws();
            if (peek() == ']') {
                i++;
                return l;
            }
            while (true) {
                ws();
                l.add(value(depth + 1));
                ws();
                char c = peek();
                i++;
                if (c == ']') return l;
                if (c != ',') throw error("',' bekleniyor");
            }
        }

        String str() {
            i++; // "
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                i++;
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = peek();
                    i++;
                    switch (e) {
                        case '"', '\\', '/' -> sb.append(e);
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw error("Eksik \\u");
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw error("Hatalı \\u");
                            }
                            i += 4;
                        }
                        default -> throw error("Hatalı kaçış");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        BigDecimal number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw error("Değer bekleniyor");
            try {
                return new BigDecimal(s.substring(start, i));
            } catch (NumberFormatException ex) {
                throw error("Hatalı sayı");
            }
        }
    }
}
