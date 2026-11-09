#!/usr/bin/env bash
# Dump do banco, criptografado para a chave pública do mantenedor e enviado ao Cloudflare R2.
#   backup.sh diario         pelo cron; avisa o healthchecks.io ao terminar
#   backup.sh deploy <tag>   pelo deploy, antes de migrar
# A chave privada do age nunca fica na VM: daqui só sai arquivo que esta máquina não lê.
set -euo pipefail
cd "$(dirname "$0")"
set -a
# shellcheck source=/dev/null
. ./.env
set +a

agora=$(date -u +%Y-%m-%dT%H%M%SZ)
case "${1:-}" in
  diario) chave="diario/$agora.sql.gz.age" ;;
  deploy) chave="deploy/${2:?falta a tag}-$agora.sql.gz.age" ;;
  *) echo "uso: backup.sh diario | backup.sh deploy <tag>" >&2; exit 2 ;;
esac

mkdir -p backups
arquivo="backups/${chave//\//-}"
docker compose exec -T db pg_dump -U tino -d tino | gzip | age -r "$AGE_RECIPIENT" > "$arquivo"

# O R2 fala a API do S3. O aws-cli roda num container para não instalar nada na VM; as
# credenciais passam pelo ambiente, sem aparecer na linha de comando.
export AWS_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" AWS_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY"
docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION=auto \
  -v "$PWD/backups:/backups:ro" docker.io/amazon/aws-cli:2.17.0 \
  s3 cp "/$arquivo" "s3://$R2_BUCKET/$chave" --endpoint-url "$R2_ENDPOINT" --only-show-errors

# A cópia local serve para restaurar rápido; a retenção de verdade é a do bucket.
find backups -name '*.age' -mtime +7 -delete

if [ "$1" = diario ] && [ -n "${BACKUP_PING_URL:-}" ]; then
  curl -fsS -m 10 --retry 3 -o /dev/null "$BACKUP_PING_URL"
fi
