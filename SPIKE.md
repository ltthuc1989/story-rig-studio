# SPIKE.md — Thử hiệu năng engine hoạt hình vector trên Android

## 1. Câu hỏi cần trả lời

Compose Canvas trên một máy Android tầm trung có vẽ được những cảnh nặng nhất của 4 sản phẩm mẫu (`REFERENCE_PRODUCTS.md`) ở 60 khung/giây không, khi dùng đúng cách mà engine StoryRig định dùng: hình vector, cây xương, hình uốn mềm theo xương, hạt, nhiều lớp?

Kết quả quyết định spec 2.0:

| Kết luận | Điều kiện | Spec 2.0 sẽ |
| --- | --- | --- |
| **Đạt** | Cả 3 cảnh P1–P3 đạt ngưỡng ở mục 6 với cách vẽ vector thuần | Dùng vector thuần như kế hoạch |
| **Đạt có điều kiện** | Chỉ đạt sau khi áp một số mục của phương án B (mục 7) | Ghi các kỹ thuật đó thành bắt buộc trong runtime |
| **Không đạt** | Áp hết phương án B vẫn không đạt | Dừng lại bàn với người dùng: thu hẹp phạm vi, hoặc dùng runtime Spine/Rive cho nhân vật |

**Câu hỏi thêm (từ 2026-10-17): nhân vật nên vẽ bằng vector, bitmap hay mesh?** Engine không bắt buộc đầu vào là vector. Bộ phận nhân vật có thể là ảnh PNG tạo bằng AI (Gemini) hoặc SVG (Recraft), dùng chung một hệ xương và timeline. Cảnh P5 (mục 5) đo cùng một cảnh nhảy với ba loại lớp:

| Loại lớp | Vẽ thế nào | Chi phí chính cần đo |
| --- | --- | --- |
| `vector` | `Shape` / `SoftPath` hiện có | Dựng lại path, nét viền |
| `bitmap` | `BitmapPart`: một vùng ảnh gắn cứng vào xương | Bộ nhớ ảnh đã giải nén, thời gian giải nén |
| `mesh` | `MeshPart`: lưới tam giác có texture, đỉnh lưới theo xương | Như `bitmap`, cộng thêm tính đỉnh lưới |

Spec 2.0 ghi engine hỗ trợ cả ba loại. Loại dùng mặc định cho nhân vật được chọn theo bảng so sánh trong `REPORT.md` và kết quả chấm ảnh Kai (`assets/kai/PROMPTS.md`).

**Thời hạn:** 3 ngày làm việc cho P1–P4, cộng Ngày 4 cho P5 (mục 8). Cuối mỗi ngày dừng lại báo người dùng.

## 2. Không phải mục tiêu

- Không làm engine thật, không làm định dạng JSON chính thức, không làm Studio.
- Không cần đẹp từng chi tiết như demo; cần **độ phức tạp tương đương** (số hình, số nét viền, số điểm uốn, số hạt) để số đo có ý nghĩa.
- Không gian lận: không vẽ sẵn cả khung hình, không quay video rồi phát lại, không giảm độ phức tạp mà không ghi vào báo cáo.

Đường chạy nóng (code chạy mỗi khung) phải viết giống engine thật: dữ liệu hình tách khỏi code vẽ, không cấp phát bộ nhớ trong vòng vẽ. Nếu không, số đo vô nghĩa.

## 3. Thiết bị và cách build

- **Máy thật** Android tầm trung của người dùng. Không dùng emulator để lấy số. Ghi lại model, chip, RAM, phiên bản Android, tần số quét màn hình.
- **Tần số quét:** nếu máy 90/120 Hz, cho Activity xin 60 Hz khi đo (`preferredDisplayModeId` hoặc `preferredRefreshRate`) để đo đúng ngưỡng 16,7 ms. Ghi thêm một lần đo ở tần số mặc định để tham khảo.
- **Build để đo:** build type `benchmark` không debuggable, bật R8, ký bằng khóa debug. Không lấy số từ bản debug (Compose ở bản debug chậm hơn nhiều).
- **Baseline Profile:** đo bản không có profile trước; nếu còn thời gian, tạo Baseline Profile và đo lại.
- **Project:** tạo bằng KMP Wizard (Android + Desktop, giao diện dùng chung). Desktop chỉ để xem nhanh hình khi viết; mọi con số lấy trên Android. Nếu đọc được `../kmp-template-impl` thì dùng cùng phiên bản Kotlin, Compose Multiplatform, AGP với repo đó (chỉ đọc, không sửa repo đó).
- **Module:** `composeApp` (engine nhỏ + 4 cảnh + HUD đo), `benchmark` (Macrobenchmark, chỉ Android).

## 4. Engine thu nhỏ cần viết

Chỉ viết những phần ảnh hưởng tới hiệu năng, nhưng viết như engine thật.

| Phần | Cách làm | Ghi chú |
| --- | --- | --- |
| Nạp hình | Chuỗi path SVG (`d`) lấy từ các demo trong `reference/`, parse một lần khi nạp cảnh, giữ trong cache | Đo thời gian nạp |
| Cây xương | Mỗi xương có x, y, góc, scaleX, scaleY; mỗi khung tính ma trận thế giới vào mảng `FloatArray` dùng lại | Không tạo object mới mỗi khung |
| Bộ phận cứng | Gắn vào xương, vẽ bằng `withTransform` + `drawPath` | |
| Bộ phận uốn mềm | Lưu điểm điều khiển của path trong `FloatArray`, mỗi điểm chịu tối đa 4 xương có trọng số; mỗi khung tính điểm mới rồi dựng lại `Path` bằng `reset()` + `moveTo`/`cubicTo` | Đây là phần đáng ngờ nhất, phải đo riêng |
| IK 2 đoạn, lò xo | Như demo (`ik()`, tóc và đuôi theo sau) | |
| Xoay giả 3D | Chi tiết mặt theo `dx` và bán kính đầu, như demo Bobo và Leo/Lily | Rẻ, chỉ cần có |
| Lớp cảnh | 5–6 lớp parallax; gradient trời tạo `Brush` một lần; lớp tĩnh có thể ghi một lần vào `GraphicsLayer` (`rememberGraphicsLayer`, `record`, `drawLayer`) | Có công tắc bật/tắt cache để đo cả hai |
| Mặt nước | `clipPath` + vẽ lại phần dưới nước mờ 0,38 | Đo 2 cách: vẽ hai lần với alpha từng hình, và `saveLayer` |
| Hạt | Mảng `FloatArray` cho x, y, vx, vy, tuổi, màu; số hạt tối đa cố định; có seed | Đo 2 cách: `drawCircle` từng hạt, và vẽ một lệnh bằng `drawContext.canvas.drawRawPoints` với `FloatArray` (tránh `drawPoints(List<Offset>)` vì tạo list mỗi khung) |
| Lớp phủ phim | Ảnh nhiễu nhỏ lặp lại bằng `ImageShader` + tối viền bằng gradient tròn | Có công tắc bật/tắt |
| Máy quay | Biến đổi toàn cảnh (dịch, phóng to) mỗi khung | |
| Đồng hồ | Cảnh P2 lấy thời gian từ audio đang phát (vị trí phát của ExoPlayer hoặc MediaPlayer, nội suy giữa các lần đọc); cảnh khác dùng `withFrameNanos` | |
| Chạm và kéo | `pointerInput`; kiểm tra chạm trên ~30 vùng elip hoặc đa giác; kéo vật theo ngón tay | |
| Chữ | Lời karaoke bằng Compose `Text` đặt trên Canvas, đổi màu từng từ theo thời gian | |
| Atlas ảnh | PNG nạp qua Compose Resources (`Res.readBytes` + `decodeToImageBitmap`) một lần khi nạp cảnh; mỗi vùng ảnh có `src` (`IntOffset`/`IntSize`) và điểm neo | Thời gian giải nén tính vào thời gian nạp; ghi số byte đã giải nén (w×h×4) |
| `BitmapPart` | Gắn vào xương như `Part`: `save` + `Skeleton.concat` + `drawImageRect`; `Paint` tạo một lần, `filterQuality = Low`. Đổi miệng hoặc mắt bằng cách đổi vùng ảnh, không tạo object mới | Dùng cho P5 và P4 |
| `MeshPart` | Lưới đều N×M phủ vùng ảnh, bỏ ô trong suốt; mỗi đỉnh chịu tối đa 4 xương (cùng cách tính với `SoftPath`); trọng số tự tính theo khoảng cách tới xương. Vẽ bằng hàm `expect` `drawMeshBatch` (Android: `nativeCanvas.drawVertices` với `FloatArray`/`ShortArray` dùng lại, `BitmapShader` tạo một lần) | Dùng cho tay chân, tóc, áo của P5 |

Hình vẽ khung thiết kế 1920×1080, co giãn vừa màn hình như spec 1.1.

## 5. Các cảnh thử

Mỗi cảnh mở được trực tiếp bằng intent extra (`--es scene p1`) để benchmark gọi được.

### P1 `castle_ride` — từ sản phẩm D (cảnh nặng nhất)

Chuyển từ `reference/castle.html`:
- Trời hoàng hôn gradient, mặt trời, núi, lâu đài có 4 lá cờ bay (cờ là bộ phận uốn mềm), cửa sổ sáng, đường có phối cảnh.
- Ngựa phi dáng `gallop` theo bảng pha chân; bờm và đuôi uốn mềm theo xương.
- Hai người cưỡi (Leo và Lily theo `reference/chars.html`) gắn vào lưng ngựa, nhún theo nhịp vó, tóc và áo choàng theo sau bằng lò xo.
- Máy quay đi theo ngựa.
- Mỗi 1,5 giây chuyển dần sang đêm và bắn pháo hoa; tối đa 400 hạt cùng lúc.
- Lớp nhiễu + tối viền toàn màn hình.
- Chạy lặp 30 giây.

### P2 `song_dance` — từ sản phẩm A

- 5 nhân vật nhảy cùng lúc: 3 bạn nhỏ (thiết kế theo Leo, Lily trong `reference/chars.html` và Bobo trong `reference/bobo.html`), 1 chó, 1 vịt.
- Tay chân uốn mềm; động tác nhảy, vỗ tay, xoay người lặp theo phách.
- Đổi hình miệng mỗi 80–120 ms để giả lập khẩu hình.
- Nhạc thử: một track tiếng gõ phách 120 BPM dài 60 giây do Claude Code tự sinh bằng script (không dùng nhạc có bản quyền). Âm thanh là đồng hồ chuẩn.
- Lời karaoke đổi màu từng từ; confetti 200 hạt; máy quay phóng nhẹ ở phách mạnh; nền 5 lớp.
- **Đo độ trôi:** ở mỗi phách, ghi chênh lệch giữa thời điểm động tác chạm phách và vị trí audio.

### P3 `explore_lake` — từ sản phẩm C

Chuyển từ `reference/lake.html`:
- 6 lớp parallax, mặt nước có phản chiếu và gợn sóng, 20 cây sậy đung đưa, mây trôi.
- Cậu bé, vịt, ếch như demo; khoảng 30 vùng chạm.
- Kéo mũ đội lên đầu cậu bé: mũ gắn vào xương đầu và đi theo khi cậu bé nhảy.
- Chạm vật thì vật phản ứng.
- **Đo độ trễ chạm:** từ sự kiện chạm tới khung hình đầu tiên có phản ứng (đặt trace marker).
- Benchmark tự động thao tác bằng UiAutomator: chạm lần lượt, kéo mũ, lặp lại, để số đo lặp lại được.

### P4 `stress` — tìm giới hạn

Một cảnh nhận tham số qua intent extra: số nhân vật 1–12, số hạt 0–2000, uốn mềm bật/tắt, cache lớp tĩnh bật/tắt, lớp phủ bật/tắt, khử răng cưa bật/tắt. Mục đích: vẽ đường cong "bao nhiêu thì bắt đầu rớt khung".

Từ Ngày 4 thêm `--es art vector|bitmap|mesh` và `--es atlas 1|0.5` (tỉ lệ độ phân giải atlas). Trước khi có ảnh Kai, `bitmap` và `mesh` dùng ảnh giả có kích thước thật (khoảng 1024×1024 mỗi nhân vật). Ghi rõ trong báo cáo: số này chỉ để vẽ đường cong rớt khung, không dùng để đánh giá hình.

### P5 `bitmap_dance` — nhân vật bằng ảnh (Ngày 4, chờ ảnh Kai)

- Cùng nội dung với P2: 5 nhân vật nhảy, đổi hình miệng mỗi 80–120 ms, confetti 200 hạt, nền 5 lớp, lời karaoke, đồng hồ theo audio.
- Nhân vật là Kai và các bản đổi màu của Kai. Đây là một cách giảm độ phức tạp so với 5 nhân vật khác nhau, phải ghi vào báo cáo. Nó làm giảm số ảnh phải nạp, không làm giảm số lệnh vẽ.
- Thân, đầu, mắt, miệng là `BitmapPart`; tay chân, tóc, áo là `MeshPart`.
- Chạy 3 lần với `--es art bitmap`, `--es art mesh` (tay chân uốn mềm), và `--es art vector` nếu SVG của Recraft đạt tiêu chí chấm. Cả ba dùng cùng một rig.
- Ảnh đặt ở `shared/src/commonMain/composeResources/files/kai/`. Dữ liệu rig (vùng ảnh, điểm neo, xương) để trong `scenes/KaiArt.kt`, tách khỏi code vẽ.

Sản phẩm B (truyện) nhẹ hơn C nên không thử riêng; phần chờ thao tác và ghi âm thử ở giai đoạn sau.

## 6. Cách đo và ngưỡng đạt

**Công cụ**

1. **HUD trong app** (chỉ ở bản đo, có nút ẩn): FPS, thời gian khung, % khung rớt, số hạt, số hình vẽ mỗi khung. Dùng JankStats (`androidx.metrics:metrics-performance`).
2. **Macrobenchmark** với `FrameTimingMetric` (`frameDurationCpuMs`, `frameOverrunMs`): mỗi cảnh chạy 20 giây, 5 lần lặp. Báo P50, P90, P95, P99. `frameOverrunMs` cần Android 12 trở lên; máy cũ hơn thì dùng `frameDurationCpuMs` và `gfxinfo`, ghi rõ trong báo cáo.
3. **`adb shell dumpsys gfxinfo <package> framestats`** để đối chiếu.
4. **Perfetto trace** cho cảnh tệ nhất: tìm điểm nóng (tính uốn mềm, dựng lại path, vẽ nét viền, vẽ chồng nhiều lớp).
5. **Cấp phát bộ nhớ:** Memory Profiler hoặc allocation tracking; sau 2 giây khởi động, vòng vẽ không được cấp phát.
6. **Bộ nhớ:** `adb shell dumpsys meminfo <package>` trước và sau khi mở cảnh. Với P5 và P4 (`art` là `bitmap` hoặc `mesh`), ghi riêng dòng Graphics/GL, kích thước atlas và số byte ảnh đã giải nén. Nếu vượt 30 MB, đo lại với `--es atlas 0.5` và ghi cả hai số.

**Độ phức tạp phải ghi kèm mỗi cảnh:** số path vẽ mỗi khung, số nét viền, số điểm được uốn mềm mỗi khung, số hạt tối đa, số lớp. Không có số này thì kết quả không so sánh được.

**Ngưỡng đạt** (mỗi cảnh, bản build đo, máy thật, 60 Hz):

| Chỉ số | Ngưỡng |
| --- | --- |
| `frameOverrunMs` | P95 ≤ 0 ms (ít nhất 95% khung kịp hạn); P99 ≤ 8 ms |
| `frameDurationCpuMs` | P50 ≤ 8 ms; P90 ≤ 12 ms |
| Khung tệ nhất sau 2 giây đầu | ≤ 50 ms |
| Thời gian nạp cảnh (gồm parse path) | < 300 ms |
| Bộ nhớ tăng thêm mỗi cảnh | < 30 MB |
| Cấp phát trong vòng vẽ | 0 sau 2 giây đầu |
| P2: độ trôi so với audio sau 60 giây | ≤ 17 ms (1 khung) |
| P3: độ trễ từ chạm tới phản ứng | < 100 ms |

## 7. Phương án B (chỉ thử khi chưa đạt)

Thử theo thứ tự từ rẻ tới đắt. Mỗi mục phải có số trước và sau.

1. **Cache lớp tĩnh** vào `GraphicsLayer`; chỉ vẽ lại thứ chuyển động.
2. **Nét viền rẻ hơn:** vẽ viền như một hình fill nới rộng phía sau, thay cho stroke; hoặc tắt khử răng cưa cho hình lớn ít cạnh xiên.
3. **Bớt điểm uốn mềm:** chỉ uốn tay chân, đuôi, bờm, áo choàng, cờ; giảm số điểm theo kích thước trên màn hình.
4. **Ảnh hoá bộ phận cứng:** vẽ mỗi bộ phận một lần ra `ImageBitmap` theo mật độ màn hình, mỗi khung chỉ `drawImage` có biến đổi (giống cách Spine làm). Bộ phận mềm vẽ bằng lưới tam giác có texture: `Vertices` + `ImageShader` của Compose, hoặc trên Android gọi thẳng `nativeCanvas.drawVertices` với `FloatArray` để không cấp phát mỗi khung. Dữ liệu rig giữ nguyên.
   *Từ 2026-10-17:* mục này được đo trực tiếp ở P5 (Ngày 4), không còn chỉ là phương án dự phòng. Cảnh P1–P3 vẫn đo bằng vector thuần trước.
5. **Hạt rẻ hơn:** vẽ một lệnh bằng `drawRawPoints`, hoặc atlas ảnh nhỏ.
6. **Mức chất lượng:** tắt lớp nhiễu, bớt hạt trên máy yếu (cấu hình chất lượng thấp, trung bình, cao).

## 8. Lịch 3 ngày

| Ngày | Việc | Dừng lại báo người dùng |
| --- | --- | --- |
| 1 | Tạo project, HUD, module benchmark, build `benchmark`; engine thu nhỏ (cache path, xương, bộ phận cứng, uốn mềm, IK, lò xo); P3 `explore_lake`; số đo đầu tiên trên máy | Số đo P3 + video quay màn hình |
| 2 | P1 `castle_ride` và P2 `song_dance` (gồm track phách và đo độ trôi); chạy Macrobenchmark cả 3 cảnh | Bảng số P1–P3 |
| 3 | P4 `stress`; Perfetto cho cảnh tệ nhất; phương án B nếu cần; viết `REPORT.md` theo `REPORT_TEMPLATE.md` | Báo cáo cuối + kết luận Đạt / Đạt có điều kiện / Không đạt |
| 4 | Atlas, `BitmapPart`, `MeshPart`, `drawMeshBatch`; P4 thêm `art`/`atlas`; chấm ảnh Kai; P5 `bitmap_dance` khi đã có ảnh; Macrobenchmark P5 với từng loại lớp | Bảng so sánh vector / bitmap / mesh + kết quả chấm ảnh Kai |

## 9. Bàn giao

- `REPORT.md` điền đủ theo `REPORT_TEMPLATE.md`.
- Video quay màn hình mỗi cảnh (`adb shell screenrecord`), kèm ảnh chụp đặt cạnh ảnh demo HTML để thấy độ phức tạp tương đương.
- File Perfetto trace của cảnh tệ nhất.
- Code của repo thử. Code này là để thử; phần hữu ích (cache path, uốn mềm, hạt) có thể chép sang engine thật sau.
