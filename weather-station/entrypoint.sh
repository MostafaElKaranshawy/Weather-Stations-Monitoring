#!/bin/sh

if [ -z "$STATION_ID" ]; then
    ORDINAL=$(hostname | awk -F '-' '{print $NF}')
    if [ ! -z "$ORDINAL" ]; then
        export STATION_ID=$((ORDINAL + 1))
    fi
fi

exec java -jar app.jar
