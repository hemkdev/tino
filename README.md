<div align="center">

[![CI](https://img.shields.io/github/actions/workflow/status/hemkdev/tino/ci.yml?branch=main&style=flat&logo=githubactions&logoColor=white&label=CI)](https://github.com/hemkdev/tino/actions)
[![Licença](https://img.shields.io/github/license/hemkdev/tino?style=flat&label=licen%C3%A7a)](LICENSE)
[![Adicionar ao Discord](https://img.shields.io/badge/adicionar%20ao-Discord-5865F2?style=flat&logo=discord&logoColor=white)](https://discord.com/oauth2/authorize?client_id=1535474778180292628)

![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat&logo=openjdk&logoColor=white)
![JDA](https://img.shields.io/badge/JDA-5.6-5865F2?style=flat&logo=discord&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=flat&logo=postgresql&logoColor=white)

<img src="docs/marca/svg/ocioso.svg" width="160" alt="O Tino, um brotinho azul com uma moeda no colo">

</div>

# Tino

Finanças pessoais **por conversa**: você escreve o gasto como falaria e o Tino anota, categoriza e
mostra para onde o dinheiro vai. O nome vem de *ter tino*, juízo. Ele é um brotinho que guarda
moeda, sério com o dinheiro e gentil com a pessoa.

Hoje o canal é o **Discord**. O núcleo não depende dele, e site, Telegram e um conector MCP são os
próximos canais sobre a mesma conta.

## O que é

```
50 mercado
+3000 salário
50 mercado, 30 uber, 12 café
```

Na DM, sem barra e sem formulário. Também vale perguntar: **quanto gastei esse mês?**, **extrato
de hoje**. O que ele não entende recebe o jeito certo de escrever, nunca silêncio. Cada
lançamento mostra o acumulado da categoria no mês, com botões para desfazer e editar, e o
`/resumo` traz as categorias em barras, a variação sobre o período anterior e a projeção do mês.

Na DM, o Tino pode aparecer offline mesmo no ar: o Discord só mostra a presença de um bot (o
online e o "Me manda: 50 mercado") para quem divide um servidor com ele. Para saber se ele está
respondendo, basta mandar uma mensagem.


## Os estados

| Anotou | Valor fora do normal | Não entendeu | De madrugada |
|:---:|:---:|:---:|:---:|
| <img src="docs/marca/svg/sucesso.svg" width="110"> | <img src="docs/marca/svg/alerta.svg" width="110"> | <img src="docs/marca/svg/erro.svg" width="110"> | <img src="docs/marca/svg/dormindo.svg" width="110"> |
| "Registrado: **R$ 132,40** em Mercado." | "R$ 5.000 em Mercado? Seu normal é 300." **[Tá certo] [Corrigir]** | "Não achei o valor. Manda assim: `23,50 uber`." | "Hm… anotado. Vai dormir também." |

No alerta ele **só grava depois da resposta**: o `5000` digitado no lugar de `50,00` não vira
histórico. Toda pergunta vem com botões, e quem julga o gasto é a pessoa, nunca o bot.

## Como funciona

```mermaid
flowchart LR
    msg["Mensagem no Discord<br/>50 mercado"] --> bot["Bot<br/>tino.discord"]
    bot --> tino["Tino<br/>tino.nucleo"]
    tino --> entrada["Entrada<br/>interpreta o texto"]
    tino --> fin[("Financas<br/>Postgres")]
    tino --> ev{"Evento"}
    ev -- Registrado --> vis["Visual<br/>resposta com o Tino"]
    ev -- ForaDoNormal --> pend["Visual<br/>pergunta antes"]
    pend -. "Tá certo: confirmar(chave)" .-> tino
```

O canal só recebe a mensagem e formata a resposta. Quem interpreta, calcula e grava é o núcleo:
`Tino.lancar` devolve um `Evento`, que é `Registrado` ou `ForaDoNormal`. Quando o valor passa de
5× a média recente da categoria, o núcleo guarda o pedido como pendente e o canal pergunta antes;
a resposta volta por `Tino.confirmar` ou `Tino.descartar`. Um canal novo (site, Telegram, MCP) fala só com a classe `Tino`.

## Comandos

| Comando | O que faz |
|---|---|
| `/gasto` · `/ganho` | Registra um valor, com categoria, descrição e data opcionais |
| `/lancar` | Vários de uma vez: `50 mercado, 30 uber, +3000 salário` |
| `/resumo` | Por categoria, com variação e projeção do mês |
| `/lancamentos` · `/exportar` | Lista com os ids · CSV |
| `/remover` · `/categoria` · `/fixo` | Apagar, organizar categorias, lançamentos fixos |
| `/apagar-tudo` | Apaga todos os seus dados e a conta, com confirmação |
| `/ajuda` · `/help` | Como escrever e o que cada comando faz |

O bot roda numa VM, com Postgres na própria máquina e backup diário criptografado no Cloudflare
R2; o deploy sai a cada release. Como publicar, voltar uma versão e restaurar está em
[operacao.md](docs/operacao.md).

Cada pessoa vê só os próprios dados. O que é guardado e como apagar está em
[PRIVACIDADE.md](docs/PRIVACIDADE.md); as regras de uso, em [TERMOS.md](docs/TERMOS.md).

## Garantias

- **Toda query filtra pela conta.** Sem exceção: ninguém lê o lançamento de outra pessoa.
- **Dinheiro em centavos inteiros.** Nunca `double`: ponto flutuante em dinheiro é bug.
- **Valor estranho não grava sozinho.** Acima de 5× a média recente da categoria, ele pergunta
  antes, e o pedido pendente tem chave aleatória que só a dona confirma.
- **A fronteira é do compilador, não da disciplina.** `Financas`, que tem o SQL, é
  package-private: chamar o banco de fora do núcleo não compila. E o `Evento` é `sealed`: um
  evento novo que algum canal esqueça de tratar também não compila.
- **Data futura é rejeitada**, e o valor precisa ser maior que zero, com no máximo duas casas.
- **Nada de token ou dado financeiro em log.**
- **Testes a cada PR**, contra um Postgres 17 de verdade, no GitHub Actions (`mvn verify`).

As regras completas e o porquê de cada uma estão em [banco.md](docs/banco.md) e
[decisoes.md](docs/decisoes.md).

## Rodar

Precisa de Java 25, Maven e Docker (ou Podman) para o Postgres.

```bash
docker compose up -d                  # Postgres 17 em localhost:5433
mvn verify                            # testes, num banco tino_teste à parte
mvn package

export DATABASE_URL=jdbc:postgresql://localhost:5433/tino DATABASE_USER=tino DATABASE_PASSWORD=tino
java -jar target/tino.jar migrar      # aplica as migrations e sai
DISCORD_TOKEN="seu-token" java -jar target/tino.jar
```

Sem alguma dessas variáveis, o bot sai dizendo qual falta. Com o esquema atrasado, não sobe e
pede o `migrar`. `DISCORD_GUILD_ID` registra os comandos numa guild só, o que é imediato; sem ela
o registro é global, necessário para a DM. `BATIMENTO_URL`, opcional, recebe um GET a cada 5 minutos
enquanto o bot está conectado (um check do healthchecks.io, que avisa quando os pings param).

Com Podman, o `docker compose` precisa do socket dele:
`systemctl --user start podman.socket` e
`export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock`.

## Estrutura

```
src/main/java/tino/
├── nucleo/    Tino (casos de uso), Financas (SQL), Entrada, Dinheiro, Evento e os records do domínio
└── discord/   Bot (eventos do JDA), Visual (embeds e botões), Estado
src/main/resources/db/migration/   migrations do Flyway (V1__esquema_inicial.sql, ...)
src/test/java/tino/   testes do núcleo e do canal, contra o Postgres do docker-compose.yml
docs/
├── decisoes.md       o que foi escolhido, o que foi recusado e por quê
├── banco.md          esquema e regras que não são negociáveis
├── operacao.md       deploy, rollback, backup e restauração
├── personagem.md     estados, falas e os próximos traços
├── PRIVACIDADE.md    o que é guardado e como apagar
├── TERMOS.md         regras de uso do bot
└── marca/            o Tino em SVG, GIF e PNG
deploy/              compose e backup da VM
.github/workflows/    CI (lint, testes, build arm64) e deploy por release
```

## Limitações

- **Só Discord por enquanto.** O núcleo já é independente, mas site, Telegram e MCP ainda não existem.
- **Um processo, uma conexão e um lock.** O bot é público, mas pequeno: se um dia houver
  concorrência de verdade, a troca é por um pool, sem mexer na API.

## Roadmap

- [x] Núcleo separado do Discord, com a fronteira garantida pelo compilador
- [x] Personagem nas respostas e alerta que pergunta antes de gravar
- [x] Regras no núcleo: pendentes que sobrevivem a reinício, relógio e fuso únicos
- [x] Postgres com migrations versionadas, testado contra o banco de verdade
- [ ] Traços do personagem: planta, ausência, impulso, vigiar categoria
- [ ] Site e conector MCP sobre a mesma conta
- [x] No ar numa VM, com deploy por release, backup e alerta de queda

## Créditos

A técnica de animação do personagem (camadas em SVG, com cada estado animado por CSS) veio do
estudo do [LabDots](https://github.com/vitorcgo/LabDots), de Vitor Cavalcante, licenciado em MIT.
O código daqui foi escrito do zero sobre essa ideia.

## Apoie

Se o repo te ajudou, deixa uma ⭐. Valeu :)
