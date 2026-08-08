# Arquitetura — finance-bot

- **Versão:** 0.1.0
- **Data:** 2026-08-07
- **Complementa:** [`ESPECIFICACAO.md`](ESPECIFICACAO.md)

Enquanto a especificação define o comportamento observável, este documento define
a estrutura interna que o realiza. Alterações aqui não deveriam mudar o que o
usuário percebe.

---

## 1. Decisões de arquitetura

| # | Decisão | Justificativa |
|---|---|---|
| AD-01 | Arquitetura hexagonal (ports & adapters) | Mantém domínio e casos de uso testáveis sem subir Discord nem banco — pré-requisito para um ciclo TDD rápido |
| AD-02 | JDA como biblioteca de Discord | Modelo síncrono/imperativo; Discord4J imporia Project Reactor, cuja complexidade não contribui para o objetivo de aprendizado |
| AD-03 | Slash commands, não parsing de texto livre | Validação de tipo e autocomplete nativos; dispensa a privileged intent de conteúdo de mensagem |
| AD-04 | SQLite com JDBC puro | Zero infraestrutura externa e SQL explícito — a persistência fica visível em vez de escondida por um ORM |
| AD-05 | Migrações versionadas com Flyway | Schema evolui de forma reproduzível e auditável |
| AD-06 | Multiusuário desde o início | Retrofitar `usuario_id` em schema, repositórios e testes depois é caro; agora é quase de graça |
| AD-07 | Relatórios como embeds textuais | Sem dependência gráfica; saída verificável por asserção de string |
| AD-08 | `BigDecimal` no domínio, inteiro de centavos na persistência | Elimina erro de ponto flutuante e ambiguidade de arredondamento |
| AD-09 | Java 25 (LTS), build com Maven Wrapper | Build reprodutível, sem depender do que está instalado na máquina |
| AD-10 | `java.time.Clock` injetado; proibido chamar `now()` estático | Torna determinístico todo teste que envolve data — ver seção 4 |
| AD-11 | Fakes em memória no lugar de mocks para repositórios | Testa estado resultante em vez de sequência de chamadas — ver seção 6 |
| AD-12 | SQLite em WAL mode com um único escritor | Modo correto de operar SQLite sob concorrência do JDA — ver seção 5 |
| AD-13 | Empacotamento em contêiner Docker desde a Fase 0 | Desacopla a aplicação da escolha de hospedagem, que fica adiada sem bloquear nada |
| AD-14 | Comandos registrados por guild em desenvolvimento, globais em produção | Registro global leva até uma hora para propagar; por guild é imediato |
| AD-15 | Repositório privado no GitHub com CI em Actions | `./mvnw verify` a cada push, sem exposição pública |

---

## 2. Estrutura de pacotes

Pacote raiz: `io.github.hemk.financebot`

```
dominio/
├── lancamento/     Lancamento, TipoLancamento, exceções de domínio
├── categoria/      Categoria
├── usuario/        Usuario
├── orcamento/      Orcamento                         (Fase 3)
└── comum/          Dinheiro, Periodo, TipoPeriodo

aplicacao/
├── porta/
│   ├── entrada/    RegistrarGastoUseCase, GerarResumoUseCase, ...
│   └── saida/      LancamentoRepository, CategoriaRepository, ...
├── comando/        RegistrarGastoComando, EditarLancamentoComando   (records)
├── resultado/      ResumoPeriodo, TotalPorCategoria                 (records)
└── servico/        RegistrarGastoService, GerarResumoService

adaptadores/
├── entrada/discord/
│   ├── comando/       GastoHandler, ResumoHandler, CategoriaHandler
│   ├── interacao/     PaginacaoHandler, ConfirmacaoHandler, EdicaoModalHandler
│   ├── apresentacao/  ResumoEmbedFactory, LancamentoEmbedFactory
│   └── RoteadorDeInteracoes
└── saida/persistencia/
    ├── SqliteLancamentoRepository, SqliteCategoriaRepository
    └── mapeador/      LancamentoRowMapper

infraestrutura/
├── configuracao/   Configuracao, FabricaDeConexao
├── migracao/       ExecutorDeMigracoes
└── Aplicacao.java  main
```

### Regra de dependência

As setas apontam sempre para dentro:

```
adaptadores  ──►  aplicacao  ──►  dominio
infraestrutura ──►  (monta tudo, só no boot)
```

- `dominio` não importa nada além do JDK.
- `aplicacao` importa apenas `dominio`.
- `adaptadores` importam `aplicacao` e `dominio`, nunca uns aos outros.
- `infraestrutura` é o único lugar que conhece todo mundo, e apenas para instanciar.

Verificado por teste ArchUnit — em particular, `dominio` não pode importar
`net.dv8tion.jda` nem `java.sql`.

---

## 3. Fluxo de uma interação

```
SlashCommandInteractionEvent  (JDA)
        │
        ▼
GastoHandler                      ← adaptador de entrada
  extrai opções, converte "1.234,56" → Dinheiro
  monta RegistrarGastoComando (record imutável)
        │
        ▼
RegistrarGastoUseCase             ← porta de entrada (interface)
        │
        ▼
RegistrarGastoService             ← orquestra; não decide regra de negócio
  busca ou cria usuário, resolve categoria, delega validação ao domínio
        │
        ▼
LancamentoRepository              ← porta de saída (interface)
        │
        ▼
SqliteLancamentoRepository        ← adaptador de saída
```

O adaptador de entrada não conhece SQL; o serviço de aplicação não conhece JDA;
o domínio não conhece nenhum dos dois. Cada travessia dessa fronteira é uma
interface.

---

## 4. Tempo

Nenhuma classe chama `LocalDate.now()`, `Instant.now()` ou equivalente. Todo
componente que precisa saber "agora" recebe um `java.time.Clock` no construtor.

```java
public RegistrarGastoService(LancamentoRepository repositorio, Clock relogio) { ... }
```

Em produção, `Clock.system(ZoneId.of("America/Sao_Paulo"))`. Em teste,
`Clock.fixed(...)`.

Sem isso, regras como "rejeitar data futura" (RN-05) ou "semana atual na virada
do ano" (RN-07) não têm teste determinístico — e o projeto acumula testes que
passam em janeiro e falham em dezembro.

Convenções complementares:

- Data de negócio: `LocalDate` (competência do lançamento).
- Instante de auditoria: `Instant`, sempre UTC.
- Conversão entre os dois acontece apenas na borda, usando o fuso do usuário.

---

## 5. Persistência

### Esboço do schema

A forma definitiva é o script Flyway `V1__esquema_inicial.sql`; o esboço abaixo
registra a intenção.

```sql
CREATE TABLE usuario (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    discord_user_id   TEXT    NOT NULL UNIQUE,
    fuso_horario      TEXT    NOT NULL DEFAULT 'America/Sao_Paulo',
    criado_em         TEXT    NOT NULL
);

CREATE TABLE categoria (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id   INTEGER REFERENCES usuario(id),  -- NULL = categoria padrão
    nome         TEXT    NOT NULL,
    arquivada    INTEGER NOT NULL DEFAULT 0,
    criado_em    TEXT    NOT NULL
);

CREATE TABLE lancamento (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id         INTEGER NOT NULL REFERENCES usuario(id),
    numero_usuario     INTEGER NOT NULL,   -- sequencial por usuário, exibido como #123
    tipo               TEXT    NOT NULL,   -- DESPESA | RECEITA
    valor_centavos     INTEGER NOT NULL,
    categoria_id       INTEGER NOT NULL REFERENCES categoria(id),
    descricao          TEXT,
    data_competencia   TEXT    NOT NULL,   -- ISO-8601, AAAA-MM-DD
    criado_em          TEXT    NOT NULL,   -- ISO-8601 UTC
    UNIQUE (usuario_id, numero_usuario)
);

CREATE INDEX idx_lancamento_consulta
    ON lancamento (usuario_id, data_competencia);
```

Decisões embutidas no schema:

- **`valor_centavos INTEGER`** — SQLite não tem tipo decimal; centavos em inteiro
  eliminam qualquer imprecisão.
- **`numero_usuario`** — identificador curto e digitável, sequencial por usuário.
  A chave primária técnica continua sendo `id`.
- **Índice composto por `(usuario_id, data_competencia)`** — atende ao padrão de
  consulta dominante, que é sempre "lançamentos deste usuário neste período".
- **Unicidade de nome de categoria (RN-09)** — como a comparação ignora
  maiúsculas e a coluna aceita `usuario_id` nulo, a restrição é aplicada em
  índice parcial ou na aplicação; definido ao escrever a migração.

### Concorrência

SQLite aceita muitos leitores simultâneos e **um único escritor**. Como o JDA
processa interações em várias threads, três configurações são obrigatórias:

1. `PRAGMA journal_mode=WAL` — leitores não bloqueiam o escritor.
2. `PRAGMA busy_timeout` — espera em vez de falhar com `SQLITE_BUSY`.
3. Pool de escrita limitado a **uma conexão**.

Isso não é otimização prematura: é o modo correto de operar SQLite, e é
exatamente o tipo de detalhe que um ORM esconderia.

---

## 6. Estratégia de teste

| Camada | Tipo | Dependências |
|---|---|---|
| `dominio` | Unitário puro | Nenhuma — sem mock, sem fake |
| `aplicacao` | Unitário com fakes | Repositórios em memória e `Clock.fixed` |
| `adaptadores/saida` | Integração | SQLite real em arquivo temporário (`@TempDir`) |
| `adaptadores/entrada` | Unitário | JDA mockado apenas na fronteira |
| Arquitetura | ArchUnit | — |

### Por que fakes e não mocks nos repositórios

Um mock verifica que `salvar()` **foi chamado**. Um fake verifica que o dado
**ficou salvo** — que é o comportamento de interesse. Além disso, mock de
repositório acopla o teste à sequência exata de chamadas: uma refatoração que
preserva comportamento quebra dezenas de testes sem que nada tenha regredido.

O fake (`LancamentoRepositoryEmMemoria`, sobre um `HashMap`) é escrito uma vez e
reusado por todos os casos de uso. Mockito fica reservado a colaboradores com
efeito colateral genuinamente difícil de simular — neste projeto, quase nada.

### Ordem do ciclo TDD

Primeiro os objetos de valor, que concentram regra e não têm dependência:

1. `Dinheiro` — RN-01 a RN-04
2. `Periodo` — RN-07, RN-08
3. `Categoria` — RN-09 a RN-13
4. `Lancamento` — RN-05, RN-06, RN-14

Só então os casos de uso, e por último os adaptadores.

---

## 7. Stack

| Dependência | Papel |
|---|---|
| JDA 5.x | Integração com Discord |
| org.xerial:sqlite-jdbc | Driver JDBC |
| HikariCP | Pool de conexões |
| Flyway | Migrações de schema |
| SLF4J + Logback | Logging |
| JUnit 5 | Framework de teste |
| AssertJ | Asserções fluentes |
| Mockito | Mocks, uso restrito |
| ArchUnit | Teste da regra de dependência |
| JaCoCo | Cobertura, com limiar que quebra o build |
| maven-shade-plugin | Fat jar executável |

Versões são fixadas na Fase 0 conferindo o Maven Central. Opcionais em avaliação:
Spotless (formatação automática) e Error Prone (detecção de bugs em compilação).

---

## 8. Configuração

Toda configuração vem de variável de ambiente, lida uma vez no boot para um
record `Configuracao`. Nenhum valor sensível é versionado.

| Variável | Obrigatória | Descrição |
|---|---|---|
| `DISCORD_TOKEN` | sim | Token do bot |
| `DISCORD_GUILD_ID` | não | Quando presente, registra comandos apenas nesta guild (desenvolvimento). Ausente, registra globalmente |
| `BANCO_CAMINHO` | não | Caminho do arquivo SQLite. Default: `dados/financebot.db` |
| `FUSO_HORARIO` | não | Default: `America/Sao_Paulo` |
| `LOG_NIVEL` | não | Default: `INFO` |

`DISCORD_GUILD_ID` existe por um motivo prático: comandos globais levam até uma
hora para propagar, enquanto comandos de guild são registrados na hora. Sem essa
distinção, cada ajuste em assinatura de comando custa uma hora de espera.

---

## 9. Empacotamento e deploy

Fat jar via maven-shade dentro de `Dockerfile` multi-stage:

```
etapa build:  imagem com JDK + Maven  →  ./mvnw package
etapa final:  imagem JRE slim         →  copia o jar, define ENTRYPOINT
```

O `docker-compose.yml` monta um volume para o diretório do banco. Isso é o que
torna a escolha de hospedagem um detalhe de deploy em vez de uma decisão de
arquitetura.

### Restrições de hospedagem, quando a decisão chegar

Duas propriedades do sistema eliminam categorias inteiras de plataforma:

1. **O bot mantém WebSocket permanente com o gateway do Discord.** Não é
   serverless. Elimina Lambda, Cloud Run com scale-to-zero, Vercel e Workers.
2. **SQLite é um arquivo em disco.** Elimina plataformas com filesystem efêmero,
   onde um deploy de rotina recria o contêiner e apaga o histórico financeiro.

Sobram: máquina local ou Raspberry Pi, VPS com disco (Oracle Free Tier, Hetzner,
Contabo), ou plataforma com volume persistente (Fly.io). Memória mínima
confortável: 512 MB.

### Backup

`cron` diário executando `sqlite3 dados.db ".backup"` com envio para
armazenamento externo. Alternativa mais robusta a avaliar: **Litestream**, que
replica SQLite continuamente para armazenamento S3-compatível e permite
recuperação até o último segundo.

---

## 10. Integração contínua

GitHub Actions em repositório privado, executando `./mvnw verify` a cada push e
pull request. O build quebra em falha de teste, violação de arquitetura
(ArchUnit) ou cobertura abaixo do limiar (JaCoCo). Nenhum segredo é necessário na
CI — não há teste que exija conexão real com o Discord.
