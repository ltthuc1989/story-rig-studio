# 4 sản phẩm mẫu — thước đo của engine StoryRig

StoryRig là engine hoạt hình 2D dùng chung cho nhiều app: nội dung trông như phim hoạt hình nhưng chạy trực tiếp trong app và bé tương tác được bất kỳ lúc nào. Để "dùng chung" không biến thành làm tính năng không ai dùng, engine được đo bằng 4 sản phẩm mẫu dưới đây.

**Quy tắc:** mỗi tính năng của engine phải phục vụ ít nhất một sản phẩm mẫu. Kiến trúc để mở (xương tự do, keyframe cho mọi thuộc tính, gói mở rộng), nhưng chỉ viết code cho thứ mà 4 sản phẩm cần. Thêm sản phẩm mẫu mới khi có app mới cần.

Mọi lời thoại, bài hát, nhân vật trong sản phẩm mẫu là nội dung tự viết, không dùng lời hay nhân vật của kênh hoặc hãng khác.

---

## A. Bài hát hoạt hình (kiểu English Singsing)

Một bài hát ngắn khoảng 60 giây, ví dụ lời tự viết "Hello, hello, how are you?". Ba bạn nhỏ và hai con vật hát và nhảy theo nhịp.

| Mục | Nội dung |
| --- | --- |
| Cảnh | 3 shot: toàn cảnh sân trường, cận mặt nhân vật đang hát, toàn cảnh nhảy cuối bài |
| Nhân vật | 3 bạn nhỏ (`humanoid_chibi`), 1 chó (`quadruped`), 1 vịt (`bird`) |
| Chuyển động | Nhảy theo nhịp, vỗ tay đúng phách, xoay người, vẫy tay; khẩu hình theo giọng hát |
| Máy quay | Cắt cảnh, zoom nhẹ theo phách mạnh, lia theo nhân vật |
| Âm thanh | Nhạc nền + giọng hát; âm thanh là đồng hồ chuẩn; mốc phách (beat) để động tác khớp nhịp |
| Chữ | Lời bài hát chạy kiểu karaoke, tô sáng từng từ |
| Hiệu ứng | Hoa giấy (confetti), lấp lánh khi kết bài |
| Tương tác | Bé chạm vào nhân vật thì nhân vật nhảy hoặc làm mặt vui; bài hát không dừng |
| Điểm nặng về hiệu năng | 5 nhân vật cùng lúc có tay chân uốn mềm, hạt confetti, nền 5 lớp, khớp nhịp không trôi |
| Đạt khi | 60 khung/giây trên Android tầm trung; động tác lệch nhịp không quá 1 khung (17 ms) sau 60 giây; miệng khớp lời nhìn bằng mắt |

## B. Truyện tương tác (kiểu Duolingo Stories)

Truyện "Lily cho vịt ăn" dài 2–3 phút ở cảnh hồ (`samples/st02_duck_pond` trong spec 1.1).

| Mục | Nội dung |
| --- | --- |
| Cảnh | 1 cảnh hồ, máy quay đổi khung giữa các đoạn hội thoại |
| Nhân vật | Lily, Ben, vịt |
| Chuyển động | Đi tới, chỉ tay, vẫy, ngồi, cử chỉ khi nói, vịt bơi và ăn |
| Âm thanh | Giọng TTS từng câu, khẩu hình theo viseme |
| Chữ | Phụ đề tô sáng từ, chạm từ xem nghĩa, phụ đề dịch |
| Tương tác | Chọn đáp án, chọn câu trả lời, nghe và nhắc lại, đóng vai nói vào mic, kéo bánh mì cho vịt, chạm nhân vật trong lúc xem |
| Điểm nặng về hiệu năng | Nhẹ hơn A và C; chủ yếu kiểm tra luồng chờ thao tác, ghi âm và chấm điểm không làm khựng hình |
| Đạt khi | Chạy hết truyện không khựng; từ lúc bé chạm tới lúc nhân vật phản ứng dưới 100 ms |

## C. Cảnh khám phá và tìm đồ (kiểu Khan Academy Kids)

Cảnh hồ rộng có khoảng 30 vật tương tác (`samples/explore_lake`, `samples/find_lake` trong spec 1.1).

| Mục | Nội dung |
| --- | --- |
| Cảnh | Hồ, núi, cây, bụi cây, hộp; 6 lớp parallax; mặt nước có phản chiếu và gợn sóng |
| Nhân vật | Ben, vịt, ếch, cún |
| Chuyển động | Mỗi vật có phản ứng riêng khi chạm; 20 cây sậy đung đưa; mây trôi |
| Tương tác | Chạm vật đọc tên; đổi trạng thái (hoa nở, hộp mở); kéo mũ đội lên đầu Ben (mũ đi theo khi Ben nhảy); kéo bánh mì cho vịt; nút lệnh cho Ben nhảy, vẫy, ngồi; tìm đồ theo tên, theo mô tả, đồ bị che |
| Điểm nặng về hiệu năng | Nhiều vật vẽ cùng lúc, vẽ phần dưới mặt nước hai lần, kiểm tra chạm trên 30 vùng, kéo thả mượt |
| Đạt khi | 60 khung/giây khi đang kéo vật; phản ứng chạm bắt đầu dưới 100 ms |

## D. Phim ngắn không liên quan học tiếng Anh: "Về lâu đài"

Phim ngắn khoảng 45 giây, phát triển từ demo "Về Lâu Đài". Sản phẩm này để chứng minh engine dùng được cho app khác.

| Mục | Nội dung |
| --- | --- |
| Cảnh | Hoàng hôn: đường uốn vào lâu đài, cờ bay, cửa sổ sáng; chuyển dần sang đêm |
| Nhân vật | Ngựa (`quadruped`) phi 3 dáng `walk`, `trot`, `gallop`; hoàng tử và công chúa cưỡi trên lưng (gắn vào xương ngựa), tóc và áo choàng bay theo |
| Chuyển động | Ngựa đổi dáng chạy, người cưỡi nhún theo nhịp vó, tới cổng thì dừng, công chúa vẫy tay; uốn mềm bờm, đuôi, áo choàng |
| Máy quay | Theo ngựa dọc đường có phối cảnh, zoom vào lâu đài cuối phim |
| Hiệu ứng | Pháo hoa khi tới nơi (vài trăm hạt), sao lấp lánh, ánh sáng chuyển ngày sang đêm, hạt nhiễu và tối viền để ra chất phim |
| Tương tác | Bé chạm lên trời thì bắn thêm pháo hoa; chạm ngựa thì ngựa hí và dựng chân |
| Điểm nặng về hiệu năng | Cảnh nặng nhất: nhiều nét vẽ, gradient, cờ uốn mỗi khung, bờm và đuôi uốn mềm, hạt pháo hoa, lớp nhiễu toàn màn hình |
| Đạt khi | 60 khung/giây trên Android tầm trung kể cả lúc pháo hoa nhiều nhất |

---

## Bảng khả năng engine × sản phẩm mẫu

Dấu ● là bắt buộc cho sản phẩm đó; ○ là có dùng nhưng không phải trọng tâm.

| Khả năng engine | A | B | C | D |
| --- | --- | --- | --- | --- |
| Xương tự do + mẫu rig (người, 4 chân, chim) | ● | ● | ● | ● |
| Hình uốn mềm theo xương (tay chân, đuôi, bờm, áo choàng) | ● | ○ | ○ | ● |
| Xoay giả 3D, hình theo góc nhìn | ● | ○ | ○ | ○ |
| Biểu cảm, morph hình dạng | ● | ● | ○ | ○ |
| Đổi trang phục, gắn đồ vào nhân vật | | | ● | ○ |
| Nhân vật gắn vào nhân vật (cưỡi, nắm tay) | | | | ● |
| Keyframe mọi thuộc tính, trộn lớp động tác | ● | ● | ● | ● |
| Chu kỳ tự sinh (đi, chạy, dáng ngựa), lò xo | ○ | ● | ● | ● |
| Nhiều shot, chuyển cảnh, máy quay | ● | ○ | | ● |
| Âm thanh là đồng hồ chuẩn, mốc phách | ● | ○ | | ○ |
| Khẩu hình: TTS viseme | | ● | ○ | |
| Khẩu hình: giọng thu sẵn hoặc giọng hát | ● | | | |
| Chữ karaoke, phụ đề, chạm từ | ● | ● | | |
| Hiệu ứng hạt | ● | | ○ | ● |
| Ánh sáng ngày, hoàng hôn, đêm; lớp phủ toàn màn hình | | ○ | ○ | ● |
| Mặt nước: cắt, phản chiếu, gợn sóng | | ● | ● | |
| State machine: chạm, kéo thả, trạng thái vật | ○ | ● | ● | ○ |
| Gói học tiếng Anh: bước truyện, tìm đồ, chấm phát âm, bản dịch | | ● | ● | |
| `CharacterView`: linh vật ở màn hình bất kỳ của app | ○ | ○ | ○ | |

## Thứ tự làm sau khi bản thử hiệu năng đạt

Mỗi sản phẩm mẫu là một cổng duyệt, chạy được trên Android:

1. Nền móng + nhân vật đi ra hồ, vẫy tay (phần đầu của C).
2. A: bài hát.
3. B và C: truyện, khám phá, tìm đồ (gói học tiếng Anh).
4. D: phim ngắn lâu đài.

Kế hoạch tuần chi tiết sẽ viết trong spec 2.0, sau khi có kết quả thử hiệu năng.
