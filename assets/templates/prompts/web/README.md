# Tạo bộ kit nhân vật trên Gemini web

Dùng gemini.google.com, miễn phí trong hạn mức mỗi ngày. API (`tools/gemini_gen.py`) thì lượt nào cũng tính tiền, vì model tạo ảnh không có free tier qua API.

Bộ kit gồm 2 tờ:

| Tờ | Tờ mẫu (ảnh 3) | Prompt chính | Tin nhắn sửa |
|---|---|---|---|
| Thân | `assets/templates/rig_body.png` | `body_sheet.txt` | `body_fixes.txt` |
| Mặt và tay | `assets/templates/rig_face_hands.png` | `face_hands_sheet.txt` | `face_hands_fixes.txt` |

## Chuẩn bị cho một nhân vật

1. Viết `assets/<char>/prompts/notes.txt`: một đoạn tiếng Anh tả quần áo, mọi chi tiết, tóc, phụ kiện, số ngón tay. Xem mẫu ở `assets/kai/prompts/notes.txt`.
2. Điền notes vào chỗ `<<CHARACTER NOTES>>` của 2 prompt chính:
   ```
   for s in body_sheet:web_body face_hands_sheet:web_face_hands; do python3 -I -c "import sys;t=open(sys.argv[1]).read();n=open(sys.argv[2]).read().strip();open(sys.argv[3],'w').write(t.replace('<<CHARACTER NOTES>>',n))" assets/templates/prompts/web/${s%%:*}.txt assets/<char>/prompts/notes.txt assets/<char>/prompts/${s##*:}.txt; done
   ```
   Kết quả là `assets/<char>/prompts/web_body.txt` và `web_face_hands.txt`, dán được ngay.

## Cách tạo một tờ

1. Mở **một cuộc trò chuyện mới** cho mỗi tờ, để Gemini không lẫn tờ này với tờ kia.
2. Chọn chế độ tạo ảnh. Nếu có model Pro ("Nano Banana Pro" hoặc chế độ Thinking), chọn nó, vì nó giữ bố cục tốt hơn.
3. Đính kèm 3 ảnh, **đúng thứ tự này**:
   1. `assets/<char>/gemini/ref.png` (góc 3/4 đã duyệt)
   2. `assets/<char>/gemini/view_front.png` (trực diện đã duyệt)
   3. tờ mẫu của tờ đó (xem bảng trên)
4. Dán toàn bộ prompt đã điền (`web_body.txt` hoặc `web_face_hands.txt`).
5. Tải hình **kích thước lớn nhất** (nút "Download full size" nếu có), lưu thành `assets/<char>/gemini/web/<body|face>_<n>.png`, với n = 1, 2, 3… theo từng lần. Lưu cả hình chưa đạt.
6. Gửi Claude soi. Claude đo kích thước, kiểm từng ô, đo màu, rồi chọn tin nhắn sửa trong file fixes, điền vị trí sẵn.
7. Dán tin nhắn sửa **vào cùng cuộc trò chuyện**. Mỗi lần chỉ sửa 1–2 chỗ: sửa ít chỗ thì Gemini giữ nguyên phần đã đúng; nêu nhiều chỗ một lúc thì nó chỉ làm được vài chỗ.
8. Lặp bước 5–7 tới khi đạt.

## Gọi vị trí một mảnh

- **Tờ thân:**
  - "top row, view 1…5";
  - "middle row, left group / right group, first…fifth piece" (left group trực diện, right group nhìn nghiêng);
  - "bottom row, first…sixth piece" (3/4).
- **Tờ mặt và tay:** "row 1…6, piece n".

## Nếu hình tải về chỉ khoảng 1K (cạnh dài khoảng 1400 px)

Sửa cho đạt hết ở 1K trên web trước, rồi chọn một trong hai cách:
- Gọi API 4K đúng 1 lượt ($0,151), đính kèm bản đạt và yêu cầu vẽ lại y hệt. Prompt mẫu: `assets/kai/prompts/5c_body_sheet_4k.txt`.
- Phóng to trên máy.

Cách nào cũng phải được người dùng duyệt trước.

## Vì sao prompt viết như vậy (bài học từ 5 lượt gọi tờ thân Kai, 2026-10-18 và 19)

- **Mảnh hông gọi là "hip band":** không bao giờ dùng chữ "shorts". Cứ thấy chữ đó là Gemini vẽ cả chiếc quần có 2 ống (4/4 lần).
- **Mỗi mảnh chân rời ghi riêng "NO shoe":** chỉ nói một câu chung thì chân nhìn nghiêng và chân 3/4 vẫn dính giày.
- **Tay chân phía xa ghi rõ cùng màu tay chân gần:** khi tờ mẫu tô xám đậm, Gemini vẽ tay xa sẫm như bị bệnh.
- **Tờ mẫu không vẽ đĩa sáng ở vai:** Gemini biến đĩa đó thành mảng da.
- **Vẽ lại cả tờ thì kết quả ngẫu nhiên:** sửa prompt rồi vẽ lại, chỗ này đúng thì chỗ khác hỏng. Sửa ảnh trong cùng cuộc trò chuyện thì giữ được phần đã đúng.
- **Phần hình học Gemini vẫn không làm đúng thì để tool cắt làm:** cắt theo đường thẳng, tách giày, kéo dài tất màu trơn.

## Cảnh và đạo cụ

Chưa có tờ mẫu. Khi cần thì làm theo cùng khuôn:
- tờ mẫu hình nộm;
- prompt chính có chỗ `<<...>>`;
- file tin nhắn sửa;
- notes cho từng cảnh.
