# finance-bot

Bot de Discord para controle financeiro pessoal, escrito em Java.

Registra despesas e receitas por slash command e apresenta resumos semanais,
mensais e anuais com breakdown por categoria.

## Estado

Fase 0 — fundação. Ainda não há código de produção.

Documentação:

- [`docs/ESPECIFICACAO.md`](docs/ESPECIFICACAO.md) — requisitos, regras de
  negócio, contrato de comandos e fases de entrega
- [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md) — decisões técnicas, estrutura de
  pacotes, schema, estratégia de teste e deploy

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
