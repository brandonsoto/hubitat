#!/bin/bash
# Script to download hubitat backup
# https://community.hubitat.com/t/solved-downloading-latest-backup-file/18065/12

cd [DIRECTORY_OF_YOUR_BACKUPS]
ls -1tr | head -n -3 | xargs -d '\n' rm -f --
curl http://[HUBIPHERE]/hub/backup | grep data-fileName | grep download | sed 's/<td class=\"mdl-data-table__cell--non-numeric\"><a class=\"download mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect\" href=\"#\"  data-fileName=\"/\ /' | sed 's/\">Download<\/a><\/td>/\ /' | sed 's/ //g' | tail -1 | xargs -I @ curl -o @ http://[HUBIPHERE]/hub/backupDB?fileName=@
