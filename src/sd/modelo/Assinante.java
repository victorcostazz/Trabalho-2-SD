package sd.modelo;

/** Usuário que entra/sai de grupos e recebe as mensagens. */
public class Assinante extends Usuario {

    public Assinante(String login, String nome, String senhaHash) {
        super(login, nome, senhaHash);
    }

    @Override
    public String papel() {
        return "ASSINANTE";
    }
}
