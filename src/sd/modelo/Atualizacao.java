package sd.modelo;

public class Atualizacao extends Mensagem {

    public Atualizacao(long id, String texto, Usuario autor, long timestamp) {
        super(id, texto, autor, timestamp);
    }

    @Override
    public TipoMensagem tipo() {
        return TipoMensagem.ATUALIZACAO;
    }

    @Override
    protected String rotulo() {
        return "[ATUALIZAÇÃO]";
    }
}
