package sd.modelo;

/** Usuário que pode publicar mensagens nos grupos. */
public class Administrador extends Usuario {

    public Administrador(String login, String nome, String senhaHash) {
        super(login, nome, senhaHash);
    }

    @Override
    public String papel() {
        return "ADMINISTRADOR";
    }
}
