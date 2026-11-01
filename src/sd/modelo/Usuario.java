package sd.modelo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Entidade base de quem participa do sistema. Especializada em {@link Administrador} e {@link Assinante}. */
public abstract class Usuario implements Marshallable {

    private final String login;
    private final String nome;
    private final String senhaHash;

    protected Usuario(String login, String nome, String senhaHash) {
        this.login = login;
        this.nome = nome;
        this.senhaHash = senhaHash;
    }

    public abstract String papel();

    public String getLogin() {
        return login;
    }

    public String getNome() {
        return nome;
    }

    public boolean autentica(String senha) {
        return senhaHash.equals(hash(senha));
    }

    public static String hash(String senha) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(senha.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A senha nunca é incluída na representação externa. */
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("login", login);
        m.put("nome", nome);
        m.put("papel", papel());
        return m;
    }

    public static Usuario fromMap(Map<String, Object> m) {
        String login = (String) m.get("login");
        String nome = (String) m.get("nome");
        return switch ((String) m.get("papel")) {
            case "ADMINISTRADOR" -> new Administrador(login, nome, "");
            case "ASSINANTE" -> new Assinante(login, nome, "");
            default -> throw new IllegalArgumentException("Papel desconhecido: " + m.get("papel"));
        };
    }

    @Override
    public String toString() {
        return nome + " (" + login + ", " + papel() + ")";
    }
}
