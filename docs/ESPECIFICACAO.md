# Especificação — Bot de Controle Financeiro para Discord

- **Versão:** 0.1.0
- **Data:** 2026-08-07
- **Status:** rascunho aprovado para a Fase 0

Este documento é a fonte de verdade do comportamento do sistema. No fluxo
Spec-Driven Development, nenhuma linha de produção é escrita sem uma regra
correspondente aqui; no fluxo TDD, cada regra de negócio (RN-xx) vira ao menos um
teste automatizado antes da implementação. Regra que não é testável está mal
escrita e deve ser reescrita, não implementada.

---

## 1. Visão e escopo

Bot de Discord para registro e visualização de finanças pessoais, no espírito dos
bots de controle financeiro de WhatsApp: registrar um gasto precisa ser mais
rápido do que abrir um aplicativo.

**Objetivo duplo:** resolver um problema financeiro real e servir de estudo
prático de arquitetura de software em Java.

### Fora de escopo (v1)

- Compras parceladas e regime de competência (ver seção 9)
- Integração com bancos, Open Finance ou importação de extrato
- Múltiplas moedas — o sistema opera exclusivamente em BRL
- Investimentos, patrimônio, metas de longo prazo
- Interface web ou mobile

---

## 2. Decisões de arquitetura

| # | Decisão | Justificativa |
|---|---|---|
| AD-01 | Arquitetura hexagonal (ports & adapters) | Mantém domínio e casos de uso testáveis sem subir Discord nem banco — pré-requisito para um ciclo TDD rápido |
| AD-02 | JDA como biblioteca de Discord | Modelo síncrono/imperativo; a alternativa (Discord4J) impõe Project Reactor, cuja complexidade não contribui para o objetivo de aprendizado |
| AD-03 | Slash commands, não parsing de texto livre | Validação de tipo e autocomplete nativos do Discord; respostas ephemeral; menos código de parsing |
| AD-04 | SQLite com JDBC puro | Zero infraestrutura externa e SQL explícito — a camada de persistência fica visível em vez de escondida por um ORM |
| AD-05 | Migrações versionadas com Flyway | Schema evolui de forma reproduzível e auditável |
| AD-06 | Multiusuário desde o início | Retrofitar `usuario_id` em schema, repositórios e testes depois é caro; adicionar agora é quase de graça |
| AD-07 | Relatórios como embeds textuais | Sem dependência gráfica; saída verificável por asserção de string |
| AD-08 | Dinheiro como inteiro de centavos (`long`) na persistência, `BigDecimal` no domínio | Elimina erro de ponto flutuante e ambiguidade de arredondamento |
| AD-09 | Java 25 (LTS), build com Maven Wrapper | Build reprodutível, sem depender do que está instalado na máquina |

---

## 3. Modelo de domínio

### Objetos de valor

**`Dinheiro`** — encapsula `BigDecimal` em BRL. Expõe soma, subtração,
multiplicação por escalar, percentual sobre outro `Dinheiro`, comparação e
formatação pt-BR. Imutável.

**`Periodo`** — intervalo fechado de `LocalDate` com um tipo
(`SEMANA`, `MES`, `ANO`, `PERSONALIZADO`). Fábricas: `semanaAtual`,
`mesAtual`, `anoAtual`, `anterior()`.

### Entidades

**`Usuario`** — `id`, `discordUserId` (snowflake), `fusoHorario`, `criadoEm`.

**`Categoria`** — `id`, `usuarioId` (nulo = categoria padrão do sistema),
`nome`, `arquivada`, `criadoEm`.

**`Lancamento`** — `id`, `usuarioId`, `tipo` (`DESPESA` | `RECEITA`), `valor`
(`Dinheiro`), `categoriaId`, `descricao` (opcional), `dataCompetencia`
(`LocalDate`), `criadoEm` (`Instant`).

### Agregados de leitura

**`ResumoPeriodo`** — total de receitas, total de despesas, saldo, lista de
`TotalPorCategoria` e comparativo com o período anterior.

---

## 4. Requisitos funcionais

### Lançamentos

| ID | Requisito |
|---|---|
| RF-01 | Registrar despesa com valor, categoria, descrição opcional e data (default: hoje) |
| RF-02 | Registrar receita com os mesmos campos |
| RF-03 | Listar lançamentos de um período, com paginação |
| RF-04 | Editar um lançamento existente |
| RF-05 | Remover um lançamento |
| RF-06 | Desfazer o último lançamento registrado |

### Categorias

| ID | Requisito |
|---|---|
| RF-07 | Carregar conjunto padrão de categorias no primeiro uso |
| RF-08 | Criar, renomear e arquivar categorias próprias |
| RF-09 | Oferecer autocomplete de categoria durante a digitação do comando |

### Relatórios

| ID | Requisito |
|---|---|
| RF-10 | Resumo semanal |
| RF-11 | Resumo mensal |
| RF-12 | Resumo anual |
| RF-13 | Breakdown por categoria com valor, percentual e barra em texto |
| RF-14 | Comparativo com o período anterior (variação absoluta e percentual) |
| RF-15 | Saldo do período (receitas menos despesas) |
| RF-16 | Top N maiores gastos do período |

### Orçamento (Fase 3)

| ID | Requisito |
|---|---|
| RF-17 | Definir teto mensal por categoria |
| RF-18 | Avisar, no ato do registro, quando o gasto ultrapassa um percentual do teto |

### Suporte

| ID | Requisito |
|---|---|
| RF-19 | Exportar lançamentos de um período em CSV |
| RF-20 | Onboarding automático no primeiro comando |

---

## 5. Requisitos não-funcionais

| ID | Requisito | Como verificar |
|---|---|---|
| RNF-01 | Toda resposta é *ephemeral*; o bot opera apenas em DM ou guild explicitamente autorizada | Teste de adaptador + revisão de configuração |
| RNF-02 | Todo acesso a dados é filtrado por `usuarioId`, sem exceção | Teste de integração que prova que o usuário A não lê dado do usuário B |
| RNF-03 | Valores monetários em `BigDecimal`, persistidos como centavos; arredondamento `HALF_UP`, 2 casas | Testes unitários de `Dinheiro` |
| RNF-04 | Fuso de negócio `America/Sao_Paulo`; competência em `LocalDate`, auditoria em `Instant` UTC; semana ISO-8601 | Testes de `Periodo` com relógio fixo (`Clock.fixed`) |
| RNF-05 | Token do bot exclusivamente via variável de ambiente; nunca versionado | `.gitignore` + revisão |
| RNF-06 | Interações respondem ou fazem `deferReply()` dentro de 3 segundos | Revisão dos handlers |
| RNF-07 | Cobertura ≥ 90% em `dominio` e `aplicacao`; adaptadores cobertos por teste de integração | JaCoCo com limiar que quebra o build |
| RNF-08 | `dominio` não importa JDA nem JDBC; a regra de dependência é respeitada | Teste ArchUnit |
| RNF-09 | Schema alterado somente por migração Flyway versionada | Revisão |
| RNF-10 | Logs não registram valores monetários nem descrições de lançamento | Revisão |
| RNF-11 | Build reprodutível via Maven Wrapper, Java 25 | `./mvnw verify` em máquina limpa |
| RNF-12 | Dados recuperáveis via export CSV e cópia do arquivo SQLite | Procedimento documentado |

---

## 6. Regras de negócio

Cada regra abaixo é candidata direta a caso de teste.

### Valores

- **RN-01** — Valor de lançamento deve ser estritamente maior que zero. Zero e negativo são rejeitados com mensagem explícita.
- **RN-02** — Valor com mais de duas casas decimais é arredondado com `HALF_UP`.
- **RN-03** — O parser aceita as formas `50`, `50,00`, `50.00`, `1.234,56` e `1234.56`. Entrada ambígua ou não numérica é rejeitada.
- **RN-04** — Valor máximo por lançamento: R$ 1.000.000,00. Acima disso é rejeitado como provável erro de digitação.

### Datas

- **RN-05** — `dataCompetencia` não pode ser futura.
- **RN-06** — `dataCompetencia` não pode ser anterior a 5 anos da data atual.
- **RN-07** — Semana vai de segunda a domingo (ISO-8601), no fuso do usuário.
- **RN-08** — Mês vai do dia 1 ao último dia do mês; ano vai de 1º de janeiro a 31 de dezembro.

### Categorias

- **RN-09** — Nome de categoria é único por usuário, comparado sem diferenciar maiúsculas e após remoção de espaços nas pontas.
- **RN-10** — Nome de categoria tem entre 2 e 30 caracteres.
- **RN-11** — Categoria arquivada não aceita novos lançamentos, mas continua aparecendo nos relatórios de períodos históricos.
- **RN-12** — Categoria padrão do sistema não pode ser renomeada nem arquivada por um usuário; ele pode apenas deixar de usá-la.
- **RN-13** — Um lançamento só pode referenciar categoria do próprio usuário ou categoria padrão.

### Lançamentos

- **RN-14** — Descrição tem no máximo 200 caracteres.
- **RN-15** — `/desfazer` remove o último lançamento criado pelo usuário nos últimos 10 minutos. Sem lançamento elegível, retorna mensagem informativa, não erro.
- **RN-16** — Remoção é definitiva (*hard delete*); a confirmação exibe o que foi removido para permitir reinserção manual.

### Relatórios

- **RN-17** — Saldo do período = soma das receitas menos soma das despesas.
- **RN-18** — Percentual de uma categoria = despesa da categoria dividida pelo total de despesas do período, `HALF_UP` com 1 casa. A soma dos percentuais exibidos pode não fechar exatamente em 100% e a apresentação não deve forçar o ajuste.
- **RN-19** — Na comparação com o período anterior, quando o período anterior tem total zero, a variação percentual é exibida como `—`, nunca como divisão por zero ou infinito.
- **RN-20** — Período sem lançamentos produz um embed de estado vazio com orientação de uso, não uma mensagem de erro.
- **RN-21** — Categorias com total zero no período não aparecem no breakdown.

### Usuário

- **RN-22** — O usuário é criado sob demanda no primeiro comando, junto com a associação às categorias padrão.

---

## 7. Contrato de comandos

Registrar um gasto é a operação mais frequente e por isso é um comando raiz, sem
subcomando.

| Comando | Parâmetros | Descrição |
|---|---|---|
| `/gasto` | `valor`, `categoria`, `descricao?`, `data?` | Registra uma despesa |
| `/receita` | `valor`, `categoria`, `descricao?`, `data?` | Registra uma receita |
| `/lancamentos` | `periodo?`, `categoria?` | Lista lançamentos, paginado |
| `/editar` | `id`, campos a alterar | Edita um lançamento |
| `/remover` | `id` | Remove um lançamento |
| `/desfazer` | — | Desfaz o último lançamento |
| `/resumo` | `periodo` (`semana`\|`mes`\|`ano`), `deslocamento?` | Resumo do período |
| `/categoria` | `acao` (`listar`\|`criar`\|`renomear`\|`arquivar`) | Gerencia categorias |
| `/exportar` | `periodo` | Exporta CSV |

O parâmetro `id` usa numeração sequencial por usuário (exibida como `#123`) e é
preenchido por autocomplete com os lançamentos recentes — digitar identificador
à mão não deve ser necessário.

---

## 8. Fases de entrega

**Fase 0 — Fundação.** Repositório, build Maven, esqueleto de pacotes,
teste ArchUnit da regra de dependência, JaCoCo com limiar, bot conecta e responde
`/ping`. Critério de pronto: `./mvnw verify` passa e o `/ping` responde no
Discord.

**Fase 1 — Núcleo.** `Dinheiro`, `Periodo`, `Categoria`, `Lancamento`;
casos de uso de registrar gasto e receita, listar, remover e desfazer;
persistência SQLite com Flyway; `/resumo mes`. Critério de pronto: registrar um
gasto e vê-lo no resumo mensal, ponta a ponta.

**Fase 2 — Relatórios.** Resumo semanal e anual, breakdown por categoria,
comparativo com período anterior, top N, edição de lançamento, CRUD de
categorias.

**Fase 3 — Orçamento.** Teto mensal por categoria e alerta de estouro no ato do
registro.

**Fase 4 — Suporte.** Exportação CSV e lançamentos recorrentes.

---

## 9. Dívida consciente

**Compras parceladas** ficaram fora da v1 por decisão explícita. O efeito
colateral aceito é conhecido: uma compra de R$ 1.200,00 em 12x aparece
integralmente no mês da compra, inflando aquele mês e deixando os onze seguintes
artificialmente baratos.

Quando o parcelamento entrar, a modelagem prevista é regime de competência: uma
entidade `Compra` gerando N `Lancamento` com datas de competência futuras,
ligados pelo identificador da compra, com o resto da divisão absorvido pela
última parcela. O modelo atual não deve antecipar essa estrutura — apenas evitar
decisões que a inviabilizem.
