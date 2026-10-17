# BACKLOG — việc để làm sau (ngoài kế hoạch 3 ngày của SPIKE.md)

| # | Việc | Ghi chú | Thêm ngày |
| --- | --- | --- | --- |
| 1 | Thêm target iOS (iPhone + iPad) vào project, có project Xcode | Engine và cảnh đã ở `commonMain`. Chỉ cần `actual` cho `Platform.kt`: `uptimeMillis`, trace (os_signpost), `FrameLoop` (CADisplayLink), `concatAffine`, `drawPointsBatch`, `exposeTestTags` | 2026-10-17 |
| 2 | Vòng đo hiệu năng trên iOS: một iPhone tầm trung + một iPad đời thấp (vd iPad 9th gen) | Compose trên iOS vẽ bằng Skia/Metal, số Android không dùng thay được. iPad vẽ nhiều điểm ảnh hơn hẳn (iPad Pro ~5,6 MP so với điện thoại ~2,6 MP) | 2026-10-17 |
| 3 | Studio: hiện khung vùng an toàn / phần tràn, xem trước nhiều máy (iPhone SE, iPhone, Android 20:9, iPad 4:3) | Báo lỗi khi vật chạm được nằm ngoài vùng an toàn, hoặc lớp nền không phủ hết phần tràn | 2026-10-17 |
| 4 | Màn ngoài khoảng 4:3–21:9 (máy gập mở ra ~1,15–1,25) | Hiện đang tô viền bằng màu nền. Cân nhắc phần tràn dọc lớn hơn cho các cảnh cần hỗ trợ máy gập | 2026-10-17 |
| 5 | Điểm neo (`anchor`) cho lớp hoặc vật trang trí: neo vào góc/mép của vùng nhìn thấy thay vì vị trí cố định trong cảnh | Chỉ dùng cho tiền cảnh trang trí (bụi lá làm khung góc màn hình...). Nhân vật và vật tương tác không neo. Đưa vào spec 2.0 và Studio | 2026-10-17 |
| 6 | UI theo nhóm kích thước màn hình (`WindowSizeClass` Android / size class iOS) | Nút, chữ giữ cỡ theo dp/pt; iPad bố trí rộng hơn (vd phụ đề có dòng dịch, 4 thẻ đáp án một hàng thay vì 2) | 2026-10-17 |
