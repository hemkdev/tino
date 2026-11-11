#!/usr/bin/env bash
# Quem usa o Tino e quanto, para o mantenedor. Lê só contagens e datas: valores, descrições e
# nomes de categoria ficam de fora (docs/PRIVACIDADE.md, "O que o mantenedor vê"). O @ vem do
# Discord na hora e não é guardado. A saída fica só no terminal.
set -euo pipefail
cd "$(dirname "$0")"
set -a
# shellcheck source=/dev/null
. ./.env
set +a

sql() { docker compose exec -T db psql -U tino -d tino -X -A -t -F '|' -c "$1"; }

IFS='|' read -r contas novas ativas lancamentos <<< "$(sql "
SELECT (SELECT count(*) FROM conta),
       (SELECT count(*) FROM conta WHERE criado_em >= now() - interval '7 days'),
       (SELECT count(DISTINCT usuario) FROM lancamento WHERE criado_em >= now() - interval '7 days'),
       (SELECT count(*) FROM lancamento WHERE criado_em >= now() - interval '7 days')")"
echo "Contas: $contas | novas em 7 dias: $novas | com lançamento em 7 dias: $ativas | lançamentos em 7 dias: $lancamentos"
echo

printf '%-24s %-10s %11s %5s %10s %-10s\n' usuario nasceu lancamentos fixos categorias ultimo
sql "
SELECT coalesce(i.externo, '-'),
       to_char(c.criado_em, 'YYYY-MM-DD'),
       (SELECT count(*) FROM lancamento l WHERE l.usuario = c.id),
       (SELECT count(*) FROM recorrente r WHERE r.usuario = c.id),
       (SELECT count(*) FROM categoria k WHERE k.usuario = c.id),
       coalesce((SELECT to_char(max(l.criado_em), 'YYYY-MM-DD') FROM lancamento l
                 WHERE l.usuario = c.id), '-')
FROM conta c LEFT JOIN identidade i ON i.conta_id = c.id AND i.provedor = 'discord'
ORDER BY c.id" | while IFS='|' read -r externo nasceu lanc fixos cats ultimo; do
  arroba=""
  if [ "$externo" != - ]; then
    # Sem resposta do Discord (conta apagada, limite de requisições), mostra o id.
    arroba=$(curl -fsS -m 10 -H "Authorization: Bot $DISCORD_TOKEN" \
      "https://discord.com/api/v10/users/$externo" 2>/dev/null \
      | grep -o '"username": *"[^"]*"' | cut -d'"' -f4 || true)
  fi
  printf '%-24s %-10s %11s %5s %10s %-10s\n' "${arroba:-$externo}" "$nasceu" "$lanc" "$fixos" "$cats" "$ultimo"
done
