# finance-bot

Bot de Discord para controle financeiro pessoal, escrito em Java.

Registra despesas e receitas por slash command e apresenta resumos semanais,
mensais e anuais com breakdown por categoria.

## Estado

Fase 0 — fundação. Ainda não há código de produção.

A especificação completa (requisitos, regras de negócio, arquitetura e fases de
entrega) está em [`docs/ESPECIFICACAO.md`](docs/ESPECIFICACAO.md).

## Stack

- Java 25 (LTS)
- JDA — integração com Discord
- SQLite com JDBC puro, migrações via Flyway
- JUnit 5, AssertJ, Mockito e ArchUnit
- Maven (via wrapper)

## Processo de desenvolvimento

Spec-Driven Development combinado com TDD: a especificação define o comportamento
observável, cada regra de negócio vira teste antes da implementação, e o código
existe apenas para satisfazer esses testes.

## Configuração local

O token do bot vem da variável de ambiente `DISCORD_TOKEN` e nunca é versionado.

```bash
export DISCORD_TOKEN="seu-token-aqui"
```
