package sd.rpc;

import java.util.Map;

/** Leitura tipada dos argumentos desempacotados de uma chamada. */
public final class Args {

    private Args() {
    }

    public static String texto(Map<String, Object> args, String chave) {
        Object v = args.get(chave);
        if (!(v instanceof String s)) {
            throw new IllegalArgumentException("Argumento obrigatório ausente ou inválido: " + chave);
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> objeto(Map<String, Object> args, String chave) {
        Object v = args.get(chave);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Argumento obrigatório ausente ou inválido: " + chave);
        }
        return (Map<String, Object>) v;
    }
}
