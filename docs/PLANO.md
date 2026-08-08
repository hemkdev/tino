# finance-bot — plano

Bot de Discord para controle financeiro pessoal. Registrar ganhos e despesas por
categoria, ver quanto foi gasto em cada uma e como evoluiu de um mês para o
outro.

## Comandos

| Comando | O que faz |
|---|---|
| `/gasto <valor> [categoria] [descricao] [data]` | Registra despesa. Sem categoria, vai para Outros |
| `/ganho <valor> [categoria] [descricao] [data]` | Registra receita |
| `/lancar <texto>` | Vários de uma vez: `50 mercado, 30 uber, +3000 salário` |
| `/resumo [periodo]` | Por categoria, saldo, variação sobre o período anterior e projeção do mês |
| `/lancamentos [periodo]` | Lista paginada com os ids |
| `/exportar [periodo]` | Anexa um CSV |
| `/remover <id>` | Apaga um lançamento |
| `/categoria listar\|criar\|renomear\|excluir` | Gerencia categorias |
| `/fixo listar\|adicionar\|remover\|lancar` | Aluguel e assinaturas, lançados de uma vez |
| `/ajuda` | Exemplos de uso |

Na conversa privada, texto solto também vira lançamento: `50 mercado` sem barra
nenhuma. Só entra o que começa com número — conversa comum é ignorada.

Cada registro traz botões de **Desfazer** e **Editar**. O contexto viaja no id do
botão, então eles continuam funcionando depois de reiniciar o bot, e clique de
terceiro é recusado.

Categoria vem por autocomplete. Em servidor as respostas são *ephemeral*; em DM
ficam no histórico, já que a conversa é privada de qualquer forma.

## Banco

Três colunas de decisão e nada mais:

```sql
CREATE TABLE IF NOT EXISTS categoria (
    id      INTEGER PRIMARY KEY,
    usuario TEXT,                                  -- NULL = categoria padrão
    pai_id  INTEGER REFERENCES categoria(id),      -- NULL = categoria raiz
    nome    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS lancamento (
    id             INTEGER PRIMARY KEY,
    usuario        TEXT    NOT NULL,
    valor_centavos INTEGER NOT NULL,               -- negativo = despesa
    categoria_id   INTEGER NOT NULL REFERENCES categoria(id),
    descricao      TEXT,
    data           TEXT    NOT NULL                -- AAAA-MM-DD
);

CREATE INDEX IF NOT EXISTS idx_consulta ON lancamento (usuario, data);
```

**Sinal no valor em vez de coluna `tipo`.** Saldo é `SUM(valor_centavos)`, gasto
é `SUM` do que for negativo. Elimina uma coluna, um enum e todo o código que
decidiria quando somar e quando subtrair.

**Centavos em inteiro.** SQLite não tem decimal e `double` em dinheiro é bug.

**Subcategoria é `pai_id` apontando para a própria tabela.** Um nível ou vários,
tanto faz — a query de resumo agrupa pela raiz.

**`CREATE TABLE IF NOT EXISTS` no boot.** Sem ferramenta de migração enquanto o
schema couber nisso.

## Arquivos

```
Bot.java          main, JDA, registro e roteamento de comandos
Financas.java     queries e cálculos; recebe uma Connection
Visual.java       embeds, botões, modal e CSV
Entrada.java      interpreta "50 mercado, 30 uber"
Dinheiro.java     converte "1.234,56" ↔ centavos, formata em BRL

FinancasTest.java   contra jdbc:sqlite::memory:
VisualTest.java
EntradaTest.java
DinheiroTest.java
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
4. Categoria só pode ser usada pelo dono ou ser padrão do sistema.
5. Comparação com período anterior de total zero exibe `—`, nunca divisão por
   zero.
6. Nada de token ou dado financeiro em log.

## Pendências

**Hospedagem.** O bot roda na máquina pessoal, então só responde enquanto ela
está ligada — e o link de instalação é público. Candidato natural: uma VPS com
disco persistente (Oracle Cloud Free Tier atende sem custo), rodando o jar
direto. Duas restrições que eliminam boa parte das plataformas: o bot mantém
WebSocket permanente com o gateway, logo não é serverless; e o SQLite é um
arquivo em disco, logo filesystem efêmero apaga o histórico a cada deploy.

Enquanto isso não acontece, os dados de quem instalar ficam no `financebot.db`
local.

## Fora de escopo

Orçamento com alerta, filtro por categoria em `/lancamentos`, evolução de uma
categoria mês a mês, parcelamento, gráficos em imagem, resumo automático
agendado, Docker e CI.

Cada um entra se e quando incomodar de verdade.
