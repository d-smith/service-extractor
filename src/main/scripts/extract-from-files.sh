#!/bin/sh
for dumpfile in "$@"
do
    echo "processing $dumpfile"
    extract-services.sh $dumpfile $EXTRACTOR_USER $EXTRACTOR_PASSWORD $EXTRACTOR_URL
done
