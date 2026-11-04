<p align="center">
  <img src="docs/marca/svg/ocioso.svg" width="160" alt="O Tino, um brotinho azul com uma moeda no colo">
</p>

<h1 align="center">Tino</h1>

<p align="center">
  Finanças pessoais por conversa, em Java.<br>
  Você escreve o gasto como falaria; o Tino anota, categoriza e mostra para onde o dinheiro vai.
</p>

```
50 mercado
+3000 salário
50 mercado, 30 uber, 12 café
```

O nome vem de *ter tino*: juízo, discernimento. Ele é um brotinho que guarda moeda,
**sério com o dinheiro e gentil com a pessoa**. Quando um valor foge muito do seu
normal, ele pergunta antes de gravar:

<p align="center">
  <img src="docs/marca/svg/alerta.svg" width="96" alt="O Tino em alerta"><br>
  "R$ 4.800 em Moradia? Seu normal é 1.800." <b>[Tá certo]</b> <b>[Corrigir]</b>
</p>

Estados, falas e próximos traços em [`docs/PERSONAGEM.md`](docs/PERSONAGEM.md).

## Usar

**[Adicionar o Tino ao Discord](https://discord.com/oauth2/authorize?client_id=1535474778180292628)**,
instalar na sua conta (*Add to my apps*) e mandar `ajuda` na DM.

| Comando | O que faz |
|---|---|
| `/gasto` · `/ganho` | Registra um valor, com categoria, descrição e data opcionais |
| `/lancar` | Vários de uma vez: `50 mercado, 30 uber, +3000 salário` |
| `/resumo` | Por categoria, com variação e projeção do mês |
| `/lancamentos` · `/exportar` | Lista com os ids · CSV |
| `/remover` · `/categoria` · `/fixo` | Apagar, organizar categorias, lançamentos fixos |

Na DM também vale perguntar sem comando: **quanto gastei esse mês?**, **extrato de hoje**.
Cada pessoa vê só os próprios dados; o que é guardado está em
[`docs/PRIVACIDADE.md`](docs/PRIVACIDADE.md).

## Arquitetura

O núcleo (`tino.nucleo`) não conhece o Discord. Canais (`tino.discord`, e depois
site, Telegram e MCP) falam só com a classe `Tino`; o SQL é package-private, então
o compilador impede qualquer canal de gravar por fora. Lançar devolve um `Evento`
selado, e cada canal decide como mostrar:

```java
return switch (tino.lancar(usuario, pedido)) {
    case Evento.Registrado(Tino.Registro r) -> registro(usuario, r);
    case Evento.ForaDoNormal f -> perguntar(usuario, f);
};
```

Java 25, JDA, SQLite e JUnit, sem framework e sem ORM. Detalhes em
[`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).

## Rodar

```bash
mvn package
DISCORD_TOKEN="seu-token" java -jar target/tino.jar
```

Os dados ficam em `tino.db` (ou em `BANCO_CAMINHO`). `DISCORD_GUILD_ID` registra os
comandos numa guild só, para testar sem esperar o registro global.

## Sobre

Escrito em par com [Claude Code](https://claude.com/claude-code). Começou em agosto
de 2026 como bot de Discord; em outubro foi retomado parte por parte, com o núcleo
separado do canal e o personagem desenhado do zero. Referências: Porquim IA e
[money-flow](https://github.com/guithepc/money-flow).

MIT — veja [LICENSE](LICENSE).
