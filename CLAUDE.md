# CLAUDE.md — StoryRig perf spike

Repo này là bản **thử hiệu năng** trước khi viết engine hoạt hình StoryRig. Câu hỏi duy nhất: Compose Canvas trên Android tầm trung có vẽ được các cảnh nặng nhất ở 60 khung/giây không. Đọc `SPIKE.md` (việc cần làm, cách đo, ngưỡng) và `REFERENCE_PRODUCTS.md` (vì sao chọn các cảnh này) trước khi làm.

Người dùng là Android developer, trao đổi bằng tiếng Việt. Trả lời và viết commit message bằng tiếng Việt; tên trong code bằng tiếng Anh.

## Quy tắc

- **Thời hạn 3 ngày.** Cuối mỗi ngày dừng lại, gửi số đo và video theo bảng lịch ở `SPIKE.md` mục 8, chờ người dùng đồng ý rồi làm tiếp.
- **Chỉ báo số đo lấy từ máy thật**, bản build `benchmark` (không debuggable, có R8). Không báo số từ bản debug, emulator hay desktop.
- **Không báo "đạt" khi chưa có số.** Mọi kết luận phải dẫn số trong `REPORT.md`.
- **Độ phức tạp phải tương đương demo HTML** trong `reference/`. Nếu giảm bớt (ít hình, ít hạt) thì ghi rõ trong báo cáo, kèm lý do.
- **Không gian lận:** không vẽ sẵn cả khung, không phát video thay cho vẽ thật.
- **Vòng vẽ không cấp phát bộ nhớ:** dùng lại `Path` (`reset()`), `FloatArray`, ma trận; không tạo `List`, `Offset` hay lambda mới mỗi khung khi tránh được. Kiểm tra bằng allocation tracking.
- **Dữ liệu tách khỏi code vẽ:** hình là chuỗi path SVG và bảng xương, code vẽ chung cho mọi nhân vật. Cảnh có thể dựng bằng Kotlin cho nhanh, không cần JSON.
- **Phương án B** (`SPIKE.md` mục 7) chỉ thử khi chưa đạt, theo đúng thứ tự, và luôn đo trước và sau.
- Hỏi người dùng trước khi thêm thư viện ngoài danh sách: Compose Multiplatform, kotlinx.coroutines, AndroidX Media3 (ExoPlayer), AndroidX Benchmark/Macrobenchmark, AndroidX Metrics (JankStats), UiAutomator, Baseline Profile plugin.

## Đo trên máy

- Kiểm tra máy đã cắm: `adb devices`.
- Cài và mở một cảnh: build `benchmark`, rồi `adb shell am start -n <package>/<activity> --es scene p1`.
- Đối chiếu nhanh: `adb shell dumpsys gfxinfo <package> framestats`.
- Quay màn hình: `adb shell screenrecord /sdcard/p1.mp4`, rồi `adb pull`.
- Số chính thức lấy từ Macrobenchmark: `./gradlew :benchmark:connectedBenchmarkAndroidTest` (hoặc task tương ứng của phiên bản đang dùng).
- Mở ảnh chụp màn hình ra xem bằng công cụ đọc ảnh trước khi nói một cảnh "đã giống demo".

## Repo khác

- `../kmp-template-impl` (nếu được thêm bằng `--add-dir`) chỉ để đọc phiên bản Kotlin, Compose, AGP cho khớp. Không sửa gì trong đó.
