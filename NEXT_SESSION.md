# NEXT_SESSION.md — việc cho session mới

Đọc `CLAUDE.md` và `ART_PIPELINE.md` (nhất là mục 1, 2, 4, 7), rồi làm theo bảng tiến độ ở mục 7, bắt đầu từ **Bước 0**.

## Quy tắc

1. **Làm trọn một nhân vật trước: Kai.** Gồm 2 tờ Gemini, `sheetcheck`, lắp rig, mọi góc, engine, rồi xem trong app. Người dùng xác nhận Kai ổn hết mới sang nhân vật khác, mỗi lần một nhân vật (Mia → Leo → Hana → Bo). Không gọi Gemini cho nhân vật khác khi chưa xong Kai.
2. **Không gọi Gemini khi người dùng chưa duyệt riêng lượt đó.**
   - Trước khi gọi: cho người dùng xem tờ mẫu và prompt.
   - Sau khi gọi: chạy `sheetcheck`, báo kết quả bằng lời thường, kèm ảnh khoanh lỗi và số lượt đã gọi.
   - Lỗi thì đề xuất gọi lại 1 lượt, không tự gọi.
3. **Mỗi bước:**
   - soi `tools/joint_sheet.sh <outDir>` (16 tư thế cực hạn × 11 bộ cắt), xem hết mọi ô, phóng to khớp vừa sửa từ `<cut>/pose_NN.png`, so với bảng của commit trước;
   - trước khi làm một bước, tự hỏi bước đó có sửa đúng gốc lỗi người dùng thấy không (quy tắc khớp chuẩn, ART_PIPELINE.md mục 4 và 8);
   - chạy selftest (`./gradlew :tools:run --args="selftest <dir>"`) và `./gradlew :desktopApp:motionCheck`;
   - commit bằng tiếng Việt, cập nhật mục 7 của `ART_PIPELINE.md`;
   - dừng app cũ và mở lại app desktop cho người dùng xem.
4. **Lượt Gemini:** ngày 2026-10-18 đã dùng 12/20 lượt paid. Từ 2026-10-19 dùng `STORYRIG_TIER=free` (chỉ là chờ giữa các lượt, tiền vẫn tính).
5. Trả lời người dùng bằng tiếng Việt.
