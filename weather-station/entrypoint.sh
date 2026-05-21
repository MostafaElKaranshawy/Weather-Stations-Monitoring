#!/bin/sh

# If STATION_ID is not set, try to derive it from the hostname (for StatefulSet)
if [ -z "$STATION_ID" ]; then
    # Hostname is expected to be something like "weather-station-0"
    ORDINAL=$(hostname | awk -F '-' '{print $NF}')
    if [ ! -z "$ORDINAL" ]; then
        # Station IDs are 1-indexed in the default code, so add 1
        export STATION_ID=$((ORDINAL + 1))
        echo "Derived STATION_ID=$STATION_ID from hostname $(hostname)"
    fi
fi

exec java -jar app.jar
