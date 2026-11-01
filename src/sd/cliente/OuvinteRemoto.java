package sd.cliente;

import sd.modelo.Mensagem;
import sd.rpc.MetodoRemoto;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Objeto remoto do cliente. O servidor o invoca (por referência) a cada mensagem de grupo.
 * A mensagem chega por valor, desempacotada do JSON.
 */
public class OuvinteRemoto {

    public static final String NOME = "ouvinte";

    private final Consumer<Mensagem> aoReceber;

    public OuvinteRemoto(Consumer<Mensagem> aoReceber) {
        this.aoReceber = aoReceber;
    }

    @MetodoRemoto
    public Object receber(Map<String, Object> args) {
        aoReceber.accept(Mensagem.fromMap(args));
        return Map.of("recebido", true);
    }
}
