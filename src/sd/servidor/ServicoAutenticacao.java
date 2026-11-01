package sd.servidor;

import sd.modelo.Usuario;
import sd.rpc.Args;
import sd.rpc.MetodoRemoto;
import sd.rpc.RpcServer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Autentica usuários. Num login válido cria uma {@link SessaoRemota}, registra-a no servidor RPC
 * com um nome imprevisível e devolve sua referência (passagem por referência).
 */
public class ServicoAutenticacao {

    public static final String NOME = "autenticacao";

    private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();
    private final ServicoMulticast multicast;
    private final RpcServer servidor;

    public ServicoAutenticacao(ServicoMulticast multicast, RpcServer servidor) {
        this.multicast = multicast;
        this.servidor = servidor;
    }

    public void cadastrar(Usuario u) {
        usuarios.put(u.getLogin(), u);
    }

    @MetodoRemoto
    public Object login(Map<String, Object> args) {
        String login = Args.texto(args, "usuario");
        String senha = Args.texto(args, "senha");
        Usuario u = usuarios.get(login);
        if (u == null || !u.autentica(senha)) {
            throw new SecurityException("Usuário ou senha inválidos");
        }
        String nomeSessao = "sessao-" + UUID.randomUUID();
        servidor.registrar(nomeSessao, new SessaoRemota(u, nomeSessao, multicast, servidor));
        System.out.println("[servidor] login: " + u);

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("usuario", u);
        resposta.put("sessao", servidor.referencia(nomeSessao).toMap());
        return resposta;
    }
}
