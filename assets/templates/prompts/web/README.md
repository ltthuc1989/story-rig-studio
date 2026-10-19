# Tạo nhân vật trên Gemini web (flow tối giản)

Mỗi nhân vật cần **3 hình**, tạo miễn phí trên gemini.google.com, rồi code cắt và kiểm. Xem ART_PIPELINE.md mục 2–3.

| Bước | Hình | Cuộc trò chuyện | Đính kèm (đúng thứ tự) | Prompt | Lưu về | Kiểm |
|---|---|---|---|---|---|---|
| 1 | trực diện, 1:1 | mới | `assets/templates/rig_front.png` | `views.txt` tin 1 | `assets/<char>/design/view_front.png` | `viewcheck <char> front` |
| 2 | 3/4, 1:1 | **cùng** bước 1 | `assets/templates/rig_3q.png` | `views.txt` tin 2 | `design/view_3q.png` | `viewcheck <char> 3q` |
| 3 | mặt và tay, 16:9 | mới | (1) `design/view_front.png` đã đạt, (2) `assets/templates/rig_face.png` | `face.txt` | `design/face.png` | `facecheck <char>` |

`tools/new_character.sh <char> <bước>` in sẵn mọi thứ để chép và dán, và chạy lệnh kiểm:
```
tools/new_character.sh kai front        # in prompt tin 1 đã điền brief, file cần đính kèm, nơi lưu
tools/new_character.sh kai check-front  # sau khi lưu hình
tools/new_character.sh kai 3q / check-3q / face / check-face / cut
```

## Trước khi tạo
1. Viết `assets/<char>/brief.txt`: một đoạn tiếng Anh tả nhân vật, theo `assets/templates/brief_template.md`. Rà mục "thiết kế dễ rig" trong đó trước: tay áo ngắn, ống quần trên gối, tóc không chạm vai, không hoa văn vắt qua khớp.
2. Chọn model Pro ("Nano Banana Pro" hoặc Thinking) nếu có: giữ bố cục tốt hơn.

## Khi hình chưa đạt
- Lệnh kiểm in từng mục ĐẠT hoặc LỖI kèm số đo, và khoanh đỏ chỗ sai trong `design/viewcheck_<view>.png` hay `design/facecheck.png`.
- Chọn tin nhắn sửa tương ứng trong `fixes.txt`, dán vào **cùng cuộc trò chuyện**. Mỗi lần một tin.
- **Gemini vẽ lại cả hình mỗi lần**, nên sau mỗi tin sửa phải lưu hình mới đè lên và chạy lại lệnh kiểm từ đầu, không chỉ nhìn chỗ vừa sửa.
- Lưu cả hình chưa đạt (`design/attempts/`) để so.

## Sau khi khóa
Hình đã đạt cả 3 lệnh kiểm là **khóa thiết kế**: không tạo lại nữa. Lỗi về sau (khớp, chuyển động) sửa ở tool cắt hoặc engine, và `jointcheck` phải báo về 0 (xem `tools/joint_sheet.sh`).

## Tải về
Web miễn phí trả về 1024×1024 (1:1) và 1376×768 (16:9), đủ cho app (nhân vật cao khoảng 830 px, app cần 670–950 px). Không cần 4K. Biểu tượng ngôi sao ở góc dưới phải nằm trên nền, lệnh kiểm bỏ qua.

## Vì sao prompt viết như vậy
- **Tay giơ gần ngang vai, chân dạng:** nách, hông áo và đùi trong đều lộ ra, code không phải đoán phần bị che (gốc của lỗi rách nách).
- **Chỉ 2 góc:** 5 góc thì tỉ lệ và màu lệch nhau giữa các góc; xoay người làm bằng đổi góc.
- **Tờ mặt 22 ô:** tờ nhiều ô Gemini vỡ bố cục (tờ 58 ô và tờ thân 21 ô đều vỡ). Đầu trọc và tóc tách lớp code cắt từ hình gốc.
- **Chiều cao như hình nộm, cùng đường đất:** để mọi góc cùng tỉ lệ; lệnh kiểm đo.
- Đồ cũ (tờ thân 16:9, 5 góc, prompt API) ở `legacy/`.
