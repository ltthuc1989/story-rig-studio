#!/bin/zsh
# Joint check sheet: every cut in the 16 extreme poses of DanceClips.STRESS, full body, one sheet per cut.
# Run after every change to the cut (tools), the rig or the clips (shared), look at every tile of every sheet before
# saying a joint is fixed; compare with the sheets of the last good commit.
#   tools/joint_sheet.sh <outDir> [cuts, default every cut in the app]
# Feet are not pinned (feet=0): each pose shows exactly the angles keyed.
# Writes <outDir>/<cut>.png (8 x 2 tiles, poses 0-7 on top, 8-15 below) and <outDir>/<cut>/pose_NN.png at full size
# (crop those to zoom on a joint). Poses: 0 rest, 1 arms out, 2 V, 3 hands to chest, 4 elbows bent up, 5 hands
# turned over, 6 knee up, 7 sit, 8 kick back, 9 ankles turned, 10 bow, 11 head tilt, 12 hair and skirt swung,
# 13 arms forward, 14 hands on hips, 15 stride.
#   tools/joint_sheet.sh <outDir> <cuts> green
# Joint check by numbers: each cut is rendered from a copy in which every invented pixel is green (greencut), on a flat
# magenta background (bg=0); `jointcheck` then counts, per pose, the green that shows and the holes in the figure, and
# compares with tools/jointcheck_baseline.txt. The reference image of a cut is assets/<char>/gemini/{ref,view_front,
# view_back}.png (old flow) or assets/<char>/design/view_<view>.png (minimal flow), whichever exists; REF=gemini forces
# the old flow's image (a character redesigned while the app still has its old cut).
set -e
out=${1:?usage: tools/joint_sheet.sh <outDir> [kai,kai_front,...] [green]}
cd "${0:A:h}/.."
files=shared/src/commonMain/composeResources/files
cuts=${2:-$(ls $files | grep -E '^(kai|mia|leo|hana|bo)(_front|_back)?$' | paste -sd, -)}
green=${3:-}
mkdir -p $out
# Scene time starts after 200 warm-up frames; at 120 BPM a beat is 30 frames. Pose k is held during beat k of the
# 16-beat clip: shoot the second loop (beats 16-31), at 0.85 of each beat.
picks=()
for k in {0..15}; do picks+=$(( ((16 + k) * 30 + 25) - 200 )); done
for c in ${(s:,:)cuts}; do
  raw=$out/$c/raw
  rm -rf $out/$c; mkdir -p $raw
  rc=$c; extra=""
  if [[ $green == green ]]; then
    char=${c%%_*}; view=${c#$char}; view=${view#_}; view=${view:-3q}
    ref=assets/$char/design/view_$view.png
    [[ -f $ref && ${REF:-design} == design ]] || ref=assets/$char/gemini/$([[ $view == 3q ]] && echo ref.png || echo view_$view.png)
    rc=_g_$c; extra="bg=0"
    ./gradlew :tools:run -q --args="greencut $files/$c $ref $files/$rc" | grep -v "^$" | tail -1
  fi
  ./gradlew :desktopApp:snapshot -q --args="out=$raw scene=kai char=$rc count=1 art=mesh dance=stress feet=0 $extra w=2400 h=2400 frames=$(( picks[-1] + 1 )) pick=${(j:,:)picks}" >/dev/null 2>&1
  if [[ $green == green ]]; then
    # Second pass, every part tinted with its index (ids=1): which parts bound a hole (jointcheck).
    ./gradlew :desktopApp:snapshot -q --args="out=$raw/ids scene=kai char=$rc count=1 art=mesh dance=stress feet=0 bg=0 ids=1 w=2400 h=2400 frames=$(( picks[-1] + 1 )) pick=${(j:,:)picks}" >/dev/null 2>&1
    for k in {0..15}; do
      ffmpeg -loglevel error -y -i $raw/ids/frame_$(printf %04d ${picks[$((k + 1))]}).png -vf "crop=760:1300:820:480" $out/$c/ids_$(printf %02d $k).png
    done
  fi
  [[ $rc == _g_* ]] && rm -rf "${files:?}/${rc:?}"
  tiles=()
  for k in {0..15}; do
    f=$raw/frame_$(printf %04d ${picks[$((k + 1))]}).png
    p=$out/$c/pose_$(printf %02d $k).png
    # The character's box (Bo, 1.35 tall, fits too), then a small tile for the sheet.
    ffmpeg -loglevel error -y -i $f -vf "crop=760:1300:820:480" $p
    ffmpeg -loglevel error -y -i $p -vf "scale=380:650:flags=lanczos" $out/$c/tile_$k.png
    tiles+=(-i $out/$c/tile_$k.png)
  done
  ffmpeg -loglevel error -y $tiles -filter_complex "[0][1][2][3][4][5][6][7]hstack=8[a];[8][9][10][11][12][13][14][15]hstack=8[b];[a][b]vstack" $out/$c.png
  # Squash per pose (Snapshot's DistortProbe), in pose order: jointcheck fails a pose that draws a picture deformed.
  [[ -f $raw/distort.csv ]] && grep -v '^#' $raw/distort.csv > $out/$c/distort.csv
  rm -rf $raw $out/$c/tile_*.png
  echo $out/$c.png
  [[ $green == green ]] && ./gradlew :tools:run -q --args="jointcheck $out $c ${4:-}" | grep -v "^$"
done
