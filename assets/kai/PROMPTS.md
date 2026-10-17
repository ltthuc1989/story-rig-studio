# Tạo nhân vật bằng Gemini, cắt bằng tool

Gemini chỉ vẽ **một ảnh nhân vật** và **một bảng mặt**. Tool `:tools` cắt thân thành từng bộ phận, tách khớp, bo đầu tròn, xoá mắt và miệng có sẵn để thay được biểu cảm. Không bắt Gemini vẽ bảng bộ phận rời: thử nhiều lần vẫn không chia khớp được, và mỗi lần vẽ lệch tỉ lệ một kiểu.

Cách này ổn vì mọi bộ phận thân lấy từ cùng một ảnh, nên tỉ lệ, màu và nét luôn khớp nhau.

## Các lượt prompt

Mình tự chạy bằng `tools/gemini_gen.py`: chế độ free tier (chờ giữa các lần gọi, trần 20 lần/ngày), model `gemini-3.1-flash-image`. Bạn cũng có thể dán từng prompt vào app Gemini. Mỗi file trong `prompts/` là một prompt hoàn chỉnh.

| Lượt | File | Đính kèm (đúng thứ tự) | Lưu thành | Bắt buộc |
| --- | --- | --- | --- | --- |
| 1 | `prompts/1_rig_pose.txt` | ảnh nhân vật đã duyệt, rồi `assets/templates/rig_3q_right.png` (hình nộm) | `ref.png` | Có |
| 2 | `prompts/2_face_sheet.txt` | `ref.png` | `face.png` | Có |
| 3 | `prompts/3_backpack.txt` | `ref.png` | `backpack.png` | Không (đạo cụ rời) |

Đặt cả ba file vào `assets/kai/gemini/`. Không cần cắt nền.

### Lượt 1: dáng để rig

Đây là lượt quan trọng nhất. Tool chỉ cắt sạch được khi các bộ phận **không che lên nhau**. Trước khi lưu, kiểm tra ảnh:

- [ ] Có khoảng nền hở giữa mỗi cánh tay và thân, suốt dọc cánh tay. Tay chỉ chạm thân ở vai.
- [ ] Có khoảng nền hở giữa hai chân. Hai giày không chạm nhau.
- [ ] Không thấy thân ba lô, chỉ thấy hai quai trên áo.
- [ ] Tóc không chạm vai, tay không cầm gì.
- [ ] Nền hồng tím đặc, không có bóng dưới chân.

Sai điểm nào thì bấm tạo lại lượt 1. Đừng nhắn sửa, vì Gemini sửa thường giữ nguyên lỗi cũ. Chỉ khi cả 5 điểm đều đạt mới làm lượt 2.

Vì sao phải như vậy: ảnh `ref.png` đầu tiên có tay đè lên ba lô và hai giày chạm nhau. Khi tay giơ lên, chỗ bị che lộ ra vết vá, và tay kéo theo cả mẩu ba lô.

Đã thử ngày 2026-10-17 (các bản thử ở `gemini/attempts/`):

| Lần | Cách | Kết quả |
| --- | --- | --- |
| v1 | Chỉ có chữ, model 2.5 | Đúng dáng, **sai người** (tóc xoăn, mặt khác) |
| v2, v3 | Đính kèm Kai (v3 kèm thêm hình nộm), model 2.5 | Đúng người, nhưng **chép dáng cũ**, bỏ qua hình nộm |
| v4 | Đưa ảnh v1 vào để sửa người, model 2.5 | Vẫn chép dáng cũ |
| **v5** | Đính kèm Kai + hình nộm, model **3.1 flash** | **Đúng người, đúng dáng, nền sạch.** Đang dùng làm `ref.png` |

Kết luận: phải dùng model 3.1 (hoặc mới hơn) và hình nộm. Với model 2.5, cứ đính kèm ảnh nhân vật là nó chép luôn dáng của ảnh đó. Ảnh Kai đã duyệt lúc đầu lưu ở `gemini/ref_v0_approved.png`.

### Lượt 2: bảng mặt

Gemini vẽ được bảng mặt đạt ngay từ lần đầu. Tool lấy mắt, mày, miệng mặc định từ chính `ref.png`, còn bảng mặt chỉ cung cấp các biểu cảm khác (nhắm, nửa nhắm, mở to, nhíu mày, các hình miệng). Prompt yêu cầu không vẽ mảng da quanh miệng.

## Sau khi có ảnh: mình làm

1. Không cần đo điểm bằng tay nữa. Lệnh `cutauto` tự tìm các điểm cắt:
   - Lấy khớp của hình nộm trong `assets/templates/rig_3q_right.csv` (toạ độ trên `rig_3q_right.png`), khớp khung bao bóng nhân vật với bóng hình nộm để có vị trí đoán ban đầu. Gemini chép dáng và khung của hình nộm nên vị trí đoán chỉ lệch vài chục px.
   - Chỉnh từng điểm theo hình dạng bóng nhân vật, không theo màu: cổ là chỗ hẹp nhất giữa đầu và vai (cắt ngay trên chỗ vai loe ra); đường vai đi qua nách (đỉnh khe nền dưới cánh tay, bỏ qua khe hẹp dưới 12 px) và vuông góc với trục tay; cổ tay là chỗ hẹp ở cuối cẳng tay trước khi bàn tay loe ra; khuỷu ở giữa vai và cổ tay; đường hông ngang đáy háng (đỉnh khe nền giữa hai chân); chỗ hai giày chạm nhau đi từ đáy khe giữa hai chân xuống khấc ở mép đế; đầu gối ở chỗ ống quần kết thúc (bề rộng chân thu hẹp đột ngột); cổ chân ở chỗ bàn chân bắt đầu rộng ra. Bán kính vùng tay lấy từ bề rộng tay đo được.
   - Màu chỉ dùng ở chỗ hai bộ phận vẽ đè lên nhau mà không có khe, và luôn so với màu của chính nhân vật: màu quần lấy mẫu quanh hông (tách ống chân khỏi ống quần, tách quần khỏi áo), màu tóc lấy ở đỉnh đầu, màu da là màu nhiều nhất trên đầu. Mắt, mày, miệng là các mảng tương phản với da nằm trọn trong mặt, phân loại theo vị trí và hình dạng; ngưỡng tương phản giảm theo độ sáng của da.
2. Chạy `./gradlew :tools:run --args="scan assets/kai/gemini/face.png <thư mục tạm>"` để đánh số các mảnh trong bảng mặt, rồi ghi toạ độ từng biểu cảm vào `KaiSpec.face` (bước này vẫn làm tay).
3. Chạy `./gradlew :tools:run --args="cutauto assets/kai/gemini <thư mục ra> <thư mục xem thử>"` (thêm `kai` ở cuối để in bảng so từng điểm tự tìm với điểm đo tay trong `KaiRefSpec.kt`). Lệnh này ghi ra:
   - `parts/` (các file PNG), `bones.csv`, `parts.csv`, để app nạp;
   - `points.png` (điểm đoán từ hình nộm màu xanh dương, điểm tự tìm màu xanh lá, điểm đo tay màu đỏ nếu có), `regions.png` (mỗi vùng một màu, kiểm tra cắt đúng chưa), `rest_vs_ref.png` (ghép lại đặt cạnh ảnh gốc), `pose_dance.png` và `pose_bend.png` (xoay khớp, kiểm tra có hở không).
   - `cutref` (điểm đo tay trong `KaiRefSpec.kt`) vẫn giữ nguyên để so sánh; Kai trong app hiện vẫn cắt bằng `cutref`.
4. Mở các ảnh xem thử ra kiểm tra rồi mới báo là đạt.
5. Kiểm tra độ bền không cần Gemini: `./gradlew :tools:run --args="autotest assets/kai/gemini <thư mục tạm>"` tạo các bản biến thể của `ref.png` (đổi màu toàn bộ, đổi màu áo, quần, da tối hơn, thu nhỏ 0,85 và dời chỗ, kéo giãn ngang 1,12), cắt từng bản bằng `cutauto` và so với điểm đo tay đã biến đổi theo cùng phép biến đổi.

Giới hạn của `cutauto`: cần dáng của hình nộm (tay giang, có khe nền dưới nách và giữa hai chân), nền hồng tím. Váy hoặc áo choàng che kín khe giữa hai chân, tóc dài chạm vai, áo liền quần cùng màu với áo thì phải kiểm tra kỹ `regions.png`, có thể phải sửa tay.

## Bảng chấm

| Tiêu chí | Cách kiểm tra |
| --- | --- |
| Đúng style | So với cảnh đồng cỏ đã duyệt: không viền, đúng bảng màu, bóng là một mảng phẳng |
| Cắt sạch | `regions.png`: mỗi bộ phận đúng một màu, không lấn sang vùng khác |
| Dáng gốc | `rest_vs_ref.png`: hai nửa giống nhau |
| Khớp | `pose_*.png`: xoay tay chân không hở, không có vết vá, không sót mẩu |
| Biểu cảm | Mắt, miệng từ bảng mặt khớp cỡ và kiểu với mặt trong `ref.png` |

## Lịch sử

- `prompts/legacy/`: bộ prompt cũ (bảng bộ phận thân, Recraft). Không dùng nữa.
- Kai trong app được cắt bằng `cutref` từ `ref.png` (v5). Lệnh `build` (cắt từ bảng bộ phận) vẫn còn nhưng không dùng nữa.
