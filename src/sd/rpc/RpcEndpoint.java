package sd.rpc;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Único ponto de entrada RMI de cada processo. Carrega mensagens já empacotadas:
 * toda a semântica (objeto, método, argumentos) fica no protocolo requisição-resposta.
 */
public interface RpcEndpoint extends Remote {

    /**
     * @param requisicao  mensagem de requisição empacotada
     * @param portaCliente porta RMI do processo chamador (junto do host, identifica quem espera a resposta)
     * @return mensagem de resposta empacotada
     */
    byte[] transmitir(byte[] requisicao, int portaCliente) throws RemoteException;
}
