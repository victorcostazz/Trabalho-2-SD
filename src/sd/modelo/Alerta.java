package sd.modelo;

public class Alerta extends Mensagem {

    public Alerta(long id, String texto, Usuario autor, long timestamp) {
        super(id, texto, autor, timestamp);
    }

    @Override
    public TipoMensagem tipo() {
        return TipoMensagem.ALERTA;
    }

    @Override
    protected String rotulo() {
        return "[!! ALERTA !!]";
    }
}
