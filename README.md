<div align="center">

[![CI](https://img.shields.io/github/actions/workflow/status/hemkdev/Tino/ci.yml?branch=main&style=flat&logo=githubactions&logoColor=white&label=CI)](https://github.com/hemkdev/Tino/actions)
[![Licença](https://img.shields.io/github/license/hemkdev/Tino?style=flat&label=licen%C3%A7a)](LICENSE)
[![Adicionar ao Discord](https://img.shields.io/badge/adicionar%20ao-Discord-5865F2?style=flat&logo=discord&logoColor=white)](https://discord.com/oauth2/authorize?client_id=1535474778180292628)

![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat&logo=openjdk&logoColor=white)
![JDA](https://img.shields.io/badge/JDA-5.6-5865F2?style=flat&logo=discord&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-3-003B57?style=flat&logo=sqlite&logoColor=white)

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
de hoje**. Cada lançamento mostra o acumulado da categoria no mês, com botões para desfazer e
editar, e o `/resumo` traz as categorias em barras, a variação sobre o período anterior e a
projeção do mês.

## Os estados

| Anotou | Valor fora do normal | Não entendeu | De madrugada |
|:---:|:---:|:---:|:---:|
| <img src="docs/marca/svg/sucesso.svg" width="110"> | <img src="docs/marca/svg/alerta.svg" width="110"> | <img src="docs/marca/svg/erro.svg" width="110"> | <img src="docs/marca/svg/dormindo.svg" width="110"> |
| "Registrado: **R$ 132,40** em Mercado." | "R$ 4.800 em Moradia? Seu normal é 1.800." **[Tá certo] [Corrigir]** | "Não achei o valor. Manda assim: `23,50 uber`." | "Hm… anotado. Vai dormir também." |

No alerta ele **só grava depois da resposta**: o `5000` digitado no lugar de `50,00` não vira
histórico. Toda pergunta vem com botões, e quem julga o gasto é a pessoa, nunca o bot.

## Comandos

| Comando | O que faz |
|---|---|
| `/gasto` · `/ganho` | Registra um valor, com categoria, descrição e data opcionais |
| `/lancar` | Vários de uma vez: `50 mercado, 30 uber, +3000 salário` |
| `/resumo` | Por categoria, com variação e projeção do mês |
| `/lancamentos` · `/exportar` | Lista com os ids · CSV |
| `/remover` · `/categoria` · `/fixo` | Apagar, organizar categorias, lançamentos fixos |

Cada pessoa vê só os próprios dados. O que é guardado e como apagar está em
[PRIVACIDADE.md](docs/PRIVACIDADE.md).

## Rodar

Precisa de Java 25 e Maven.

```bash
mvn package
DISCORD_TOKEN="seu-token" java -jar target/tino.jar
```

Os dados ficam em `tino.db` (ou no caminho de `BANCO_CAMINHO`). `DISCORD_GUILD_ID` registra os
comandos numa guild só, o que é imediato; sem ela o registro é global, necessário para a DM.

## Docs

[decisoes.md](docs/decisoes.md) tem o que foi escolhido, o que foi recusado e por quê: o núcleo
separado do canal, a fronteira garantida pelo compilador e o evento selado. [banco.md](docs/banco.md)
tem o esquema e as regras que não são negociáveis. [personagem.md](docs/personagem.md) tem os
estados, as falas e os próximos traços.

## Roadmap

- [x] Núcleo separado do Discord, com a fronteira garantida pelo compilador
- [x] Personagem nas respostas e alerta que pergunta antes de gravar
- [ ] Traços do personagem: planta, ausência, impulso, vigiar categoria
- [ ] Site e conector MCP sobre a mesma conta
- [ ] No ar numa VPS

## Apoie

Se o repo te ajudou, deixa uma ⭐. Valeu :)

MIT — veja [LICENSE](LICENSE).
