# NEXT_SESSION.md — việc cho session mới

Đọc `CLAUDE.md` và `ART_PIPELINE.md` (nhất là mục 1, 2, 4, 7), rồi làm theo bảng tiến độ ở mục 7, bắt đầu từ **Bước 0**.

## Quy tắc

1. **Làm trọn một nhân vật trước: Kai.** Gồm 2 tờ Gemini, `sheetcheck`, lắp rig, mọi góc, engine, rồi xem trong app. Người dùng xác nhận Kai ổn hết mới sang nhân vật khác, mỗi lần một nhân vật (Mia → Leo → Hana → Bo). Không gọi Gemini cho nhân vật khác khi chưa xong Kai.
2. **Không gọi Gemini khi người dùng chưa duyệt riêng lượt đó.**
   - Trước khi gọi: cho người dùng xem tờ mẫu và prompt.
   - Sau khi gọi: chạy `sheetcheck`, báo kết quả bằng lời thường, kèm ảnh khoanh lỗi và số lượt đã gọi.
   - Lỗi thì đề xuất gọi lại 1 lượt, không tự gọi.
3. **Mỗi bước:**
   - soi `tools/pose_sheet.sh <out.png>` (trực diện) và `tools/pose_sheet.sh <out.png> kai,mia,leo,hana,bo` (3/4), phóng to các tư thế có giơ tay;
   - chạy selftest (`./gradlew :tools:run --args="selftest <dir>"`) và `./gradlew :desktopApp:motionCheck`;
   - commit bằng tiếng Việt, cập nhật mục 7 của `ART_PIPELINE.md`;
   - dừng app cũ và mở lại app desktop cho người dùng xem.
4. **Lượt Gemini:** ngày 2026-10-18 đã dùng 12/20 lượt paid. Từ 2026-10-19 dùng `STORYRIG_TIER=free` (chỉ là chờ giữa các lượt, tiền vẫn tính).
5. Trả lời người dùng bằng tiếng Việt.
