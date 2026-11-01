package sd.modelo;

import java.util.Map;

/** Entidade que sabe se converter para a representação externa de dados (passagem por valor). */
public interface Marshallable {
    Map<String, Object> toMap();
}
