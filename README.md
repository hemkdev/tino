# finance-bot

Bot de Discord para controle financeiro pessoal, escrito em Java.

Registra ganhos e despesas por categoria e mostra quanto foi gasto em cada uma,
com a variação de um período para o outro.

Plano em [`docs/PLANO.md`](docs/PLANO.md).

## Configuração

```bash
export DISCORD_TOKEN="seu-token"
java -jar target/finance-bot.jar
```

Sem `DISCORD_GUILD_ID` os comandos são registrados globalmente, que é o que os
faz existir em conversa privada com o bot. Definindo a variável, eles passam a
valer só naquele servidor — o registro é imediato, o que ajuda ao mexer nas
assinaturas dos comandos.

Para usar o bot sem estar em servidor nenhum, habilite **User Install** em
*Installation* no portal do Discord e instale pelo link gerado lá.
