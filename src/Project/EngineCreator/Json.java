package Project.EngineCreator;

import java.util.*;

/** Small JSON reader/writer used for engine project files. */
public final class Json {
    private final String s;
    private int i;
    private Json(String s) { this.s = s; }

    static Object parse(String text) {
        Json j = new Json(text);
        Object v = j.value();
        j.skip();
        if (j.i != text.length()) throw j.error("Extra symbols after data");
        return v;
    }
    static String quote(String v) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : v.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
    private Object value() {
        skip();
        if (i >= s.length()) throw error("Unexpected EOF");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (c == 't') { expect("true"); return Boolean.TRUE; }
        if (c == 'f') { expect("false"); return Boolean.FALSE; }
        if (c == 'n') { expect("null"); return null; }
        return number();
    }
    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        skip();
        if (peek('}')) { i++; return m; }
        while (true) {
            skip();
            String key = string();
            skip();
            if (!peek(':')) throw error("expected ':'");
            i++;
            m.put(key, value());
            skip();
            if (peek(',')) { i++; continue; }
            if (peek('}')) { i++; return m; }
            throw error("expected ',' or '}'");
        }
    }
    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        i++;
        skip();
        if (peek(']')) { i++; return list; }
        while (true) {
            list.add(value());
            skip();
            if (peek(',')) { i++; continue; }
            if (peek(']')) { i++; return list; }
            throw error("expected ',' or ']'");
        }
    }
    private String string() {
        if (!peek('"')) throw error("expected string");
        i++;
        StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            if (i >= s.length()) break;
            char e = s.charAt(i++);
            switch (e) {
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 't' -> b.append('\t');
                case 'u' -> {
                    if (i + 4 > s.length()) throw error("bad escape-code");
                    b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                }
                default -> b.append(e);
            }
        }
        throw error("forgot to close line");
    }
    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw error("unexpected symbol '" + s.charAt(i) + "'");
        try { return Double.parseDouble(s.substring(start, i)); }
        catch (NumberFormatException e) { throw error("bad num"); }
    }
    private void expect(String word) {
        if (!s.startsWith(word, i)) throw error("expected " + word);
        i += word.length();
    }
    private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }
    private void skip() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    private IllegalArgumentException error(String what) { return new IllegalArgumentException("File Error on Position " + i + ": " + what); }
}