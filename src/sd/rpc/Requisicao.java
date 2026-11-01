package sd.rpc;

/** Requisição retirada da fila por getRequest, com a origem para o sendReply. */
public record Requisicao(byte[] mensagem, String hostCliente, int portaCliente) {
}
