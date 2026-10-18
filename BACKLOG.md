# BACKLOG — việc để làm sau (ngoài kế hoạch 3 ngày của SPIKE.md)

| # | Việc | Ghi chú | Thêm ngày |
| --- | --- | --- | --- |
| 1 | Thêm target iOS (iPhone + iPad) vào project, có project Xcode | Engine và cảnh đã ở `commonMain`. Chỉ cần `actual` cho `Platform.kt`: `uptimeMillis`, trace (os_signpost), `FrameLoop` (CADisplayLink), `concatAffine`, `drawPointsBatch`, `exposeTestTags` | 2026-10-17 |
| 2 | Vòng đo hiệu năng trên iOS: một iPhone tầm trung + một iPad đời thấp (vd iPad 9th gen) | Compose trên iOS vẽ bằng Skia/Metal, số Android không dùng thay được. iPad vẽ nhiều điểm ảnh hơn hẳn (iPad Pro ~5,6 MP so với điện thoại ~2,6 MP) | 2026-10-17 |
| 3 | Studio: hiện khung vùng an toàn / phần tràn, xem trước nhiều máy (iPhone SE, iPhone, Android 20:9, iPad 4:3) | Báo lỗi khi vật chạm được nằm ngoài vùng an toàn, hoặc lớp nền không phủ hết phần tràn | 2026-10-17 |
| 4 | Màn ngoài khoảng 4:3–21:9 (máy gập mở ra ~1,15–1,25) | Hiện đang tô viền bằng màu nền. Cân nhắc phần tràn dọc lớn hơn cho các cảnh cần hỗ trợ máy gập | 2026-10-17 |
| 5 | Điểm neo (`anchor`) cho lớp hoặc vật trang trí: neo vào góc/mép của vùng nhìn thấy thay vì vị trí cố định trong cảnh | Chỉ dùng cho tiền cảnh trang trí (bụi lá làm khung góc màn hình...). Nhân vật và vật tương tác không neo. Đưa vào spec 2.0 và Studio | 2026-10-17 |
| 6 | UI theo nhóm kích thước màn hình (`WindowSizeClass` Android / size class iOS) | Nút, chữ giữ cỡ theo dp/pt; iPad bố trí rộng hơn (vd phụ đề có dòng dịch, 4 thẻ đáp án một hàng thay vì 2) | 2026-10-17 |
| 7 | Bộ kit nhân vật: tờ "character constructor" 4K mỗi nhân vật (5 góc, bộ mặt trực diện + nghiêng, bàn tay hoán đổi) | Spec và tiến độ ở `ART_PIPELINE.md` mục 7 (bước 0–5). Gemini chỉ gọi khi người dùng duyệt từng đợt | 2026-10-18 |
| 8 | Tool: `splitsheet` (tách tờ, đưa về cùng tỉ lệ) và `sheetcheck` (tự kiểm tờ, báo ĐẠT/LỖI kèm ảnh) | `ART_PIPELINE.md` mục 3 | 2026-10-18 |
| 9 | Cắt góc nghiêng và 3/4 sau; engine thêm góc 90° và 135°, bàn tay hoán đổi, clip đi ở góc nghiêng | `ART_PIPELINE.md` mục 4, 5 | 2026-10-18 |
| 10 | Bộ kit nhân vật chuẩn: 2 tờ Gemini 4K mỗi nhân vật (thân: 5 góc + bộ phận rời có vẽ phần khuất + tóc tách lớp; mặt và tay: mắt tách tròng, 12 miệng, bàn tay đếm số), tạo lại cho cả 5 nhân vật (~$1,51, tối đa ~$3), làm trọn Kai trước rồi mới sang nhân vật khác | `ART_PIPELINE.md` mục 2, 7. Thay thế cho dòng 7. Mỗi đợt gọi Gemini người dùng duyệt riêng | 2026-10-18 |
| 11 | `cutparts`: lắp rig từ bộ phận rời (ball joint ở vai và hông, bắp tay sau thân); engine đọc tròng mắt tách, 12 miệng, bàn tay hoán đổi, tóc tách lớp | `ART_PIPELINE.md` mục 4. Cắt hình phẳng thành đường dự phòng | 2026-10-18 |
