package sd.rpc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/** "Select object / execute method" do servidor: acha o método anotado e o executa. */
final class Despachante {

    private Despachante() {
    }

    static Object invocar(Object alvo, String methodId, Map<String, Object> args) throws Exception {
        for (Method m : alvo.getClass().getMethods()) {
            if (m.isAnnotationPresent(MetodoRemoto.class) && m.getName().equals(methodId)) {
                try {
                    return m.invoke(alvo, args);
                } catch (InvocationTargetException e) {
                    Throwable causa = e.getCause();
                    if (causa instanceof Exception ex) throw ex;
                    throw e;
                }
            }
        }
        throw new RpcException("Método remoto inexistente: " + methodId);
    }
}
