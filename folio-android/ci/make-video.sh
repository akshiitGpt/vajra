#!/usr/bin/env bash
# Joins the screenrecord segments into one MP4 and makes a contact sheet of the screenshots.
OUT="$1"
cd "$OUT" || exit 0
ls folio_*.mp4 >/dev/null 2>&1 || exit 0
: > list.txt
for f in $(ls folio_*.mp4 | sort -V); do echo "file '$f'" >> list.txt; done
ffmpeg -y -loglevel error -f concat -safe 0 -i list.txt -c copy folio_demo_full.mp4 || true
# a compact 720p version that is easy to watch on a phone
ffmpeg -y -loglevel error -i folio_demo_full.mp4 -vf "scale=1280:-2" -c:v libx264 -preset veryfast -crf 28 -an folio_demo_720p.mp4 || true
rm -f list.txt
ls -la
