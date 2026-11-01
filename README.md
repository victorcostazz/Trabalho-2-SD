# Trabalho 2 – RPC/RMI: grupo de mensagens (multicast) sobre protocolo requisição-resposta

QXD0043 – Sistemas Distribuídos · UFC Quixadá · Prof. Antonio Rafael Braga

Reimplementação da 4ª questão do Trabalho 1 (autenticação + envio de mensagens NOTIFICAÇÃO / ALERTA /
ATUALIZAÇÃO a um grupo de clientes) usando **Java RMI** como transporte e o **protocolo
requisição-resposta** da seção 5.2 do livro-texto (`doOperation`, `getRequest`, `sendReply`).
**Nenhum socket é criado no código**; a representação externa de dados é **JSON** (codec próprio, sem
dependências).

O relatório completo está em [RELATORIO.md](RELATORIO.md).

## Requisitos

- JDK 17 ou superior (testado com OpenJDK 21). Não há Maven, Gradle nem bibliotecas externas.

## Como compilar e testar

```bash
./build.sh          # compila src/ para out/
./run-testes.sh     # compila e roda os testes de integração (27 verificações)
```

## Como executar a demonstração

Em terminais separados, nesta ordem:

```bash
./run-servidor.sh                                   # terminal 1  (porta 1099)
./run-cliente.sh localhost 1099 localhost 1100      # terminal 2  -> login: ana / 123
./run-cliente.sh localhost 1099 localhost 1200      # terminal 3  -> login: bruno / 123
./run-cliente.sh localhost 1099 localhost 1300      # terminal 4  -> login: admin / admin123
```

Argumentos do cliente: `hostServidor portaServidor hostLocal portaLocal`. Cada cliente precisa de uma
**porta local própria**, porque também hospeda um objeto remoto (o ouvinte) que o servidor invoca. Se a porta
informada estiver ocupada, o cliente tenta as seguintes.

Roteiro:

1. Ana e Bruno: opção `2` (joinGroup), grupo `geral`.
2. Admin: opção `6`, grupo `geral`, tipo `ALERTA`, texto livre. Ana e Bruno veem a mensagem na hora.
3. Bruno: opção `3` (leaveGroup). Nova mensagem do admin chega só à Ana.
4. Qualquer um: opção `5` mostra o histórico do grupo.

Usuários pré-cadastrados: `admin/admin123` (ADMINISTRADOR), `ana/123`, `bruno/123`, `carla/123`
(ASSINANTES). Grupos: `geral` (230.0.0.1:4446) e `alertas` (230.0.0.2:4447).

Para rodar em máquinas diferentes, passe o IP real nos argumentos de host (`hostLocal` do cliente e `host` do
servidor), pois o RMI o anuncia nas referências remotas.

## Estrutura

```
src/sd/
  rpc/        protocolo requisição-resposta sobre RMI
  modelo/     entidades (Usuario, Mensagem, GrupoMulticast, ...)
  servidor/   serviços e objeto remoto de sessão
  cliente/    núcleo do cliente, ouvinte remoto e CLI
  util/       codec JSON
  teste/      testes de integração
```

| Classe | Papel |
|---|---|
| `rpc.RpcClient` | `doOperation`: empacota a requisição, envia, desempacota a resposta |
| `rpc.RpcServer` | `getRequest` / `sendReply`, tabela de objetos remotos, threads trabalhadoras |
| `rpc.RpcEndpoint` | única interface RMI (`transmitir(byte[], int)`) |
| `rpc.MensagemRpc` | `messageType, requestId, objectReference, methodId, arguments` |
| `rpc.RemoteObjectRef` | referência remota (host, porta, nome) |
| `servidor.ServicoAutenticacao` | `login` (cria a sessão e a devolve por referência) |
| `servidor.SessaoRemota` | `listarGrupos, entrarGrupo, sairGrupo, listarMembros, historico, enviarMensagem, logout` |
| `servidor.ServicoMulticast` | grupos, membros e envio concorrente |
| `cliente.OuvinteRemoto` | `receber` (invocado pelo servidor) |

## Limitações conhecidas

- O endereço classe D e a porta do grupo são atributos do `GrupoMulticast`, mas **não há multicast IP real**:
  como o enunciado proíbe sockets, a entrega é feita por uma chamada RMI a cada membro, em paralelo.
- As senhas dos usuários de demonstração estão no código (`ServidorMain`); guardadas como SHA-256 sem sal.
- Não há persistência: grupos, membros e histórico vivem na memória do servidor.
