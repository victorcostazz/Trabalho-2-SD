package sd.cliente;

import sd.modelo.Mensagem;
import sd.modelo.Usuario;
import sd.rpc.RemoteObjectRef;
import sd.rpc.RpcClient;
import sd.rpc.RpcServer;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Núcleo do cliente, sem E/S de console. Hospeda o {@link OuvinteRemoto} (por isso tem seu próprio
 * {@link RpcServer}) e fala com o servidor pelo {@link RpcClient}.
 */
public class ClienteRpc implements AutoCloseable {

    private final RpcServer local;
    private final RpcClient rpc;
    private final RemoteObjectRef servidor;
    private RemoteObjectRef sessao;
    private Usuario usuario;

    public ClienteRpc(String hostLocal, int portaLocalInicial, String hostServidor, int portaServidor,
                      Consumer<Mensagem> aoReceber) throws RemoteException {
        this.local = RpcServer.emPortaLivre(hostLocal, portaLocalInicial, 200);
        this.local.registrar(OuvinteRemoto.NOME, new OuvinteRemoto(aoReceber));
        this.local.iniciar(2);
        this.rpc = new RpcClient(local.getPorta());
        this.servidor = new RemoteObjectRef(hostServidor, portaServidor, "autenticacao");
    }

    @SuppressWarnings("unchecked")
    public Usuario login(String login, String senha) {
        Map<String, Object> r = rpc.chamar(servidor, "login", mapa("usuario", login, "senha", senha));
        this.sessao = RemoteObjectRef.fromMap((Map<String, Object>) r.get("sessao"));
        this.usuario = Usuario.fromMap((Map<String, Object>) r.get("usuario"));
        return usuario;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listarGrupos() {
        return (List<Map<String, Object>>) sessao("listarGrupos", mapa()).get("grupos");
    }

    public void entrar(String grupo) {
        sessao("entrarGrupo", mapa("grupo", grupo, "ouvinte", local.referencia(OuvinteRemoto.NOME).toMap()));
    }

    public boolean sair(String grupo) {
        return Boolean.TRUE.equals(sessao("sairGrupo", mapa("grupo", grupo)).get("saiu"));
    }

    @SuppressWarnings("unchecked")
    public List<Usuario> membros(String grupo) {
        List<Usuario> lista = new ArrayList<>();
        for (Object o : (List<Object>) sessao("listarMembros", mapa("grupo", grupo)).get("membros")) {
            lista.add(Usuario.fromMap((Map<String, Object>) o));
        }
        return lista;
    }

    @SuppressWarnings("unchecked")
    public List<Mensagem> historico(String grupo) {
        List<Mensagem> lista = new ArrayList<>();
        for (Object o : (List<Object>) sessao("historico", mapa("grupo", grupo)).get("mensagens")) {
            lista.add(Mensagem.fromMap((Map<String, Object>) o));
        }
        return lista;
    }

    /** @return quantidade de membros que receberam */
    public int enviar(String grupo, String tipo, String texto) {
        Map<String, Object> r = sessao("enviarMensagem", mapa("grupo", grupo, "tipo", tipo, "mensagem", texto));
        return ((Number) r.get("entregues")).intValue();
    }

    public void logout() {
        if (sessao != null) {
            sessao("logout", mapa());
            sessao = null;
        }
    }

    /** Derruba o endpoint local sem fazer logout (simula a queda do cliente). */
    public void derrubarSemLogout() {
        local.encerrar();
    }

    @Override
    public void close() {
        try {
            logout();
        } catch (RuntimeException ignorada) {
            // servidor já indisponível
        }
        local.encerrar();
    }

    private Map<String, Object> sessao(String metodo, Map<String, Object> args) {
        if (sessao == null) throw new IllegalStateException("Faça login primeiro");
        return rpc.chamar(sessao, metodo, args);
    }

    private static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
