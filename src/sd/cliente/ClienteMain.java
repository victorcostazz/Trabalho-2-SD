package sd.cliente;

import sd.modelo.Administrador;
import sd.modelo.Mensagem;
import sd.modelo.Usuario;

import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Cliente em modo texto. Duas threads: a de exibição imprime as mensagens assim que o servidor
 * as entrega (via OuvinteRemoto); a principal cuida da interação com o usuário.
 *
 * Uso: ClienteMain [hostServidor=localhost] [portaServidor=1099] [hostLocal=localhost] [portaLocal=1100]
 */
public class ClienteMain {

    public static void main(String[] args) throws Exception {
        String hostServidor = args.length > 0 ? args[0] : "localhost";
        int portaServidor = args.length > 1 ? Integer.parseInt(args[1]) : 1099;
        String hostLocal = args.length > 2 ? args[2] : "localhost";
        int portaLocal = args.length > 3 ? Integer.parseInt(args[3]) : 1100;

        BlockingQueue<Mensagem> recebidas = new LinkedBlockingQueue<>();
        Thread exibicao = new Thread(() -> {
            try {
                while (true) System.out.println("\n>> " + recebidas.take().formatar());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "exibicao");
        exibicao.setDaemon(true);
        exibicao.start();

        Scanner in = new Scanner(System.in);
        try (ClienteRpc cliente = new ClienteRpc(hostLocal, portaLocal, hostServidor, portaServidor, recebidas::add)) {
            System.out.print("Usuário: ");
            String login = in.nextLine().trim();
            System.out.print("Senha: ");
            String senha = in.nextLine().trim();
            Usuario u = cliente.login(login, senha);
            System.out.println("Autenticado como " + u);
            menu(cliente, u, in);
        } catch (RuntimeException e) {
            System.err.println("Erro: " + e.getMessage());
        }
    }

    private static void menu(ClienteRpc c, Usuario u, Scanner in) {
        boolean admin = u instanceof Administrador;
        while (true) {
            System.out.println("\n1) listar grupos  2) entrar (joinGroup)  3) sair (leaveGroup)  4) membros  5) histórico"
                    + (admin ? "  6) enviar mensagem" : "") + "  0) logout");
            System.out.print("> ");
            if (!in.hasNextLine()) return;
            String op = in.nextLine().trim();
            try {
                switch (op) {
                    case "0" -> {
                        return;
                    }
                    case "1" -> {
                        for (Map<String, Object> g : c.listarGrupos()) {
                            System.out.println(" - " + g.get("nome") + " (" + g.get("endereco") + ":" + g.get("porta") + ")");
                        }
                    }
                    case "2" -> {
                        c.entrar(ler(in, "Grupo"));
                        System.out.println("Você entrou no grupo.");
                    }
                    case "3" -> System.out.println(c.sair(ler(in, "Grupo")) ? "Você saiu do grupo." : "Você não estava no grupo.");
                    case "4" -> c.membros(ler(in, "Grupo")).forEach(m -> System.out.println(" - " + m));
                    case "5" -> c.historico(ler(in, "Grupo")).forEach(m -> System.out.println(" " + m.formatar()));
                    case "6" -> {
                        if (!admin) {
                            System.out.println("Opção inválida.");
                            break;
                        }
                        String grupo = ler(in, "Grupo");
                        String tipo = ler(in, "Tipo (NOTIFICACAO, ALERTA, ATUALIZACAO)").toUpperCase();
                        String texto = ler(in, "Mensagem");
                        System.out.println("Entregue a " + c.enviar(grupo, tipo, texto) + " membro(s).");
                    }
                    default -> System.out.println("Opção inválida.");
                }
            } catch (RuntimeException e) {
                System.out.println("Erro: " + e.getMessage());
            }
        }
    }

    private static String ler(Scanner in, String rotulo) {
        System.out.print(rotulo + ": ");
        return in.hasNextLine() ? in.nextLine().trim() : "";
    }
}
