# StoryRig — bộ thử hiệu năng

Mục đích: trước khi viết spec 2.0 cho engine hoạt hình dùng chung, đo xem Compose trên Android tầm trung có chạy được các cảnh nặng nhất ở 60 khung/giây không. Kết quả quyết định spec 2.0 dùng vector thuần, hay phải thêm kỹ thuật tăng tốc, hay phải đổi hướng.

| File | Dùng để |
| --- | --- |
| `REFERENCE_PRODUCTS.md` | 4 sản phẩm mẫu làm thước đo cho engine: bài hát, truyện, khám phá, phim ngắn lâu đài; bảng khả năng engine cần |
| `SPIKE.md` | Việc Claude Code phải làm: engine thu nhỏ, 4 cảnh thử, cách đo, ngưỡng đạt, phương án B, lịch 3 ngày |
| `CLAUDE.md` | Quy tắc Claude Code tự đọc mỗi phiên |
| `REPORT_TEMPLATE.md` | Mẫu báo cáo kết quả |
| `reference/` | Các demo HTML đã duyệt, để chép hình và chuyển động sang Compose |

## Các bước

1. Tạo project bằng Kotlin Multiplatform Wizard (https://kmp.jetbrains.com/): chọn Android + Desktop, giao diện dùng chung. Đặt tên `storyrig-perf-spike`, để trong `~/AndroidStudioProjects/` cạnh `kmp-template-impl`.
2. Chép các file trong thư mục này vào gốc project đó.
3. Cắm điện thoại Android tầm trung, bật USB debugging, kiểm tra bằng `adb devices`.
4. Mở Claude Code tại gốc project bằng `claude --add-dir ../kmp-template-impl` và nói:

> Đọc CLAUDE.md, SPIKE.md, REFERENCE_PRODUCTS.md. Project tạo từ KMP wizard, điện thoại đã cắm. Làm Ngày 1 theo SPIKE.md mục 8: thêm module benchmark và build type benchmark, viết engine thu nhỏ, chuyển cảnh P3 explore_lake từ reference/lake.html, đo trên máy, rồi dừng lại gửi mình số đo và video.

5. Cuối mỗi ngày xem số đo và video, đồng ý thì bảo Claude Code làm tiếp ngày sau.
6. Xong Ngày 3, gửi `REPORT.md` cho Claude (bên chat) để viết spec 2.0 theo kết quả.
