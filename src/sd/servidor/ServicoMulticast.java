package sd.servidor;

import sd.modelo.GrupoMulticast;
import sd.modelo.Mensagem;
import sd.modelo.TipoMensagem;
import sd.modelo.Usuario;
import sd.rpc.RemoteObjectRef;
import sd.rpc.RpcClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serviço de grupos. Guarda os grupos e, para cada membro, a referência remota do seu ouvinte.
 * O envio é feito em paralelo: uma chamada remota {@code receber} por membro.
 */
public class ServicoMulticast {

    private static final long TIMEOUT_ENTREGA_SEGUNDOS = 10;

    private final RpcClient rpc;
    private final Map<String, GrupoMulticast> grupos = new ConcurrentHashMap<>();
    /** grupo -> (login -> referência do ouvinte remoto do cliente) */
    private final Map<String, Map<String, RemoteObjectRef>> ouvintes = new ConcurrentHashMap<>();
    private final AtomicLong proximoId = new AtomicLong();
    private final ExecutorService entregas = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "entrega-multicast");
        t.setDaemon(true);
        return t;
    });

    public ServicoMulticast(RpcClient rpc) {
        this.rpc = rpc;
    }

    public void criarGrupo(GrupoMulticast g) {
        grupos.put(g.getNome(), g);
        ouvintes.put(g.getNome(), new ConcurrentHashMap<>());
    }

    public List<GrupoMulticast> listarGrupos() {
        return new ArrayList<>(grupos.values());
    }

    public GrupoMulticast grupo(String nome) {
        GrupoMulticast g = grupos.get(nome);
        if (g == null) throw new IllegalArgumentException("Grupo inexistente: " + nome);
        return g;
    }

    /** joinGroup */
    public void entrar(Usuario usuario, String nomeGrupo, RemoteObjectRef ouvinte) {
        GrupoMulticast g = grupo(nomeGrupo);
        g.adicionarMembro(usuario);
        ouvintes.get(nomeGrupo).put(usuario.getLogin(), ouvinte);
    }

    /** leaveGroup */
    public boolean sair(Usuario usuario, String nomeGrupo) {
        GrupoMulticast g = grupo(nomeGrupo);
        ouvintes.get(nomeGrupo).remove(usuario.getLogin());
        return g.removerMembro(usuario.getLogin());
    }

    public void sairDeTodos(Usuario usuario) {
        for (String nome : grupos.keySet()) sair(usuario, nome);
    }

    /** Registra a mensagem no histórico e a entrega a todos os membros concorrentemente. */
    public int enviar(Usuario autor, String nomeGrupo, TipoMensagem tipo, String texto) {
        GrupoMulticast g = grupo(nomeGrupo);
        Mensagem m = tipo.criar(proximoId.incrementAndGet(), texto, autor, System.currentTimeMillis());
        g.registrarMensagem(m);

        Map<String, RemoteObjectRef> destinos = new HashMap<>(ouvintes.get(nomeGrupo));
        Map<String, Future<?>> envios = new HashMap<>();
        for (Map.Entry<String, RemoteObjectRef> d : destinos.entrySet()) {
            envios.put(d.getKey(), entregas.submit(() -> rpc.chamar(d.getValue(), "receber", m.toMap())));
        }

        int entregues = 0;
        for (Map.Entry<String, Future<?>> e : envios.entrySet()) {
            try {
                e.getValue().get(TIMEOUT_ENTREGA_SEGUNDOS, TimeUnit.SECONDS);
                entregues++;
            } catch (Exception falha) {
                // ouvinte inalcançável: o cliente caiu; tira do grupo
                System.out.println("[servidor] removendo " + e.getKey() + " de " + nomeGrupo + ": "
                        + falha.getMessage());
                ouvintes.get(nomeGrupo).remove(e.getKey());
                g.removerMembro(e.getKey());
            }
        }
        return entregues;
    }
}
