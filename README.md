<img src="docs/marca/avatar.png" width="120" align="right" alt="">

# Tino

Bot de Discord para controle financeiro pessoal, em Java. Registre gastos e
ganhos por categoria conversando na DM e veja para onde seu dinheiro está indo.

O nome vem de *ter tino* — juízo, discernimento. É o que ele faz: te dá noção de
para onde o dinheiro está indo, sem planilha e sem cerimônia.

```
50 mercado
50 mercado 07/08 pão e leite
+3000 salário
50 mercado, 30 uber, 12 café
```

Sem barra, sem formulário. Na conversa privada, é só digitar.

## Usar

**[Adicionar o Tino](https://discord.com/oauth2/authorize?client_id=1535474778180292628)**

Instale na sua conta (*Add to my apps*) e abra uma conversa com ele. O primeiro
comando já cria suas categorias iniciais. Comece com `/ajuda`.

Cada pessoa vê apenas os próprios lançamentos. O que é guardado, onde fica e como
apagar está em [`docs/PRIVACIDADE.md`](docs/PRIVACIDADE.md).

## Comandos

| Comando | O que faz |
|---|---|
| `/gasto <valor> [categoria] [descrição] [data]` | Registra uma despesa. Sem categoria, vai para Outros |
| `/ganho <valor> [categoria] [descrição] [data]` | Registra uma receita |
| `/lancar <texto>` | Vários de uma vez: `50 mercado, 30 uber, +3000 salário` |
| `/resumo [período]` | Por categoria, com saldo, variação e projeção do mês |
| `/lancamentos [período]` | Lista paginada, com os ids |
| `/exportar [período]` | Anexa um CSV |
| `/remover <id>` | Apaga um lançamento |
| `/categoria` | `listar`, `criar`, `renomear`, `excluir` |
| `/fixo` | Aluguel e assinaturas, lançados de uma vez |
| `/ajuda` | Exemplos de uso |

Período aceita `hoje`, `semana`, `mês` (padrão) ou `ano`.

Na DM você também pode só perguntar, sem comando: **quanto gastei esse mês?**,
**resumo da semana**, **extrato de hoje**, **meus fixos**, **ajuda**.

### Formato do texto livre

```
[+]valor  categoria  [data]  [descrição]
```

O `+` marca ganho; sem sinal é gasto. Depois da categoria, um campo que começa
com número é lido como data, e com letra como descrição — por isso a data, se
houver, vem antes.

Valor aceita `50`, `50,00`, `1.234,56` e `R$ 50`. Data aceita `7`, `07/08` e
`2026-08-07`, sempre no passado.

Subcategoria se escreve com `>`: `50 alimentação > mercado`. No resumo, ela soma
na categoria principal.

## Como funciona

Cada registro traz botões de **Desfazer** e **Editar**. O resumo mostra as
categorias em barras comparativas, a variação sobre o período anterior e a
projeção de quanto o mês deve fechar no ritmo atual.

Ao registrar, o bot mostra o acumulado da categoria no mês e avisa quando o valor
destoa muito do normal — o erro de digitar `5000` no lugar de `50,00` aparece na
hora, e o botão de desfazer está logo ali.

Categorias são criadas conforme você usa, com limite de 10 principais e 20
subcategorias para cada lado (gastos e ganhos), que são listas separadas.

## Rodar você mesmo

Precisa de Java 25 e Maven.

```bash
mvn package
export DISCORD_TOKEN="seu-token"
java -jar target/finance-bot.jar
```

Os dados ficam num arquivo SQLite (`financebot.db`, ao lado do jar). Para
backup, `sqlite3 financebot.db ".backup copia.db"`.

| Variável | Obrigatória | Para quê |
|---|---|---|
| `DISCORD_TOKEN` | sim | Token do bot |
| `DISCORD_GUILD_ID` | não | Registra os comandos só nessa guild, o que é imediato. Sem ela, o registro é global — necessário para funcionar na DM |
| `BANCO_CAMINHO` | não | Onde guardar o banco. Padrão: `financebot.db` |

Para criar o seu: [Discord Developer Portal](https://discord.com/developers/applications)
→ nova aplicação → **Bot** → *Reset Token*. Não precisa de nenhuma intent
privilegiada. Em **Installation**, habilite *User Install* para que funcione em
conversa privada.

## Estrutura

```
Bot.java        JDA: comandos, botões, modal e mensagens
Tino.java       núcleo: o que o bot faz, sem saber o canal (inclui CSV e projeção)
Financas.java   esquema, consultas e regras
Visual.java     embeds, botões e modal
Entrada.java    interpreta "50 mercado 07/08 pão"
Dinheiro.java   converte e formata valores em reais
```

Seis arquivos, sem framework e sem ORM. Os testes rodam contra SQLite em
memória, então a suíte inteira leva menos de um segundo.

```bash
mvn test
```

## Sobre a construção

Escrito em par com [Claude Code](https://claude.com/claude-code), da fase de
levantamento de requisitos até a implementação. O projeto começou com uma
arquitetura hexagonal completa, com portas, adaptadores e documento de
especificação — e foi deliberadamente reduzido ao que está aqui quando ficou
claro que a estrutura era maior que o problema. Esses commits continuam no
histórico.

Organização do código, banco e regras em [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md).

## Licença

MIT — veja [LICENSE](LICENSE).
