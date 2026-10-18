#!/bin/zsh
# Shoulder and armpit check sheet: one row per character, the poses that broke before, side by side, upper body only:
#   idle (arms hanging), wave (arm raised, bent), pump: arms out, going up, V.
# Run after every change to the cut (tools) or the rig/clips (shared), and look at it before saying it is fixed.
#   tools/pose_sheet.sh <out.png> [chars, default the five front cuts]
set -e
out=${1:?usage: tools/pose_sheet.sh <out.png> [kai_front,mia_front,...]}
chars=${2:-kai_front,mia_front,leo_front,hana_front,bo_front}
cd "${0:A:h}/.."
tmp=$(mktemp -d)
rows=()
for c in ${(s:,:)chars}; do
  top=740; [[ $c == bo* ]] && top=600
  for d in idle wave pump; do
    ./gradlew :desktopApp:snapshot -q --args="out=$tmp/$c/$d scene=kai char=$c count=1 art=mesh dance=$d w=1600 h=1600 frames=130 pick=55,100,106,125" >/dev/null 2>&1
  done
  tiles=()
  for f in idle/frame_0100 wave/frame_0100 pump/frame_0055 pump/frame_0106 pump/frame_0125; do
    t=$tmp/$c/$(echo $f | tr / _).png
    # Shoulders only, enlarged: the box follows the character's height (Bo is taller than the children).
    ffmpeg -loglevel error -y -i $tmp/$c/$f.png -vf "crop=400:240:600:${top:-740},scale=500:300:flags=lanczos" $t
    tiles+=(-i $t)
  done
  ffmpeg -loglevel error -y $tiles -filter_complex hstack=5 $tmp/row_$c.png
  rows+=(-i $tmp/row_$c.png)
done
if (( ${#rows} > 2 )); then ffmpeg -loglevel error -y $rows -filter_complex vstack=$(( ${#rows} / 2 )) $out; else cp $tmp/row_*.png $out; fi
rm -rf "${tmp:?}"
echo "$out"
