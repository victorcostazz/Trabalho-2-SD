package sd.modelo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grupo ao qual os assinantes aderem. Agrega os {@link Usuario} membros e o histórico de {@link Mensagem}.
 * O endereço classe D (224.0.0.0 – 239.255.255.255) e a porta continuam sendo atributos do serviço,
 * mas neste trabalho a entrega é feita por RMI, sem sockets.
 */
public class GrupoMulticast implements Marshallable {

    private final String nome;
    private final String endereco;
    private final int porta;
    private final List<Usuario> membros = new ArrayList<>();
    private final List<Mensagem> historico = new ArrayList<>();

    public GrupoMulticast(String nome, String endereco, int porta) {
        if (!classeD(endereco)) {
            throw new IllegalArgumentException("Endereço não é classe D: " + endereco);
        }
        this.nome = nome;
        this.endereco = endereco;
        this.porta = porta;
    }

    private static boolean classeD(String ip) {
        String[] p = ip.split("\\.");
        if (p.length != 4) return false;
        try {
            int primeiro = Integer.parseInt(p[0]);
            for (String parte : p) {
                int v = Integer.parseInt(parte);
                if (v < 0 || v > 255) return false;
            }
            return primeiro >= 224 && primeiro <= 239;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public String getNome() {
        return nome;
    }

    public synchronized boolean adicionarMembro(Usuario u) {
        if (estaNoGrupo(u.getLogin())) return false;
        membros.add(u);
        return true;
    }

    public synchronized boolean removerMembro(String login) {
        return membros.removeIf(u -> u.getLogin().equals(login));
    }

    public synchronized boolean estaNoGrupo(String login) {
        return membros.stream().anyMatch(u -> u.getLogin().equals(login));
    }

    public synchronized void registrarMensagem(Mensagem m) {
        historico.add(m);
    }

    public synchronized List<Usuario> getMembros() {
        return new ArrayList<>(membros);
    }

    public synchronized List<Mensagem> getHistorico() {
        return new ArrayList<>(historico);
    }

    /** Resumo do grupo, sem membros nem histórico. */
    public Map<String, Object> resumo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", nome);
        m.put("endereco", endereco);
        m.put("porta", porta);
        return m;
    }

    @Override
    public synchronized Map<String, Object> toMap() {
        Map<String, Object> m = resumo();
        m.put("membros", new ArrayList<>(membros));
        m.put("historico", new ArrayList<>(historico));
        return m;
    }
}
