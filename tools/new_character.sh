#!/bin/zsh
# The minimal character flow (ART_PIPELINE.md section 3), one step at a time. Prints what to attach, the prompt to paste
# (brief filled in), where to save the image, and the check to run; runs the checks and the cut.
#   tools/new_character.sh <char> front|3q|face|check-front|check-3q|check-face|cut
# Needs assets/<char>/brief.txt (one English paragraph, see assets/templates/brief_template.md).
set -e
char=${1:?usage: tools/new_character.sh <char> <step>}
step=${2:?step: front|3q|face|check-front|check-3q|check-face|cut}
cd "${0:A:h}/.."
a=assets/$char; p=assets/templates/prompts/web; d=$a/design
brief=$a/brief.txt
[[ -f $brief ]] || { echo "thiếu $brief (xem assets/templates/brief_template.md)"; exit 1 }
fill() { python3 -I -c "import sys;t=open(sys.argv[1]).read();b=open(sys.argv[2]).read().strip();print(t.replace('<<BRIEF>>',b),end='')" $1 $brief }
section() { awk -v n=$2 '/^=== /{k++} k==n && !/^=== /' $1 }
mkdir -p $d
case $step in
  front)
    echo "## Bước 1: góc trực diện. Mở cuộc trò chuyện MỚI trên gemini.google.com, chế độ tạo ảnh (chọn model Pro nếu có)."
    echo "## Đính kèm: assets/templates/rig_front.png"
    echo "## Lưu hình về: $d/view_front.png (kích thước lớn nhất), rồi chạy: tools/new_character.sh $char check-front"
    echo; fill $p/views.txt | section /dev/stdin 1 ;;
  3q)
    echo "## Bước 2: góc 3/4. CÙNG cuộc trò chuyện với bước 1 (sau khi check-front ĐẠT)."
    echo "## Đính kèm: assets/templates/rig_3q.png"
    echo "## Lưu hình về: $d/view_3q.png, rồi chạy: tools/new_character.sh $char check-3q"
    echo; section $p/views.txt 2 ;;
  face)
    echo "## Bước 3: tờ mặt và tay. Cuộc trò chuyện MỚI."
    echo "## Đính kèm theo thứ tự: (1) $d/view_front.png  (2) assets/templates/rig_face.png"
    echo "## Lưu hình về: $d/face.png, rồi chạy: tools/new_character.sh $char check-face"
    echo; section $p/face.txt 1 ;;
  check-front) ./gradlew :tools:run -q --args="viewcheck $char front" ;;
  check-3q) ./gradlew :tools:run -q --args="viewcheck $char 3q" ;;
  check-face) ./gradlew :tools:run -q --args="facecheck $char" ;;
  cut)
    echo "## Bước 4: cắt (tools/build/cut/${char}_<view>), rồi joint_sheet.sh"
    ./gradlew :tools:run -q --args="cutview $char front" && ./gradlew :tools:run -q --args="cutview $char 3q" ;;
  *) echo "step: front|3q|face|check-front|check-3q|check-face|cut"; exit 1 ;;
esac
