# finance-bot

Bot de Discord para controle financeiro pessoal, escrito em Java.

Registra ganhos e despesas por categoria e mostra quanto foi gasto em cada uma,
com a variação de um período para o outro.

Plano em [`docs/PLANO.md`](docs/PLANO.md).

## Configuração

```bash
export DISCORD_TOKEN="seu-token"
export DISCORD_GUILD_ID="id-da-sua-guild"   # comandos de guild aparecem na hora;
                                            # globais levam até uma hora
java -jar target/finance-bot.jar
```
