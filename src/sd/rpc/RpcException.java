package sd.rpc;

public class RpcException extends RuntimeException {

    public RpcException(String mensagem) {
        super(mensagem);
    }

    public RpcException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
