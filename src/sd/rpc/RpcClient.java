package sd.rpc;

import sd.util.Json;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Lado cliente do protocolo requisição-resposta. */
public class RpcClient {

    private final int portaLocal;
    private final AtomicInteger proximoId = new AtomicInteger(1);
    private final Map<String, RpcEndpoint> endpoints = new ConcurrentHashMap<>();

    /** @param portaLocal porta RMI do próprio processo (identifica o chamador para o servidor) */
    public RpcClient(int portaLocal) {
        this.portaLocal = portaLocal;
    }

    /**
     * Empacota a requisição, envia ao objeto remoto e devolve os bytes do envelope de resposta
     * (já desempacotado da mensagem RPC). Bloqueia até a resposta chegar.
     */
    public byte[] doOperation(RemoteObjectRef o, String methodId, byte[] arguments) {
        int id = proximoId.getAndIncrement();
        byte[] requisicao = new MensagemRpc(MensagemRpc.REQUISICAO, id, o, methodId, arguments).empacotar();
        String chave = o.host() + ":" + o.porta();
        try {
            byte[] bruta = endpoint(chave, o).transmitir(requisicao, portaLocal);
            MensagemRpc resposta = MensagemRpc.desempacotar(bruta);
            if (resposta.messageType() != MensagemRpc.RESPOSTA || resposta.requestId() != id) {
                throw new RpcException("Resposta inesperada para a requisição " + id);
            }
            return resposta.arguments();
        } catch (RemoteException e) {
            endpoints.remove(chave);
            throw new RpcException("Falha ao chamar " + o + "." + methodId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Conveniência sobre doOperation: serializa os argumentos em JSON, chama e interpreta o envelope.
     * Lança {@link RpcException} se o servidor respondeu com erro.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> chamar(RemoteObjectRef o, String metodo, Map<String, Object> argumentos) {
        Map<String, Object> envelope = Json.parseObjeto(doOperation(o, metodo, Json.bytes(argumentos)));
        if (!Boolean.TRUE.equals(envelope.get("ok"))) {
            throw new RpcException(String.valueOf(envelope.get("erro")));
        }
        Object resultado = envelope.get("resultado");
        return resultado instanceof Map ? (Map<String, Object>) resultado : Map.of();
    }

    private RpcEndpoint endpoint(String chave, RemoteObjectRef o) throws RemoteException {
        RpcEndpoint ep = endpoints.get(chave);
        if (ep != null) return ep;
        try {
            ep = (RpcEndpoint) LocateRegistry.getRegistry(o.host(), o.porta()).lookup(RpcServer.NOME_ENDPOINT);
        } catch (java.rmi.NotBoundException e) {
            throw new RemoteException("Endpoint RPC não encontrado em " + chave);
        }
        endpoints.put(chave, ep);
        return ep;
    }
}
