package sd.modelo;

public class Notificacao extends Mensagem {

    public Notificacao(long id, String texto, Usuario autor, long timestamp) {
        super(id, texto, autor, timestamp);
    }

    @Override
    public TipoMensagem tipo() {
        return TipoMensagem.NOTIFICACAO;
    }

    @Override
    protected String rotulo() {
        return "[NOTIFICAÇÃO]";
    }
}
