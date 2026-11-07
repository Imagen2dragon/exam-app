#!/bin/sh
# Copies the server database (usage, progress history, packs) to ./backups/ with today's date.
# Run from the deploy folder, e.g. daily from cron:  0 3 * * * cd /opt/exam-app/deploy && ./backup.sh
set -eu
mkdir -p backups
out="backups/tibeb-$(date +%F).db"
# SQLite's online backup gives a consistent copy while the server keeps running; it is streamed out.
docker compose exec -T api python -c "
import sqlite3, sys
src, dst = sqlite3.connect('/data/usage.db'), sqlite3.connect('/tmp/backup.db')
src.backup(dst); dst.close()
sys.stdout.buffer.write(open('/tmp/backup.db', 'rb').read())
" > "$out.part"
mv "$out.part" "$out"
find backups -name 'tibeb-*.db' -mtime +30 -delete
echo "Saved $out"
