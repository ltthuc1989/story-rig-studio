# NEXT_SESSION.md — việc cho session mới

Đọc `CLAUDE.md` và `ART_PIPELINE.md` (mục 1, 2, 3, 7), rồi làm theo bảng tiến độ ở mục 7.

## Quy tắc

1. **Flow tối giản** (ART_PIPELINE mục 1): mỗi nhân vật 3 hình tạo trên Gemini web (trực diện và 3/4 ở dáng rig, tờ mặt 22 ô), kiểm bằng `viewcheck`/`facecheck`, cắt bằng `cutview`, kiểm khớp bằng `tools/joint_sheet.sh <out> <cuts> green` (`jointcheck` phải về 0 xanh, 0 lỗ). Không quay lại bộ kit 2 tờ, không gọi API Gemini (model ảnh không có free tier; web miễn phí).
2. **Làm trọn Kai trước.** Người dùng tạo hình (`tools/new_character.sh kai front|3q|face`), Claude chạy lệnh kiểm và chọn tin sửa trong `assets/templates/prompts/web/fixes.txt`. Khóa thiết kế khi đạt; sau đó lỗi sửa ở tool cắt hoặc engine. Người dùng duyệt tờ tổng rồi mới thay dữ liệu Kai trong app; xong Kai mới sang Mia → Leo → Hana → Bo.
3. **Mỗi lần sửa tool cắt, rig hay clip:** `tools/joint_sheet.sh <outDir> <cuts> green` và so với `tools/jointcheck_baseline.txt` (số tăng là hồi quy); selftest (`./gradlew :tools:run --args="selftest <dir>"`, bộ cắt cũ với tờ mẫu `assets/templates/legacy/`) và `./gradlew :desktopApp:motionCheck`; commit tiếng Việt; cập nhật ART_PIPELINE mục 7; dừng app cũ và mở lại app desktop.
4. **Đang ở bước 4–5 (Kai):** chờ người dùng tạo `assets/kai/design/view_front.png`; trong lúc đó viết `cutview` (ART_PIPELINE mục 7, bước 5): `cutWith` nhận tên file nguồn và cờ không dùng face sheet cũ; tờ mặt 22 ô (`rig_face.csv`) vào engine; tay chân mảnh cứng; giới hạn biên độ khớp; quay người bằng đổi cut.
5. Trả lời người dùng bằng tiếng Việt.
