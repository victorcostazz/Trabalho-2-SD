package sd.rpc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Referência a um objeto remoto: onde fica o processo (host + porta do registro RMI)
 * e o nome do objeto dentro dele. É o que trafega na passagem por referência.
 */
public record RemoteObjectRef(String host, int porta, String nome) {

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("host", host);
        m.put("porta", porta);
        m.put("nome", nome);
        return m;
    }

    public static RemoteObjectRef fromMap(Map<String, Object> m) {
        return new RemoteObjectRef((String) m.get("host"), ((Number) m.get("porta")).intValue(),
                (String) m.get("nome"));
    }

    @Override
    public String toString() {
        return nome + "@" + host + ":" + porta;
    }
}
