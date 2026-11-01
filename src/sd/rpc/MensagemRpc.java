package sd.rpc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Mensagem de requisição/resposta (seção 5.2 do livro):
 * messageType | requestId | objectReference | methodId | arguments.
 * Empacotada em binário; {@code arguments} carrega JSON.
 */
public record MensagemRpc(int messageType, int requestId, RemoteObjectRef objectReference,
                          String methodId, byte[] arguments) {

    public static final int REQUISICAO = 0;
    public static final int RESPOSTA = 1;

    public byte[] empacotar() {
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(buf)) {
            out.writeInt(messageType);
            out.writeInt(requestId);
            out.writeUTF(objectReference.host());
            out.writeInt(objectReference.porta());
            out.writeUTF(objectReference.nome());
            out.writeUTF(methodId);
            out.writeInt(arguments.length);
            out.write(arguments);
            out.flush();
            return buf.toByteArray();
        } catch (IOException e) {
            throw new RpcException("Falha ao empacotar mensagem", e);
        }
    }

    public static MensagemRpc desempacotar(byte[] dados) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(dados))) {
            int tipo = in.readInt();
            int id = in.readInt();
            String host = in.readUTF();
            int porta = in.readInt();
            String nome = in.readUTF();
            String metodo = in.readUTF();
            byte[] args = new byte[in.readInt()];
            in.readFully(args);
            return new MensagemRpc(tipo, id, new RemoteObjectRef(host, porta, nome), metodo, args);
        } catch (IOException e) {
            throw new RpcException("Mensagem RPC malformada", e);
        }
    }
}
