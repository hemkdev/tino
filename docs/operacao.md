# Operação

Como o Tino roda em produção, como publicar, voltar e restaurar. O porquê de cada escolha está
em [decisoes.md](decisoes.md).

O bot roda numa VM ARM, em `~/tino`, com o [`deploy/docker-compose.yml`](../deploy/docker-compose.yml):
um Postgres 17 (`db`) e o bot (`bot`, imagem `ghcr.io/hemkdev/tino`), os dois com
`restart: unless-stopped`. Nenhuma porta fica aberta: o bot só faz conexão de saída para o
Discord, e o deploy chega pelo túnel SSH da Cloudflare.

## Publicar

Publicar uma release `vX.Y.Z` no GitHub dispara o [workflow de deploy](../.github/workflows/deploy.yml):

1. Build da imagem num runner ARM, com push de `:vX.Y.Z` e `:latest` para o GHCR.
2. Na VM: `docker compose pull` e o `db` de pé.
3. `backup.sh deploy vX.Y.Z`: dump do banco atual, criptografado, no R2.
4. `docker compose run --rm bot migrar`. Se falhar, o deploy para aqui e o bot antigo segue no ar.
5. `docker compose up -d`, e o portão: o bot novo precisa escrever `bot no ar` no log em até
   90 s, senão o workflow fica vermelho.
6. Limpeza das imagens antigas.

## Voltar uma versão

Um deploy que falha não volta sozinho. Em Actions > Deploy > Run workflow, informe a tag
anterior: o workflow reaproveita a imagem que já está no GHCR e refaz os passos 2 a 6.

Se a versão nova migrou o esquema de um jeito que a anterior não entende, o boot da anterior se
recusa a subir ("o esquema do banco não bate"). Aí é restaurar o dump que o passo 3 fez antes
da migração, e só então voltar a versão.

## Restaurar um backup

A chave privada do `age` não fica na VM. O dump é baixado e descriptografado na máquina do
mantenedor e chega ao banco pelo SSH:

```bash
# 1. Baixe o arquivo .sql.gz.age do bucket (painel do R2 ou aws-cli com --endpoint-url).
# 2. Na VM, pare o bot e recrie o banco vazio:
ssh vm 'cd tino && docker compose stop bot \
  && docker compose exec -T db psql -U tino -d postgres -c "DROP DATABASE tino" -c "CREATE DATABASE tino"'
# 3. Descriptografe e restaure:
age -d -i tino-backup.key ARQUIVO.sql.gz.age | gunzip \
  | ssh vm 'cd tino && docker compose exec -T db psql -U tino -d tino -v ON_ERROR_STOP=1 -q'
# 4. Suba o bot com a versão que corresponde ao dump:
ssh vm 'cd tino && IMAGE_TAG=vX.Y.Z docker compose up -d bot'
```

Testado em 2026-11-09: um backup diário baixado do R2, aberto com a chave e restaurado num
Postgres vazio trouxe a conta e os lançamentos intactos. Vale repetir de tempos em tempos: backup
que nunca foi restaurado é só uma promessa.

Para testar um backup sem tocar na produção, restaure no Postgres local do `docker-compose.yml`
da raiz, no lugar do passo 2: `docker compose exec -T db psql -U tino -d postgres -c "CREATE
DATABASE restaurado"` e o passo 3 com `-d restaurado`, sem o `ssh`.

## Preparar a VM e as contas (uma vez)

**VM.** O usuário `deploy` já existe, com Docker. Em `~/tino`, crie o `.env` (`chmod 600`) com:

| Variável | O que é |
|---|---|
| `DB_PASSWORD` | senha do Postgres, longa e aleatória |
| `DISCORD_TOKEN` | token do bot no portal do Discord |
| `BATIMENTO_URL` | URL de ping do check de batimento no healthchecks.io |
| `BACKUP_PING_URL` | URL de ping do check de backup no healthchecks.io |
| `AGE_RECIPIENT` | chave pública do `age` (`age1...`) |
| `R2_ENDPOINT` | `https://<conta>.r2.cloudflarestorage.com` |
| `R2_BUCKET` | nome do bucket |
| `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | token do R2 |

E instale o `age`: `sudo apt install age`.

**Chave do age.** Na máquina do mantenedor, `age-keygen -o tino-backup.key`. A linha
`public key` vai para `AGE_RECIPIENT`; o arquivo fica fora da VM, com cópia num gerenciador de
senhas. Sem ele, nenhum backup se abre.

**Cloudflare R2.** Um bucket só para o Tino, com duas regras de ciclo de vida: `diario/` apaga
depois de 7 dias, `deploy/` depois de 30. O token é do tipo *Object Read & Write*, restrito a
esse bucket; o R2 não tem token só de escrita, mas o que dá para ler está criptografado.

**healthchecks.io.** Dois checks, com alerta por e-mail:
- backup: período de 1 dia, tolerância de 1 hora;
- batimento: período de 5 minutos, tolerância de 10.

**Cron.** `crontab -e` na VM, com o horário em UTC (o de Brasília é UTC−3):

```
MM HH * * * /home/deploy/tino/backup.sh diario >> /home/deploy/tino/backups/cron.log 2>&1
```

**GitHub.** No environment `production` do repositório, os secrets `SSH_HOST`,
`SSH_PRIVATE_KEY`, `SSH_KNOWN_HOSTS`, `CF_ACCESS_CLIENT_ID` e `CF_ACCESS_CLIENT_SECRET`. São os
mesmos acessos do racha-api, cadastrados de novo aqui.

**GHCR.** Depois da primeira release, deixe o pacote `tino` público (Package settings): a imagem
não tem segredo, e assim a VM baixa sem login.

## No dia a dia

```bash
docker compose logs -f bot        # logs, sem valores nem tokens
docker compose ps                 # estado e saúde
./backup.sh diario                # backup manual
./uso.sh                          # quem usa e quanto, sem valores (PRIVACIDADE.md)
```
