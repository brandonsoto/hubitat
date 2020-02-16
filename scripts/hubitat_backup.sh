#!/bin/bash
# Script to download hubitat backup

# !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
# Required environment variables:
#   - HUBITAT_BACKUP_DIR
#   - HUBITAT_LOGIN
#   - HUBITAT_PW
#   - HUBITAT_ADDR
# !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

cd $HUBITAT_BACKUP_DIR
export COOKIE_FILE="/tmp/hubitat_cookie"
export MAX_HUBITAT_BACKUPS=5


curl -k -c $COOKIE_FILE -d username=$HUBITAT_LOGIN -d password=$HUBITAT_PW $HUBITAT_ADDR/login

ls -1tr | head -n -$MAX_HUBITAT_BACKUPS | xargs -d '\n' rm -f --

curl -s -X GET -k -b $COOKIE_FILE $HUBITAT_ADDR/hub/backup | grep data-fileName | grep download | sed 's/<td class=\"mdl-data-table__cell--non-numeric\"><a class=\"download mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect\" href=\"#\"  data-fileName=\"/\ /' | sed 's/\">Download<\/a><\/td>/\ /' | sed 's/ //g' | tail -1 | xargs -I @ curl -s -X GET -k -b $COOKIE_FILE -o @ $HUBITAT_ADDR/hub/backupDB?fileName=@
