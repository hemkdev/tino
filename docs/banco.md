# Banco

SQLite, um arquivo só (`tino.db`), aberto em modo WAL. O esquema é criado no boot com
`CREATE TABLE IF NOT EXISTS`, sem ferramenta de migração enquanto ele couber nisso. As decisões
de código estão em [decisoes.md](decisoes.md).

```mermaid
erDiagram
    direction LR
    conta ||--o{ identidade : "conta_id"
    conta ||--o{ categoria : "usuario"
    conta ||--o{ lancamento : "usuario"
    conta ||--o{ recorrente : "usuario"
    categoria |o--o{ categoria : "pai_id"
    categoria ||--o{ lancamento : "categoria_id"
    categoria ||--o{ recorrente : "categoria_id"
    conta ||--o{ pendente : "usuario"

    conta {
        integer id PK
    }
    identidade {
        text provedor PK
        text externo PK
        integer conta_id FK
    }
    categoria {
        integer id PK
        text usuario
        integer pai_id FK
        text tipo
        text nome
    }
    lancamento {
        integer id PK
        text usuario
        integer valor_centavos
        integer categoria_id FK
        text descricao
        text data
    }
    recorrente {
        integer id PK
        text usuario
        integer valor_centavos
        integer categoria_id FK
        text descricao
    }
    pendente {
        text chave PK
        text usuario
        integer valor_centavos
        text categoria
        text descricao
        text data
        text criado_em
    }
```

Um índice só: `idx_consulta ON lancamento (usuario, data)`, que serve a todo resumo e listagem.

`pendente` guarda o lançamento fora do normal que espera a pessoa responder [Tá certo] ou
[Corrigir]. A chave é aleatória e vai no botão; só a dona confirma, uma vez. Expira em 24 h, e
cada lançamento novo apaga os expirados da pessoa, sem job. `criado_em` é ISO-8601 em UTC
truncado ao segundo, para a ordem do texto ser a do tempo.

## Escolhas

**`usuario` é o id da conta, não do canal.** Uma conta tem várias identidades
(`provedor, externo`): quem lança pelo Discord e entra no site pelo Google vê os mesmos dados
depois de vincular as duas. Vincular uma identidade que já pertence a outra conta é recusado; não
existe fusão de contas.

**Sinal no valor em vez de coluna `tipo`.** Saldo é `SUM(valor_centavos)`, gasto é a soma do que
for negativo. Elimina uma coluna, um enum e todo o código que decidiria quando somar e quando
subtrair.

**Centavos em inteiro.** SQLite não tem decimal, e `double` em dinheiro é bug.

**Subcategoria é `pai_id` apontando para a própria tabela.** O resumo agrupa pela raiz, então uma
subcategoria soma na categoria principal.

**Gasto e ganho são listas separadas**, distinguidas por `tipo`. "Outros" existe nos dois lados,
como categorias diferentes.

**Categoria pertence sempre a alguém.** As iniciais são criadas para a conta no primeiro contato,
e não como registros do sistema: é isso que permite renomear e excluir qualquer uma.

**Nomes em forma canônica** (`Cartão de Crédito`), o que torna a comparação insensível a caixa
mesmo com acento, onde o `COLLATE NOCASE` do SQLite falha.

## Regras que não são negociáveis

Estas falham em silêncio se quebradas, então valem para qualquer código que escreva no banco:

1. Valor maior que zero, com no máximo duas casas (arredondamento `HALF_UP`).
2. Toda query filtra por `usuario`. Sem exceção.
3. Data futura é rejeitada.
4. Categoria só é usada pela dona, e nunca cruza entre gasto e ganho.
5. Comparação com período anterior de total zero mostra `—`, nunca divide por zero.
6. Nada de token ou dado financeiro em log.

Nenhuma delas é constraint do banco: o SQLite aqui não tem `CHECK` nem chave estrangeira para
`usuario`, então quem garante é o núcleo. `Dinheiro` garante as duas casas; `Pedido` recusa valor
zero e data nula; o `Tino` recusa data futura pelo próprio relógio, então canal que monta o pedido
direto passa pelas mesmas regras.
