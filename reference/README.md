# Demo tham chiếu

Các demo HTML/SVG đã được người dùng duyệt. Mở trực tiếp bằng trình duyệt để xem. Dùng để chép chuỗi path, màu, kích thước, và cách tính chuyển động sang Compose. Mục tiêu là độ phức tạp tương đương, không cần giống từng điểm ảnh.

| File | Dùng cho cảnh | Lấy gì |
| --- | --- | --- |
| `castle.html` | P1 `castle_ride` | Trời, lâu đài, cờ bay, đường phối cảnh `persp(y)`, ngựa 3 dáng chạy (bảng pha chân), bờm và đuôi `ribbon()`, Leo và Lily cưỡi ngựa, chế độ đêm, pháo hoa `burst()`, lớp nhiễu và tối viền |
| `lake.html` | P3 `explore_lake` | 6 lớp parallax, mặt nước cắt và vẽ mờ phần dưới nước, gợn sóng, cây sậy, cậu bé đi/ngồi/vẫy, vịt bơi và chúi đầu, ếch nhảy |
| `chars.html` | P1, P2 | Thiết kế Leo và Lily: viền màu, mắt to có điểm sáng, biểu cảm, vẫy tay cạnh đầu |
| `bobo.html` | P2 `song_dance` | Nhân vật Bobo: xoay giả 3D (`FEAT`, `yaw`), vỗ tay bàn tay dựng đứng, xoay vòng có lấy đà, tween có giữ giá trị và easing từng key |

Các hàm đáng chuyển nguyên logic: `ik()` (IK 2 đoạn, chọn hướng khuỷu), `hose()` (tay chân cong), `ribbon()` (dải mềm cho bờm, đuôi, tóc), công thức xoay giả 3D, chu kỳ bước theo quãng đường.
