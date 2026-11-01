package sd.modelo;

public enum TipoMensagem {
    NOTIFICACAO,
    ALERTA,
    ATUALIZACAO;

    public Mensagem criar(long id, String texto, Usuario autor, long timestamp) {
        return switch (this) {
            case NOTIFICACAO -> new Notificacao(id, texto, autor, timestamp);
            case ALERTA -> new Alerta(id, texto, autor, timestamp);
            case ATUALIZACAO -> new Atualizacao(id, texto, autor, timestamp);
        };
    }
}
