package sd.rpc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um método invocável remotamente. Assinatura obrigatória:
 * {@code Object metodo(Map<String,Object> argumentos)}; o nome do método é o methodId.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MetodoRemoto {
}
