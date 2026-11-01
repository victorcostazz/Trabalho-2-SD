package sd.servidor;

import sd.modelo.Administrador;
import sd.modelo.GrupoMulticast;
import sd.modelo.TipoMensagem;
import sd.modelo.Usuario;
import sd.rpc.Args;
import sd.rpc.MetodoRemoto;
import sd.rpc.RemoteObjectRef;
import sd.rpc.RpcServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Objeto remoto criado no login e devolvido ao cliente por referência. Representa a conexão
 * autenticada: toda operação acontece em nome do {@link Usuario} dono da sessão.
 */
public class SessaoRemota {

    private final Usuario usuario;
    private final String nomeObjeto;
    private final ServicoMulticast multicast;
    private final RpcServer servidor;

    public SessaoRemota(Usuario usuario, String nomeObjeto, ServicoMulticast multicast, RpcServer servidor) {
        this.usuario = usuario;
        this.nomeObjeto = nomeObjeto;
        this.multicast = multicast;
        this.servidor = servidor;
    }

    @MetodoRemoto
    public Object listarGrupos(Map<String, Object> args) {
        List<Object> lista = new ArrayList<>();
        for (GrupoMulticast g : multicast.listarGrupos()) lista.add(g.resumo());
        return mapa("grupos", lista);
    }

    /** joinGroup. O ouvinte é passado por referência: o servidor chamará {@code receber} nele. */
    @MetodoRemoto
    public Object entrarGrupo(Map<String, Object> args) {
        String grupo = Args.texto(args, "grupo");
        RemoteObjectRef ouvinte = RemoteObjectRef.fromMap(Args.objeto(args, "ouvinte"));
        multicast.entrar(usuario, grupo, ouvinte);
        return mapa("grupo", grupo);
    }

    /** leaveGroup */
    @MetodoRemoto
    public Object sairGrupo(Map<String, Object> args) {
        String grupo = Args.texto(args, "grupo");
        return mapa("saiu", multicast.sair(usuario, grupo));
    }

    @MetodoRemoto
    public Object listarMembros(Map<String, Object> args) {
        return mapa("membros", multicast.grupo(Args.texto(args, "grupo")).getMembros());
    }

    @MetodoRemoto
    public Object historico(Map<String, Object> args) {
        return mapa("mensagens", multicast.grupo(Args.texto(args, "grupo")).getHistorico());
    }

    /** Somente administradores publicam. A mensagem sai por multicast para todos os membros. */
    @MetodoRemoto
    public Object enviarMensagem(Map<String, Object> args) {
        if (!(usuario instanceof Administrador)) {
            throw new SecurityException("Permissão negada: apenas administradores enviam mensagens");
        }
        String grupo = Args.texto(args, "grupo");
        TipoMensagem tipo = TipoMensagem.valueOf(Args.texto(args, "tipo"));
        String texto = Args.texto(args, "mensagem");
        return mapa("entregues", multicast.enviar(usuario, grupo, tipo, texto));
    }

    @MetodoRemoto
    public Object logout(Map<String, Object> args) {
        multicast.sairDeTodos(usuario);
        servidor.remover(nomeObjeto);
        return mapa("logout", true);
    }

    private static Map<String, Object> mapa(String chave, Object valor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(chave, valor);
        return m;
    }
}
