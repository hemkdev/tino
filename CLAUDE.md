# CLAUDE.md — Tino

## Contexto do projeto

Finanças pessoais em Java, sem framework e sem ORM. Começou como bot de Discord
e bancada de estudo. Agora é produto próprio: o objetivo é ser usável de
verdade, com mais de um canal de entrada e de visualização.

Destino:

- **Discord**: canal atual (DM e slash commands).
- **Site**: lançar e visualizar dados.
- **Conector MCP**: para um modelo de linguagem ler e lançar.
- **VPS**: tudo rodando fora da máquina pessoal, com disco persistente.

## Ordem de trabalho

1. ~~Corrigir os bugs conhecidos abaixo antes do redesenho.~~
2. ~~Separar o núcleo (regras, consultas, dinheiro) do que é específico do Discord.~~
3. ~~Terminar o desacoplamento: `parseData` sai do `Bot` para a `Entrada`; `csv()` e a conta do
   `ritmo()` saem do `Visual` para o núcleo.~~
4. ~~Personagem, na branch `hemkdev/personagem`: pacotes `tino.nucleo` e `tino.discord`,
   `Evento` selado com alerta que pergunta antes de gravar, o Broto na miniatura de cada
   resposta, falas novas, marca e README.~~
5. Traços com dado novo (planta, ausência, impulso, vigiar categoria), em branch própria.
   Regras em `docs/PERSONAGEM.md`.
6. Adicionar os canais novos sobre esse núcleo.

## Estrutura

- **`tino.nucleo`, sem JDA:** `Tino` (casos de uso, dono da conexão e do lock),
  `Financas` (SQL, package-private), `Entrada` (texto livre), `Dinheiro`, `Tipo`
  e os records do domínio (`Periodo`, `Resumo`, `Linha`, `Lancamento`, `Recorrente`).
- **`tino.discord`:** `Bot` (eventos JDA) e `Visual` (embeds, botões, modal).

Canal novo chama `Tino`, nunca `Financas` direto: o lock de escrita mora nos
métodos `synchronized` de `Tino`. `Tino` recebe valores já interpretados
(centavos com sinal, `LocalDate`), com uma exceção: `Tino.lancar` recebe o
texto livre e chama a `Entrada`, porque lançar por texto é caso de uso de todo
canal (DM, site, MCP). Canal que precise interpretar um campo solto (valor,
data) usa `Entrada` ou `Dinheiro`, nunca `Financas`.

Documentação: `docs/` é pública e versionada (`ARQUITETURA.md`, `PERSONAGEM.md`,
`PRIVACIDADE.md`, `marca/`). `local/` fica fora do git: plano de trabalho,
brief e o pacote do personagem até a branch dele.

## Bugs conhecidos

Os cinco bugs do parser foram corrigidos, cada um coberto por teste:

1. Vírgula no valor (`3395e67`).
2. `1.500` virava R$ 1,50. Regra no ramo sem vírgula de `Dinheiro.paraCentavos`:
   3 casas depois do último ponto é milhar, até 2 é decimal, 4 ou mais é erro.
3. Aspas vazias (`50 "" 07/08`) derrubavam `Entrada.item`; também `+` sem valor.
4. Valor gigante lançava `ArithmeticException` em `longValueExact()`; agora o
   limite é checado antes.
5. Período e gatilho em `consulta()` dependiam da ordem aleatória de `Map.of()`.
   Agora são listas ordenadas: o período mais curto vence, e o empate de
   gatilho vai para o tipo declarado primeiro.

## Decisões tomadas

- **Identidade:** conta própria do Tino com várias identidades. Tabelas `conta`
  e `identidade (provedor, externo) → conta_id`. A coluna `usuario` das tabelas
  de dados guarda o id da conta, não mais o id do Discord. Cada canal resolve a
  conta com `Tino.conta(provedor, externo)` na entrada; `Tino.vincular` liga uma
  identidade nova (Google, Telegram) a uma conta existente. Não existe fusão de
  duas contas com dados: vincular uma identidade que já pertence a outra conta é
  recusado.
- **Migração:** `Financas.migrarParaContas` roda no boot e troca ids do Discord
  pela conta, de forma idempotente (id de conta é pequeno, snowflake tem 17+
  dígitos). Botões antigos no histórico do Discord carregam o id do Discord e
  passam a responder "não é seu".
- **Processo:** um só para Discord, site e MCP, compartilhando a instância de
  `Tino` e o lock dela.
- **Login no site:** OAuth do Discord primeiro; Google e Telegram entram como
  provedores adicionais pela mesma tabela `identidade`.
- **Personagem:** criatura própria (o Broto), sem Wumpus, pelas diretrizes de marca da Discord e
  porque vai a outros canais. Sério com o dinheiro, gentil com a pessoa; nunca cobra. Os estados
  são eventos de domínio emitidos por `Tino`; cada canal os traduz. Detalhes em
  `local/personagem/decisoes-personagem.md`.

## Decisões em aberto

- **Contrato do MCP:** campos estruturados (valor, categoria, data) ou texto
  livre passado pela `Entrada`.
- **Público:** só o dono ou pessoas de fora. Define o peso de login e o que
  `docs/PRIVACIDADE.md` precisa garantir.

## Regras do projeto

- O teste que falha vem antes da correção.
- Valem as regras inegociáveis de `docs/ARQUITETURA.md` (toda query filtra por
  `usuario`, centavos em inteiro, data futura rejeitada, nada de token ou dado
  financeiro em log).
