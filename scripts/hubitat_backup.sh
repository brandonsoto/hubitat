#!/bin/bash
# Script to download hubitat backup

export HUBITAT_BACKUP_DIR=TODO
export HUBITAT_LOGIN=TODO
export HUBITAT_PW=TODO
export HUBITAT_ADDR=TODO
export COOKIE_FILE="/tmp/hubitat_cookie"
export MAX_HUBITAT_BACKUPS=5

cd $HUBITAT_BACKUP_DIR

# get cookie
curl -k -c $COOKIE_FILE -d username=$HUBITAT_LOGIN -d password=$HUBITAT_PW $HUBITAT_ADDR/login

# delete old backups
ls -1tr | head -n -$MAX_HUBITAT_BACKUPS | xargs -d '\n' rm -f --

# download latest backup
curl -s -X GET -k -b $COOKIE_FILE $HUBITAT_ADDR/hub/backup | grep data-fileName | grep download | sed 's/<td class=\"mdl-data-table__cell--non-numeric\"><a class=\"download mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect\" href=\"#\"  data-fileName=\"/\ /' | sed 's/\">Download<\/a><\/td>/\ /' | sed 's/ //g' | tail -1 | xargs -I @ curl -s -X GET -k -b $COOKIE_FILE -o @ $HUBITAT_ADDR/hub/backupDB?fileName=@
