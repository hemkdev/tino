# AGENTS.md — Tino

Finanças pessoais por conversa, em Java 25, sem framework e sem ORM: JDA, Postgres 17 (driver
JDBC), Flyway, JUnit 5 e Maven. Hoje o canal é o Discord; site, Telegram e MCP vêm sobre o mesmo
núcleo.

```bash
docker compose up -d                              # Postgres 17 em localhost:5433
mvn verify                                        # testes, no banco tino_teste
mvn package && java -jar target/tino.jar migrar   # precisa de DATABASE_URL, _USER e _PASSWORD
java -jar target/tino.jar                         # e também de DISCORD_TOKEN
```

Esquema novo é uma migration nova em `src/main/resources/db/migration/` (`V2__...sql`); nunca
editar uma que já foi para a `main`.

## Estrutura

- **`tino.nucleo`, sem JDA:** `Tino` (casos de uso, dono da conexão e do lock), `Financas` (SQL,
  package-private), `Entrada` (texto livre), `Dinheiro`, `Evento` e os records do domínio.
- **`tino.discord`:** `Bot` (eventos do JDA), `Visual` (embeds e botões), `Estado`.

Canal novo chama `Tino`, nunca `Financas`. `Tino` recebe valores já interpretados (centavos com
sinal, `LocalDate`), exceto `Tino.lancar`, que recebe o texto livre. Canal que precise interpretar
um campo solto usa `Entrada` ou `Dinheiro`. O relógio é do `Tino` (`Clock`); classe pura que
depende do dia recebe `LocalDate hoje`. Regra que um segundo canal precisaria copiar é núcleo.

O porquê de cada escolha está em `docs/decisoes.md`; o esquema e as regras do banco, em
`docs/banco.md`; os estados e as falas do personagem, em `docs/personagem.md`. `local/` fica fora
do git (plano, brief, specs).

## Regras

- Valem as regras inegociáveis de `docs/banco.md`: toda query filtra por `usuario`, centavos em
  inteiro, data futura rejeitada, nada de token ou dado financeiro em log.
- Funcionalidade nova começa por uma spec em `local/specs/`, aprovada antes do código. Cada
  critério de aceite vira um teste que falha antes da implementação; bug também começa pelo teste.
- Tudo em PT-BR: código, testes, commits e docs.

## Em aberto

- **Contrato do MCP:** campos estruturados ou texto livre pela `Entrada`.

O bot é público (decisão de 2026-11-05): qualquer pessoa instala. Por isso `docs/PRIVACIDADE.md`
e `docs/TERMOS.md` valem para gente de fora, e mudança que toque em dado pessoal atualiza os dois.

## Commits, branches e PRs

- **Branches:** `<tipo>/<assunto>` a partir da `main` (`feat/tracos`, `fix/alerta`). Só a `main`
  é permanente.
- **Commits:** Conventional Commits, minúsculo, imperativo, sem ponto, até 72 caracteres,
  atômicos, com trailer `Co-Authored-By` quando houve IA.
- **PR:** o título, no formato de commit, vira o commit da `main`. Corpo curto, em prosa, sem
  cabeçalhos nem listas: o problema, o que muda e, se precisar, testes e o que ficou de fora.
- **Merge:** só squash and merge; a branch é apagada sozinha.
