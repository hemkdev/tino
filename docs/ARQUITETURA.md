# Tino — arquitetura

Como o código e o banco estão organizados, e por quê. Comandos e uso estão no
[README](../README.md).

## Núcleo e canais

- **Núcleo, sem JDA:** `Tino` (casos de uso, dono da conexão e do lock de
  escrita), `Financas` (SQL), `Entrada` (texto livre), `Dinheiro`.
- **Canal Discord:** `Bot` (eventos JDA) e `Visual` (embeds, botões, modal).

Canal chama `Tino`, nunca `Financas` direto. Um processo só serve todos os
canais, compartilhando a mesma instância de `Tino`.

## Banco

Três colunas de decisão e nada mais:

```sql
CREATE TABLE IF NOT EXISTS categoria (
    id      INTEGER PRIMARY KEY,
    usuario TEXT    NOT NULL,
    pai_id  INTEGER REFERENCES categoria(id),      -- NULL = categoria raiz
    tipo    TEXT    NOT NULL,                      -- gasto | ganho
    nome    TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS lancamento (
    id             INTEGER PRIMARY KEY,
    usuario        TEXT    NOT NULL,
    valor_centavos INTEGER NOT NULL,               -- negativo = despesa
    categoria_id   INTEGER NOT NULL REFERENCES categoria(id),
    descricao      TEXT,
    data           TEXT    NOT NULL                -- AAAA-MM-DD
);

CREATE TABLE IF NOT EXISTS recorrente (
    id             INTEGER PRIMARY KEY,
    usuario        TEXT    NOT NULL,
    valor_centavos INTEGER NOT NULL,
    categoria_id   INTEGER NOT NULL REFERENCES categoria(id),
    descricao      TEXT
);

CREATE INDEX IF NOT EXISTS idx_consulta ON lancamento (usuario, data);

CREATE TABLE IF NOT EXISTS conta (id INTEGER PRIMARY KEY);

CREATE TABLE IF NOT EXISTS identidade (
    provedor TEXT    NOT NULL,                     -- discord | google | telegram
    externo  TEXT    NOT NULL,                     -- id da pessoa no provedor
    conta_id INTEGER NOT NULL REFERENCES conta(id),
    PRIMARY KEY (provedor, externo)
);
```

**`usuario` é o id da conta, não do canal.** Uma conta tem várias identidades:
quem lança pelo Discord e entra no site pelo Google vê os mesmos dados depois de
vincular as duas.

**Sinal no valor em vez de coluna `tipo`.** Saldo é `SUM(valor_centavos)`, gasto
é `SUM` do que for negativo. Elimina uma coluna, um enum e todo o código que
decidiria quando somar e quando subtrair.

**Centavos em inteiro.** SQLite não tem decimal e `double` em dinheiro é bug.

**Subcategoria é `pai_id` apontando para a própria tabela.** Um nível ou vários,
tanto faz — a query de resumo agrupa pela raiz.

**Categorias de gasto e de ganho são listas separadas**, distinguidas por `tipo`.
"Outros" existe nos dois lados, como categorias diferentes.

**Categoria pertence sempre a alguém.** As iniciais são criadas para o usuário no
primeiro contato, e não como registros do sistema — é isso que permite renomear e
excluir qualquer uma delas.

**Nomes gravados em forma canônica** (`Cartão de Crédito`), o que torna a
comparação insensível a caixa mesmo com acento — onde o `COLLATE NOCASE` do
SQLite falha.

**`CREATE TABLE IF NOT EXISTS` no boot.** Sem ferramenta de migração enquanto o
schema couber nisso.

## Arquivos

```
Bot.java          main, JDA, registro e roteamento de comandos
Tino.java         casos de uso independentes de canal; dono da conexão,
                  do CSV e da projeção do mês
Financas.java     queries e cálculos; recebe uma Connection
Visual.java       embeds, botões e modal
Entrada.java      interpreta "50 mercado, 30 uber" e datas digitadas
Dinheiro.java     converte "1.234,56" ↔ centavos, formata em BRL

FinancasTest.java   contra jdbc:sqlite::memory:
VisualTest.java
EntradaTest.java
DinheiroTest.java
TinoTest.java
```

Sem interface de repositório: existe uma implementação só. Os testes usam SQLite
em memória, que abre e roda em milissegundos — não há lentidão para contornar.

Métodos que dependem da data recebem `LocalDate hoje` por parâmetro em vez de
chamar `LocalDate.now()` internamente. Mesmo tamanho de código, e torna
determinístico o teste de qualquer regra de período.

## Stack

Java 25, JDA, `sqlite-jdbc`, `slf4j-simple`, JUnit 5. Empacotamento com
maven-shade em jar executável.


## Regras que não são negociáveis

O resto pode ser ajustado durante a implementação; estas não, porque falham
silenciosamente:

1. Valor deve ser maior que zero, com no máximo duas casas — arredondamento
   `HALF_UP`. O parser aceita `50`, `50,00` e `1.234,56`.
2. Toda query filtra por `usuario`. Sem exceção.
3. Data futura é rejeitada.
4. Categoria só pode ser usada pelo dono, e nunca cruza entre gasto e ganho.
5. Comparação com período anterior de total zero exibe `—`, nunca divisão por
   zero.
6. Nada de token ou dado financeiro em log.
