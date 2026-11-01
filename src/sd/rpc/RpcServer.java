package sd.rpc;

import sd.util.Json;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RemoteServer;
import java.rmi.server.ServerNotActiveException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lado servidor do protocolo requisição-resposta.
 *
 * <pre>
 * transmitir (thread RMI) ──► fila ──► getRequest ──► despacha ──► sendReply ──► libera transmitir
 * </pre>
 *
 * Em RMI não existe "porta do cliente" para responder: a resposta volta como retorno da própria
 * chamada {@link #transmitir}. Por isso sendReply entrega a resposta à chamada que aguarda,
 * identificada por (host, porta, requestId) do cliente.
 */
public class RpcServer implements RpcEndpoint {

    public static final String NOME_ENDPOINT = "rpc";
    private static final long TIMEOUT_SEGUNDOS = 30;

    private final String host;
    private final int porta;
    private final Map<String, Object> objetos = new ConcurrentHashMap<>();
    private final BlockingQueue<Requisicao> fila = new LinkedBlockingQueue<>();
    private final Map<String, CompletableFuture<byte[]>> pendentes = new ConcurrentHashMap<>();
    private volatile boolean ativo = true;

    public RpcServer(String host, int porta) throws RemoteException {
        this.host = host;
        this.porta = porta;
        System.setProperty("java.rmi.server.hostname", host);
        Registry registro = LocateRegistry.createRegistry(porta);
        RpcEndpoint stub = (RpcEndpoint) UnicastRemoteObject.exportObject(this, porta);
        registro.rebind(NOME_ENDPOINT, stub);
    }

    /** Sobe um servidor na primeira porta livre a partir de {@code portaInicial}. */
    public static RpcServer emPortaLivre(String host, int portaInicial, int tentativas) throws RemoteException {
        RemoteException ultima = null;
        for (int p = portaInicial; p < portaInicial + tentativas; p++) {
            try {
                return new RpcServer(host, p);
            } catch (RemoteException e) {
                ultima = e;
            }
        }
        throw new RemoteException("Nenhuma porta livre a partir de " + portaInicial, ultima);
    }

    public String getHost() {
        return host;
    }

    public int getPorta() {
        return porta;
    }

    // ------------------------------------------------------- objetos remotos

    public void registrar(String nome, Object objeto) {
        objetos.put(nome, objeto);
    }

    public void remover(String nome) {
        objetos.remove(nome);
    }

    public RemoteObjectRef referencia(String nome) {
        return new RemoteObjectRef(host, porta, nome);
    }

    // ---------------------------------------------------------- RMI (entrada)

    @Override
    public byte[] transmitir(byte[] requisicao, int portaCliente) throws RemoteException {
        String hostCliente;
        try {
            hostCliente = RemoteServer.getClientHost();
        } catch (ServerNotActiveException e) {
            hostCliente = "local";
        }
        int requestId = MensagemRpc.desempacotar(requisicao).requestId();
        String chave = chave(hostCliente, portaCliente, requestId);
        CompletableFuture<byte[]> resposta = new CompletableFuture<>();
        pendentes.put(chave, resposta);
        try {
            fila.add(new Requisicao(requisicao, hostCliente, portaCliente));
            return resposta.get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RemoteException("Tempo esgotado aguardando a execução da requisição " + requestId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Interrompido");
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RemoteException("Falha na execução", e.getCause());
        } finally {
            pendentes.remove(chave);
        }
    }

    // --------------------------------------------- protocolo requisição-resposta

    /** Bloqueia até chegar uma requisição; devolve null se o servidor foi encerrado. */
    public Requisicao getRequest() {
        while (ativo) {
            try {
                Requisicao r = fila.poll(500, TimeUnit.MILLISECONDS);
                if (r != null) return r;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    public void sendReply(byte[] resposta, String hostCliente, int portaCliente) {
        int requestId = MensagemRpc.desempacotar(resposta).requestId();
        CompletableFuture<byte[]> f = pendentes.get(chave(hostCliente, portaCliente, requestId));
        if (f != null) f.complete(resposta);
    }

    /** Inicia {@code threads} trabalhadores que repetem getRequest → executa → sendReply. */
    public void iniciar(int threads) {
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(this::laco, "rpc-worker-" + porta + "-" + i);
            t.setDaemon(true);
            t.start();
        }
    }

    public void encerrar() {
        ativo = false;
        try {
            UnicastRemoteObject.unexportObject(this, true);
            UnicastRemoteObject.unexportObject(LocateRegistry.getRegistry(porta), true);
        } catch (Exception ignorada) {
            // já encerrado
        }
    }

    private void laco() {
        Requisicao r;
        while ((r = getRequest()) != null) {
            sendReply(executar(r.mensagem()), r.hostCliente(), r.portaCliente());
        }
    }

    private byte[] executar(byte[] dados) {
        MensagemRpc req = MensagemRpc.desempacotar(dados);
        Map<String, Object> envelope = new LinkedHashMap<>();
        try {
            Object alvo = objetos.get(req.objectReference().nome());
            if (alvo == null) {
                throw new RpcException("Objeto remoto inexistente: " + req.objectReference().nome());
            }
            Object resultado = Despachante.invocar(alvo, req.methodId(), Json.parseObjeto(req.arguments()));
            envelope.put("ok", true);
            envelope.put("resultado", resultado);
        } catch (Throwable t) {
            envelope.clear();
            envelope.put("ok", false);
            envelope.put("erro", t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        }
        return new MensagemRpc(MensagemRpc.RESPOSTA, req.requestId(), req.objectReference(), req.methodId(),
                Json.bytes(envelope)).empacotar();
    }

    private static String chave(String host, int porta, int requestId) {
        return host + ":" + porta + "#" + requestId;
    }
}
