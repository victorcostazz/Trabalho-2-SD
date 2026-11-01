# Relatório – Trabalho 2: RPC/RMI

**Disciplina:** QXD0043 – Sistemas Distribuídos · **Professor:** Antonio Rafael Braga
**Aluno(s):** _preencher_ · **Repositório:** _preencher URL_

## 1. Serviço remoto implementado

É a 4ª questão do Trabalho 1 (comunicação multicast com autenticação), refeita sobre RPC/RMI. Usuários
fazem login, entram em grupos (`joinGroup`) e saem deles (`leaveGroup`). Administradores publicam mensagens
dos tipos **NOTIFICAÇÃO**, **ALERTA** e **ATUALIZAÇÃO**, e o servidor as entrega **concorrentemente a todos
os membros do grupo**, que as exibem em tempo real.

No Trabalho 1 a autenticação usava TCP e a entrega usava UDP multicast. Aqui o enunciado proíbe sockets, então
ambas passam por chamadas remotas RMI (seção 7 discute a consequência).

## 2. Arquitetura

```
 CLIENTE (processo)                                   SERVIDOR (processo)
 ┌───────────────────────────┐                        ┌───────────────────────────────────┐
 │ ClienteMain (thread UI)   │                        │ RpcServer                         │
 │ ClienteRpc ── RpcClient ──┼── doOperation ────────►│  transmitir ─► fila               │
 │                           │   (RMI: transmitir)    │  getRequest ◄─ threads workers    │
 │ RpcServer (próprio)       │                        │  Despachante ─► objeto/método     │
 │  └ OuvinteRemoto.receber ◄┼── doOperation ─────────┼─ ServicoMulticast (envio paralelo)│
 │ thread de exibição        │   (callback)           │  sendReply ─► libera o chamador   │
 └───────────────────────────┘                        │ ServicoAutenticacao, SessaoRemota │
                                                      └───────────────────────────────────┘
```

Todo processo (cliente ou servidor) tem um `RpcServer` e um `RpcClient`. O cliente precisa do seu próprio
`RpcServer` porque hospeda o `OuvinteRemoto`, que o servidor invoca para entregar mensagens.

## 3. Protocolo requisição-resposta (seção 5.2)

Há **uma única interface RMI**, `RpcEndpoint.transmitir(byte[] requisicao, int portaCliente)`. Ela só
transporta mensagens já empacotadas; objeto, método e argumentos viajam dentro do protocolo do livro:

| Campo de `MensagemRpc` | Tipo | Conteúdo |
|---|---|---|
| `messageType` | int | 0 = requisição, 1 = resposta |
| `requestId` | int | identifica a chamada |
| `objectReference` | `RemoteObjectRef` | host, porta e nome do objeto remoto |
| `methodId` | String | nome do método (permitido pelo enunciado) |
| `arguments` | byte[] | JSON (requisição) ou envelope JSON (resposta) |

Fluxo de uma chamada:

1. **Cliente – `doOperation(ref, methodId, args)`**: empacota a requisição, localiza o endpoint pelo registro
   RMI e chama `transmitir`. Bloqueia.
2. **Servidor – `transmitir`** (thread do RMI): coloca a requisição numa fila e aguarda a resposta.
3. **Servidor – `getRequest`**: uma thread trabalhadora retira a requisição da fila.
4. **Servidor – despacho**: acha o objeto pelo nome, o método anotado com `@MetodoRemoto` e o executa.
5. **Servidor – `sendReply`**: empacota a resposta e a entrega à chamada que aguarda; `transmitir` retorna.
6. **Cliente**: desempacota a resposta, confere tipo e `requestId`.

A resposta é um envelope JSON: `{"ok":true,"resultado":{...}}` ou `{"ok":false,"erro":"..."}`. O erro vira
`RpcException` no cliente.

## 4. Mapeamento dos requisitos

### 4.1 Classes de entidade (mínimo 4) — **8**

`Usuario`, `Administrador`, `Assinante`, `Mensagem`, `Notificacao`, `Alerta`, `Atualizacao`,
`GrupoMulticast` (mais o enum `TipoMensagem`).

### 4.2 Agregação, "tem-um" (mínimo 2) — **3**

| Todo | Parte |
|---|---|
| `GrupoMulticast` | lista de `Usuario` (membros) |
| `GrupoMulticast` | lista de `Mensagem` (histórico) |
| `Mensagem` | `Usuario` (autor) |

### 4.3 Extensão, "é-um" (mínimo 2) — **2 hierarquias, 5 relações**

- `Administrador` e `Assinante` estendem `Usuario`.
- `Notificacao`, `Alerta` e `Atualizacao` estendem `Mensagem`.

O polimorfismo é usado de fato: `papel()` e `rotulo()` são abstratos e cada subclasse define o seu.

### 4.4 Métodos de invocação remota (mínimo 4) — **9**

| Objeto remoto | Métodos |
|---|---|
| `ServicoAutenticacao` | `login` |
| `SessaoRemota` | `listarGrupos`, `entrarGrupo`, `sairGrupo`, `listarMembros`, `historico`, `enviarMensagem`, `logout` |
| `OuvinteRemoto` (no cliente) | `receber` |

### 4.5 Passagem por referência (objetos remotos)

Objetos remotos nunca trafegam; trafega uma `RemoteObjectRef` (host, porta, nome) e quem a recebe chama o
objeto original por `doOperation`:

- **`login`** cria uma `SessaoRemota`, registra-a no servidor com nome imprevisível (`sessao-<UUID>`) e
  devolve a referência. O cliente passa a invocar todas as operações nela, em nome do usuário autenticado.
- **`entrarGrupo`** recebe a referência do `OuvinteRemoto` do cliente. O servidor a guarda e invoca `receber`
  nela a cada mensagem (callback).

### 4.6 Passagem por valor (objetos locais) e representação externa de dados

`Usuario`, `Mensagem` e `GrupoMulticast` são copiados: cada entidade implementa `Marshallable.toMap()` e tem
`fromMap`, e o codec `Json` escreve/lê o resultado. O formato da mensagem segue o sugerido no enunciado:

```json
{"id":3,"tipo":"ALERTA","mensagem":"Manutencao as 22h","timestamp":1793000829000,
 "autor":{"login":"admin","nome":"Administrador","papel":"ADMINISTRADOR"}}
```

A senha nunca é incluída na representação externa de `Usuario`. Escolhi JSON (aceito pelo enunciado) com
codec próprio, em vez de Protocol Buffers, para o projeto compilar só com o JDK.

## 5. Concorrência

- **Servidor:** 16 threads trabalhadoras executam `getRequest → despacho → sendReply`. `ServicoMulticast.enviar`
  dispara uma chamada `receber` por membro em um pool de threads e aguarda todas (limite de 10 s por entrega).
  `GrupoMulticast` sincroniza o acesso à lista de membros e ao histórico.
- **Cliente:** a thread principal cuida da interação; a thread de exibição imprime as mensagens assim que o
  `OuvinteRemoto` as recebe (executado nas threads do `RpcServer` local).
- **Falha de cliente:** se o ouvinte de um membro não responde, ele é removido do grupo e o envio continua
  para os demais.

## 6. Testes e demonstração

`./run-testes.sh` sobe um servidor e vários clientes RMI reais e executa 27 verificações, todas aprovadas:

- JSON: parse, ida e volta, acentos/escapes, entrada inválida rejeitada; empacotamento de `MensagemRpc`.
- Autenticação: senha errada, usuário inexistente, login de assinante e de administrador.
- `joinGroup`/`leaveGroup`, grupo inexistente, e sair duas vezes.
- Permissão: assinante não pode enviar.
- Entrega: o ALERTA chega aos 2 membros e **não** a quem está fora do grupo; autor, tipo e texto preservados;
  depois do `leaveGroup` só o membro restante recebe.
- Histórico com as mensagens enviadas.
- **Concorrência:** duas sessões de administrador enviando 15 mensagens cada, em paralelo; as 30 chegam.
- **Falha:** ouvinte derrubado sem logout é removido do grupo e o envio não trava.
- Logout: o cliente recusa novas operações depois dele. O servidor remove o objeto de sessão da sua tabela,
  mas esse lado não tem teste próprio.

Também executei a demonstração do README com **quatro processos JVM separados** (servidor, Ana, Bruno e o
administrador): as duas mensagens apareceram em tempo real para Ana e Bruno, o servidor informou
"Entregue a 2 membro(s)", e o histórico da Ana trouxe ambas.

## 7. Diferenças em relação ao livro e limitações

- `methodId` é `String`, não `int` (o enunciado permite).
- `getRequest()` devolve um `Requisicao` (mensagem + host e porta do chamador), não só `byte[]`, para que
  `sendReply` saiba a quem responder.
- `sendReply(reply, clientHost, clientPort)` não envia a resposta por conexão própria: em RMI a resposta volta
  como retorno da chamada `transmitir`, e `sendReply` a entrega à chamada que aguarda, identificada por
  host, porta e `requestId`. `clientPort` é a porta RMI do cliente, que faz o papel da "porta do cliente".
- **Não há multicast IP real.** O endereço classe D e a porta do grupo são só atributos do `GrupoMulticast`.
  Com RMI, o servidor faz N chamadas (uma por membro), de modo que o custo cresce com o número de membros,
  ao contrário do multicast UDP, em que um datagrama alcança todos.
- Sem persistência; senhas de demonstração no código, com SHA-256 sem sal; sem TLS no RMI.
- Cada cliente precisa de uma porta própria acessível pelo servidor, porque o servidor chama de volta o ouvinte.
