package sd.util;

import sd.modelo.Marshallable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Representação externa de dados (JSON) escrita à mão, sem dependências.
 * Mapeamento: objeto -> Map, array -> List, número -> Long/Double, string -> String.
 */
public final class Json {

    private Json() {
    }

    public static String stringify(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(sb, valor);
        return sb.toString();
    }

    public static byte[] bytes(Object valor) {
        return stringify(valor).getBytes(StandardCharsets.UTF_8);
    }

    public static Object parse(String texto) {
        Leitor l = new Leitor(texto);
        l.espacos();
        Object v = l.valor();
        l.espacos();
        if (l.pos != texto.length()) {
            throw l.erro("conteúdo extra após o fim do JSON");
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObjeto(byte[] dados) {
        Object v = parse(new String(dados, StandardCharsets.UTF_8));
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("JSON esperado: objeto");
        }
        return (Map<String, Object>) v;
    }

    // ---------------------------------------------------------------- escrita

    private static void escrever(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Marshallable m) {
            escrever(sb, m.toMap());
        } else if (v instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                string(sb, String.valueOf(e.getKey()));
                sb.append(':');
                escrever(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean primeiro = true;
            for (Object o : it) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                escrever(sb, o);
            }
            sb.append(']');
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            sb.append(v);
        } else if (v instanceof Number n) {
            sb.append(n.doubleValue());
        } else if (v instanceof Enum<?> e) {
            string(sb, e.name());
        } else {
            string(sb, v.toString());
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
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- leitura

    private static final class Leitor {
        private final String s;
        private int pos;

        Leitor(String s) {
            this.s = s;
        }

        IllegalArgumentException erro(String msg) {
            return new IllegalArgumentException("JSON inválido na posição " + pos + ": " + msg);
        }

        void espacos() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        Object valor() {
            if (pos >= s.length()) throw erro("fim inesperado");
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> objeto();
                case '[' -> lista();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> numero();
            };
        }

        Object literal(String texto, Object valor) {
            if (!s.startsWith(texto, pos)) throw erro("literal inválido");
            pos += texto.length();
            return valor;
        }

        Map<String, Object> objeto() {
            Map<String, Object> mapa = new LinkedHashMap<>();
            pos++; // {
            espacos();
            if (peek() == '}') {
                pos++;
                return mapa;
            }
            while (true) {
                espacos();
                if (peek() != '"') throw erro("chave esperada");
                String chave = string();
                espacos();
                esperar(':');
                espacos();
                mapa.put(chave, valor());
                espacos();
                char c = proximo();
                if (c == '}') return mapa;
                if (c != ',') throw erro("',' ou '}' esperado");
            }
        }

        List<Object> lista() {
            List<Object> itens = new ArrayList<>();
            pos++; // [
            espacos();
            if (peek() == ']') {
                pos++;
                return itens;
            }
            while (true) {
                espacos();
                itens.add(valor());
                espacos();
                char c = proximo();
                if (c == ']') return itens;
                if (c != ',') throw erro("',' ou ']' esperado");
            }
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            pos++; // "
            while (true) {
                if (pos >= s.length()) throw erro("string não terminada");
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = proximo();
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw erro("escape \\u incompleto");
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw erro("escape desconhecido: \\" + e);
                }
            }
        }

        Object numero() {
            int inicio = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            String t = s.substring(inicio, pos);
            if (t.isEmpty()) throw erro("valor inesperado");
            try {
                if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.parseDouble(t);
                return Long.parseLong(t);
            } catch (NumberFormatException e) {
                throw erro("número inválido: " + t);
            }
        }

        char peek() {
            if (pos >= s.length()) throw erro("fim inesperado");
            return s.charAt(pos);
        }

        char proximo() {
            char c = peek();
            pos++;
            return c;
        }

        void esperar(char c) {
            if (proximo() != c) {
                pos--;
                throw erro("'" + c + "' esperado");
            }
        }
    }
}
