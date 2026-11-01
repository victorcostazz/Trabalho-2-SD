package sd.modelo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mensagem entregue aos membros de um grupo. Agrega o {@link Usuario} autor.
 * Formato externo (JSON): {"tipo","mensagem","timestamp"} + "id" e "autor".
 */
public abstract class Mensagem implements Marshallable {

    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final long id;
    private final String texto;
    private final Usuario autor;
    private final long timestamp;

    protected Mensagem(long id, String texto, Usuario autor, long timestamp) {
        this.id = id;
        this.texto = texto;
        this.autor = autor;
        this.timestamp = timestamp;
    }

    public abstract TipoMensagem tipo();

    /** Rótulo usado na exibição; cada subtipo define o seu. */
    protected abstract String rotulo();

    public long getId() {
        return id;
    }

    public String getTexto() {
        return texto;
    }

    public Usuario getAutor() {
        return autor;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String formatar() {
        return rotulo() + " " + texto + "  — " + autor.getNome() + " às "
                + HORA.format(Instant.ofEpochMilli(timestamp));
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("tipo", tipo().name());
        m.put("mensagem", texto);
        m.put("timestamp", timestamp);
        m.put("autor", autor.toMap());
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Mensagem fromMap(Map<String, Object> m) {
        TipoMensagem tipo = TipoMensagem.valueOf((String) m.get("tipo"));
        Usuario autor = Usuario.fromMap((Map<String, Object>) m.get("autor"));
        return tipo.criar(((Number) m.get("id")).longValue(), (String) m.get("mensagem"), autor,
                ((Number) m.get("timestamp")).longValue());
    }
}
