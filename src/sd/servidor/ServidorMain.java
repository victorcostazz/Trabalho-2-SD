package sd.servidor;

import sd.modelo.Administrador;
import sd.modelo.Assinante;
import sd.modelo.GrupoMulticast;
import sd.modelo.Usuario;
import sd.rpc.RpcClient;
import sd.rpc.RpcServer;

/** Uso: ServidorMain [host=localhost] [porta=1099] */
public class ServidorMain {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 1099;

        RpcServer servidor = iniciar(host, porta);
        System.out.println("Servidor RMI pronto em " + host + ":" + porta);
        System.out.println("Usuários: admin/admin123 (ADMINISTRADOR), ana/123, bruno/123, carla/123 (ASSINANTES)");
        System.out.println("Grupos: geral, alertas");
        Thread.currentThread().join();
    }

    /** Monta o servidor completo; usado também pelos testes. */
    public static RpcServer iniciar(String host, int porta) throws Exception {
        RpcServer servidor = new RpcServer(host, porta);
        ServicoMulticast multicast = new ServicoMulticast(new RpcClient(porta));
        multicast.criarGrupo(new GrupoMulticast("geral", "230.0.0.1", 4446));
        multicast.criarGrupo(new GrupoMulticast("alertas", "230.0.0.2", 4447));

        ServicoAutenticacao auth = new ServicoAutenticacao(multicast, servidor);
        auth.cadastrar(new Administrador("admin", "Administrador", Usuario.hash("admin123")));
        auth.cadastrar(new Assinante("ana", "Ana", Usuario.hash("123")));
        auth.cadastrar(new Assinante("bruno", "Bruno", Usuario.hash("123")));
        auth.cadastrar(new Assinante("carla", "Carla", Usuario.hash("123")));

        servidor.registrar(ServicoAutenticacao.NOME, auth);
        servidor.iniciar(16);
        return servidor;
    }
}
