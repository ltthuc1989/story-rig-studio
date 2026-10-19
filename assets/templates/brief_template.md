# Brief nhân vật: <tên>

Điền trước khi tạo hình. Phần cuối là đoạn tiếng Anh sẽ dán vào chỗ `<<BRIEF>>` của prompt (`assets/<char>/brief.txt`, chỉ đoạn đó, một dòng).

## Nhân vật
- Tên, tuổi, giới, dáng người (trẻ con / người lớn; cao gầy / tròn).
- Da, tóc (kiểu, màu), mắt (màu), biểu cảm mặc định.
- Áo: kiểu, màu, chi tiết (hình in, cổ áo).
- Dưới: quần ngắn / quần dài / váy, màu, chi tiết.
- Tất, giày.
- Phụ kiện (balo, mũ, kính...).
- Bàn tay: không ép số ngón (Gemini vẽ tự nhiên; ép 4 ngón từng ra tay vụng). Chỉ ghi nếu nhân vật có tay đặc biệt (găng, vuốt).

## Kiểm thiết kế dễ rig (mỗi dòng phải là "có", hoặc ghi cách xử lý)
- [ ] Tay áo ngắn, kết thúc trên khuỷu (khuỷu gập không kéo tay áo).
- [ ] Ống quần kết thúc trên gối, hoặc quần dài trơn (gối gập không kéo gấu).
- [ ] Tóc không chạm vai (tóc dài: ghi "tóc là mảnh lưới riêng, không dính thân").
- [ ] Không hình in, sọc, hoa văn vắt qua vai, khuỷu, hông, gối.
- [ ] Không gì vắt chéo qua thân (khăn, dây đeo chéo). Dây balo nằm trên thân thì được.
- [ ] Không cầm gì trong tay.
- [ ] Váy (nếu có): ghi "váy là mảnh lưới riêng, chân rời bắt đầu dưới gấu váy".
- [ ] Mũ, kính (nếu có): ghi "là mảnh riêng trên đầu".

## Đoạn tiếng Anh (chép vào assets/<char>/brief.txt)
Một đoạn, tả theo thứ tự: tuổi và dáng; da, tóc, mắt; áo; dưới; tất và giày; phụ kiện.
Ví dụ: `Kai, a 7-year-old boy with light tan skin, short messy dark hair with a cowlick and a side-swept fringe, big brown eyes; a red T-shirt with short sleeves rolled into a darker red cuff and a small white star on the chest; a small mustard-yellow backpack whose two straps show over the shoulders; blue denim shorts with turned-up cuffs ending above the knee; white socks with one red stripe; orange and white velcro sneakers`
