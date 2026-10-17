# REPORT.md — Kết quả thử hiệu năng

## 1. Thiết bị và build

| Mục | Giá trị |
| --- | --- |
| Máy | (model) |
| Chip, RAM | |
| Android | |
| Tần số quét mặc định / khi đo | |
| Kotlin, Compose Multiplatform, AGP | |
| Build type, R8, Baseline Profile | |

## 2. Độ phức tạp mỗi cảnh

| Cảnh | Path vẽ / khung | Nét viền / khung | Điểm uốn mềm / khung | Hạt tối đa | Số lớp | Ghi chú so với demo HTML |
| --- | --- | --- | --- | --- | --- | --- |
| P1 `castle_ride` | | | | | | |
| P2 `song_dance` | | | | | | |
| P3 `explore_lake` | | | | | | |

## 3. Kết quả chính (vector thuần, chưa áp phương án B)

| Cảnh | CPU P50 | CPU P90 | Overrun P95 | Overrun P99 | Khung tệ nhất | Nạp cảnh | Bộ nhớ thêm | Cấp phát trong vòng vẽ | Đạt? |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P1 | | | | | | | | | |
| P2 | | | | | | | | | |
| P3 | | | | | | | | | |

Riêng P2: độ trôi so với audio sau 60 giây: ___ ms. Riêng P3: độ trễ chạm tới phản ứng: ___ ms.

## 4. Giới hạn (P4 `stress`)

| Tham số thay đổi | Bắt đầu rớt khung ở mức | Ghi chú |
| --- | --- | --- |
| Số nhân vật (uốn mềm bật) | | |
| Số nhân vật (uốn mềm tắt) | | |
| Số hạt (`drawCircle`) | | |
| Số hạt (`drawPoints`) | | |
| Lớp phủ nhiễu bật / tắt | | |
| Cache lớp tĩnh bật / tắt | | |

## 5. Điểm nóng (Perfetto, cảnh tệ nhất)

| Hạng mục | % thời gian khung | Nhận xét |
| --- | --- | --- |
| Tính xương + uốn mềm | | |
| Dựng lại Path | | |
| Vẽ fill | | |
| Vẽ nét viền | | |
| Hạt | | |
| Lớp phủ, saveLayer | | |
| Khác | | |

## 6. Phương án B đã thử

| Kỹ thuật | Cảnh | Trước (CPU P90 / Overrun P95) | Sau | Ảnh hưởng tới hình | Giữ lại? |
| --- | --- | --- | --- | --- | --- |
| | | | | | |

## 6b. Vector, bitmap hay mesh (P5 `bitmap_dance`, P4 với `art`)

Ảnh dùng: Kai thật / ảnh giả (ghi rõ). Atlas: ___ × ___ px, ___ MB đã giải nén.

| Loại lớp | Cảnh | CPU P50 | CPU P90 | Overrun P95 | Overrun P99 | Nạp cảnh (gồm giải nén) | Bộ nhớ thêm (Graphics/GL) | Cấp phát trong vòng vẽ | Đạt? |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `vector` | P5 | | | | | | | | |
| `bitmap` | P5 | | | | | | | | |
| `mesh` | P5 | | | | | | | | |
| `mesh`, atlas 0.5 | P5 | | | | | | | | |

| Loại lớp | Bắt đầu rớt khung ở số nhân vật (P4) | Ghi chú |
| --- | --- | --- |
| `vector` | | |
| `bitmap` | | |
| `mesh` | | |

**Chấm ảnh Kai** (thang 1–5, theo `assets/kai/PROMPTS.md`):

| Tiêu chí | Gemini (PNG) | Recraft (SVG) |
| --- | --- | --- |
| Đúng style đã duyệt (không viền, đúng bảng màu, bóng một mảng phẳng) | | |
| Đồng nhất tỉ lệ, màu, khối bóng, hướng sáng giữa các bộ phận | | |
| Có phần chồng ở khớp (xoay tay không hở) | | |
| Tạo thêm bộ biểu cảm hoặc trang phục vẫn khớp | | |
| Số path (chỉ SVG) | — | |
| Số phút chỉnh tay để rig được | | |

Ảnh chụp P5 đặt cạnh ảnh gốc: (đường dẫn)

## 7. Kết luận

**Đạt / Đạt có điều kiện / Không đạt:** ___

Lý do (dẫn số liệu ở trên):

## 8. Đề xuất cho spec 2.0

- Loại lớp mặc định cho nhân vật (vector / bitmap / mesh) và lý do:
- Kỹ thuật bắt buộc trong runtime:
- Giới hạn nội dung nên ghi vào spec (số nhân vật, số hạt, số điểm uốn mỗi cảnh):
- Rủi ro còn lại:
