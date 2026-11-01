package sd.teste;

import sd.cliente.ClienteRpc;
import sd.modelo.Alerta;
import sd.modelo.Mensagem;
import sd.modelo.TipoMensagem;
import sd.rpc.MensagemRpc;
import sd.rpc.RemoteObjectRef;
import sd.rpc.RpcException;
import sd.servidor.ServidorMain;
import sd.util.Json;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/** Testes de integração sem framework: sobem servidor e clientes RMI reais e verificam o comportamento. */
public class TesteIntegracao {

    private static final String HOST = "localhost";
    private static final int PORTA_SERVIDOR = 21099;
    private static int falhas = 0;

    public static void main(String[] args) throws Exception {
        testarJson();
        testarEmpacotamento();

        ServidorMain.iniciar(HOST, PORTA_SERVIDOR);

        List<Mensagem> deAna = new CopyOnWriteArrayList<>();
        List<Mensagem> deBruno = new CopyOnWriteArrayList<>();
        List<Mensagem> deCarla = new CopyOnWriteArrayList<>();
        ClienteRpc ana = novo(30000, deAna);
        ClienteRpc bruno = novo(30100, deBruno);
        ClienteRpc carla = novo(30200, deCarla);
        ClienteRpc admin = novo(30300, new CopyOnWriteArrayList<>());

        // ---- autenticação (TCP/RMI unicast)
        verifica("login inválido é rejeitado", lancaRpc(() -> ana.login("ana", "errada")));
        verifica("usuário inexistente é rejeitado", lancaRpc(() -> ana.login("ze", "123")));
        verifica("login de assinante", ana.login("ana", "123").papel().equals("ASSINANTE"));
        bruno.login("bruno", "123");
        carla.login("carla", "123");
        verifica("login de administrador", admin.login("admin", "admin123").papel().equals("ADMINISTRADOR"));
        verifica("operação sem login é recusada", lancaIllegal(() -> novoSemLogin().listarGrupos()));

        // ---- joinGroup / leaveGroup
        verifica("listar grupos", ana.listarGrupos().size() == 2);
        ana.entrar("geral");
        bruno.entrar("geral");
        verifica("grupo com 2 membros", ana.membros("geral").size() == 2);
        verifica("grupo inexistente", lancaRpc(() -> ana.entrar("nao-existe")));

        // ---- permissão
        verifica("assinante não pode enviar", lancaRpc(() -> ana.enviar("geral", "ALERTA", "x")));

        // ---- envio para todos os membros, e só para eles
        CountDownLatch dois = new CountDownLatch(2);
        deAna.clear();
        int entregues = admin.enviar("geral", "ALERTA", "Servidor reiniciará às 22h");
        esperar(() -> deAna.size() == 1 && deBruno.size() == 1);
        verifica("alerta entregue a 2 membros", entregues == 2);
        verifica("ana recebeu", deAna.size() == 1 && deAna.get(0) instanceof Alerta);
        verifica("bruno recebeu", deBruno.size() == 1);
        verifica("carla (fora do grupo) não recebeu", deCarla.isEmpty());
        verifica("conteúdo preservado (passagem por valor)",
                deAna.get(0).getTexto().equals("Servidor reiniciará às 22h")
                        && deAna.get(0).getAutor().getLogin().equals("admin")
                        && deAna.get(0).tipo() == TipoMensagem.ALERTA);

        // ---- leaveGroup
        verifica("bruno sai do grupo", bruno.sair("geral"));
        verifica("sair de novo retorna falso", !bruno.sair("geral"));
        entregues = admin.enviar("geral", "NOTIFICACAO", "Só a Ana recebe");
        esperar(() -> deAna.size() == 2);
        verifica("após sair, só 1 entrega", entregues == 1 && deBruno.size() == 1 && deAna.size() == 2);

        // ---- histórico
        verifica("histórico tem 2 mensagens", ana.historico("geral").size() == 2);

        // ---- envio concorrente de dois remetentes
        deAna.clear();
        int porThread = 15;
        Thread t1 = new Thread(() -> rajada(admin, porThread, "A"));
        ClienteRpc admin2 = novo(30400, new CopyOnWriteArrayList<>());
        admin2.login("admin", "admin123");
        Thread t2 = new Thread(() -> rajada(admin2, porThread, "B"));
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        esperar(() -> deAna.size() == 2 * porThread);
        verifica("30 mensagens concorrentes recebidas", deAna.size() == 2 * porThread);

        // ---- cliente que cai é removido do grupo no próximo envio
        carla.entrar("alertas");
        carla.derrubarSemLogout();
        entregues = admin.enviar("alertas", "ATUALIZACAO", "ninguém ouve");
        verifica("entrega a ouvinte caído falha sem travar", entregues == 0);
        verifica("ouvinte caído removido do grupo", admin.membros("alertas").isEmpty());

        // ---- objeto remoto de sessão deixa de existir após logout (referência inválida)
        ClienteRpc efemero = novo(30500, new CopyOnWriteArrayList<>());
        efemero.login("ana", "123");
        efemero.logout();
        verifica("sessão encerrada não responde", lancaIllegal(efemero::listarGrupos));

        System.out.println(falhas == 0 ? "\nTODOS OS TESTES PASSARAM" : "\n" + falhas + " TESTE(S) FALHARAM");
        System.exit(falhas == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------ testes unitários

    private static void testarJson() {
        Map<String, Object> m = Json.parseObjeto("{\"a\":[1,2.5,true,null,\"x\\n\\u00e9\"],\"b\":{\"c\":\"\\\"\"}}".getBytes());
        verifica("JSON: parse", ((List<?>) m.get("a")).size() == 5 && ((Map<?, ?>) m.get("b")).get("c").equals("\""));
        Object volta = Json.parse(Json.stringify(m));
        verifica("JSON: ida e volta", volta.equals(m));
        verifica("JSON: acentos e escapes", Json.parse(Json.stringify("ção\t\"\\")).equals("ção\t\"\\"));
        verifica("JSON: inválido é rejeitado", lancaIllegal(() -> Json.parse("{\"a\":")));
    }

    private static void testarEmpacotamento() {
        MensagemRpc original = new MensagemRpc(MensagemRpc.REQUISICAO, 42, new RemoteObjectRef("h", 1, "obj"),
                "metodo", "{\"k\":1}".getBytes());
        MensagemRpc volta = MensagemRpc.desempacotar(original.empacotar());
        verifica("empacotamento RPC: ida e volta", volta.messageType() == 0 && volta.requestId() == 42
                && volta.objectReference().equals(original.objectReference())
                && volta.methodId().equals("metodo") && new String(volta.arguments()).equals("{\"k\":1}"));
    }

    // ------------------------------------------------------------------ utilidades

    private static ClienteRpc novo(int portaLocal, List<Mensagem> destino) throws Exception {
        return new ClienteRpc(HOST, portaLocal, HOST, PORTA_SERVIDOR, destino::add);
    }

    private static ClienteRpc novoSemLogin() throws Exception {
        return novo(30600, new CopyOnWriteArrayList<>());
    }

    private static void rajada(ClienteRpc c, int n, String prefixo) {
        for (int i = 0; i < n; i++) c.enviar("geral", "NOTIFICACAO", prefixo + i);
    }

    private static void esperar(java.util.function.BooleanSupplier condicao) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5000;
        while (!condicao.getAsBoolean() && System.currentTimeMillis() < limite) Thread.sleep(20);
    }

    private interface ComExcecao {
        void executar() throws Exception;
    }

    private static boolean lancaRpc(ComExcecao r) {
        try {
            r.executar();
            return false;
        } catch (RpcException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean lancaIllegal(ComExcecao r) {
        try {
            r.executar();
            return false;
        } catch (IllegalStateException | IllegalArgumentException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void verifica(String nome, boolean ok) {
        System.out.println((ok ? "  [OK]    " : "  [FALHA] ") + nome);
        if (!ok) falhas++;
    }
}
