# Tạo nhân vật bằng Gemini, cắt bằng tool

Gemini chỉ vẽ **một ảnh nhân vật** và **một bảng mặt**. Tool `:tools` cắt thân thành từng bộ phận, tách khớp, bo đầu tròn, xoá mắt và miệng có sẵn để thay được biểu cảm, nhận các biểu cảm trên bảng mặt và tính vùng lưới cho xương. App chỉ nạp các file tool ghi ra. Không bắt Gemini vẽ bảng bộ phận rời: thử nhiều lần vẫn không chia khớp được, và mỗi lần vẽ lệch tỉ lệ một kiểu.

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

Tool lấy mắt, mày, miệng mặc định từ chính `ref.png`, còn bảng mặt chỉ cung cấp các biểu cảm khác (nửa nhắm, nhắm cười, mở to, mày nhướn, nhíu, buồn, các hình miệng). Prompt yêu cầu không vẽ mảng da quanh các mảnh.

Prompt bắt Gemini xếp bảng theo **lưới cố định 6 hàng**, đọc từ trái sang phải, vì chỉ nhìn hình dạng thì không biết chắc mảnh nào là biểu cảm nào (mày nhướn trông như mày thường, miệng A, S, cười to đều là miệng mở to, mắt nhắm cười giống hệt miệng buồn):

| Hàng | Các mảnh, trái sang phải |
| --- | --- |
| 1 | mắt gần mở, mắt xa mở, mắt gần nửa nhắm, mắt xa nửa nhắm |
| 2 | mắt gần nhắm cười, mắt xa nhắm cười, mắt gần mở to, mắt xa mở to |
| 3 | mày gần thường, mày xa thường, mày gần nhướn, mày xa nhướn |
| 4 | mày gần nhíu, mày xa nhíu, mày gần buồn, mày xa buồn |
| 5 | miệng ngậm cười (M/B/P), hé (E/I), mở to (A), tròn nhỏ (O) |
| 6 | miệng hở răng trên (S/T), cười to, miệng buồn |

Trước khi lưu, kiểm tra ảnh:

- [ ] Đúng 6 hàng, số mảnh mỗi hàng là 4, 4, 4, 4, 4, 3, không có gì khác trên bảng (không chữ, không ngôi sao).
- [ ] Mảnh "gần" là mảnh bên trái của mỗi cặp, mảnh "xa" hẹp hơn một chút.
- [ ] Không có mảng da quanh mắt nhắm hay quanh miệng.

Sai thì bấm tạo lại lượt 2. Nếu cứ sai bố cục, có thể dùng ảnh đó nhưng phải viết `face_map.csv` (xem bên dưới).

## Sau khi có ảnh: mình làm

Với nhân vật mới chỉ cần `ref.png` (lượt 1) và `face.png` (lượt 2) trong một thư mục, ví dụ `assets/<tên>/gemini/`. Không viết dòng Kotlin nào cho riêng nhân vật.

1. Chạy `./gradlew :tools:run --args="cutauto <thư mục gemini> <thư mục ra> <thư mục xem thử>"`. Lệnh này tự làm hết:
   - **Điểm cắt.** Lấy khớp của hình nộm trong `assets/templates/rig_3q_right.csv` (toạ độ trên `rig_3q_right.png`), khớp khung bao bóng nhân vật với bóng hình nộm để có vị trí đoán ban đầu. Gemini chép dáng và khung của hình nộm nên vị trí đoán chỉ lệch vài chục px. Sau đó chỉnh từng điểm theo hình dạng bóng nhân vật, không theo màu: cổ là chỗ hẹp nhất giữa đầu và vai (cắt ngay trên chỗ vai loe ra); đường vai đi qua nách (đỉnh khe nền dưới cánh tay, bỏ qua khe hẹp dưới 12 px) và vuông góc với trục tay; cổ tay là chỗ hẹp ở cuối cẳng tay trước khi bàn tay loe ra; khuỷu ở giữa vai và cổ tay; đường hông ngang đáy háng (đỉnh khe nền giữa hai chân); chỗ hai giày chạm nhau đi từ đáy khe giữa hai chân xuống khấc ở mép đế; đầu gối ở chỗ ống quần kết thúc (bề rộng chân thu hẹp đột ngột); cổ chân ở chỗ bàn chân bắt đầu rộng ra. Bán kính vùng tay lấy từ bề rộng tay đo được.
   - **Màu** chỉ dùng ở chỗ hai bộ phận vẽ đè lên nhau mà không có khe, và luôn so với màu của chính nhân vật: màu quần lấy mẫu quanh hông (tách ống chân khỏi ống quần, tách quần khỏi áo), màu tóc lấy ở đỉnh đầu, màu da là màu nhiều nhất trên đầu. Mắt, mày, miệng có sẵn trên `ref.png` là các mảng tương phản với da nằm trọn trong mặt, phân loại theo vị trí và hình dạng; ngưỡng tương phản giảm theo độ sáng của da.
   - **Bảng mặt** (`FaceSheet.kt`). Tách các mảnh trên `face.png`, gộp mẩu sáng nhỏ sát bên (môi dưới) vào mảnh lớn cạnh nó, xếp thành hàng, rồi đặt tên theo lưới 6 hàng ở trên. Hình dạng chỉ dùng để kiểm tra: mắt có lòng trắng và không có lưỡi, mắt nửa nhắm có mi màu da, mắt nhắm và miệng ngậm là nét cong (cong xuống hay cong lên), miệng O là mảng tối tròn nhỏ, miệng mở có lưỡi hồng ở nửa dưới. Mảnh nào hình dạng không khớp tên, hay nằm trên mảng da, sẽ in dòng `WARNING face`. Nếu số mảnh mỗi hàng không đúng 4, 4, 4, 4, 4, 3 thì tool đoán tên theo hình dạng (in cả bảng để kiểm tra) và dừng nếu thiếu biểu cảm app cần. Cỡ: mắt và mày theo tỉ lệ của mắt gần lúc mở so với mắt trên `ref.png`, miệng theo miệng ngậm cười; mỗi biểu cảm đặt giữa đúng chỗ của mảnh mặc định.
   - **Lưới cho `art=mesh`** (`MeshSpec.kt`): nhóm lưới (tay, chân, thân, hông) và vùng ảnh hưởng của từng xương tính từ chính các điểm cắt: xương tay chân đi từ khớp này đến khớp sau; thân là một khối dọc theo giữa người, bán kính bằng nửa chỗ hẹp nhất của áo dưới vai, gặp khối hông đúng ở gấu áo; hông là khối ngắn giữa eo và gốc, dài bằng nửa khoảng cách hai hông; độ rộng hoà trộn, dải bám vai/hông và cỡ ô lưới tỉ lệ với nửa bề rộng tay ở khuỷu.
2. Lệnh ghi ra:
   - vào thư mục ra, để app nạp: `parts/` (các file PNG), `bones.csv` (xương, xương cha, tâm xoay), `parts.csv` (bộ phận, xương, vị trí, cỡ, có vẽ ở dáng gốc không), `mesh.csv` (dòng `group`: tên lưới, cỡ ô, các bộ phận, các xương; dòng `influence`: xương, đoạn x0,y0,x1,y1, bán kính, độ rộng hoà trộn, xương cha để bám, dải bám; toạ độ là px trên `ref.png`);
   - vào thư mục xem thử: `points.png` (điểm đoán từ hình nộm màu xanh dương, điểm tự tìm màu xanh lá, điểm đo tay màu đỏ nếu có), `regions.png` (mỗi vùng một màu, kiểm tra cắt đúng chưa), `rest_vs_ref.png` (ghép lại đặt cạnh ảnh gốc), `pivots.png`, `pose_dance.png` và `pose_bend.png` (xoay khớp, đổi mắt và miệng, kiểm tra có hở không).
3. Mở các ảnh xem thử ra kiểm tra theo bảng chấm bên dưới, đọc các dòng `WARNING face`, rồi mới báo là đạt.
4. Chép thư mục ra vào `shared/src/commonMain/composeResources/files/kai/` (app hiện chỉ nạp một nhân vật ở đường dẫn này), xoá thư mục cũ trước để không sót file. Chạy snapshot desktop `./gradlew :desktopApp:snapshot --args="out=<thư mục> scene=kai frames=300 pick=60,109,200,293"` (thêm `art=mesh` để xem bản lưới) và mở ảnh ra xem vai, hông, khuỷu, gối.

Kai: dùng `cutauto` (từ 2026-10-17, trước đó là `cutref`). Bảng mặt của Kai vẽ trước khi có lưới cố định (`face.png` có mảng da quanh miệng, nên miệng lấy từ `face_extra.png`), nên mảnh nào là biểu cảm nào ghi trong `gemini/face_map.csv` (tên bộ phận, tên bảng, một điểm trên mảnh; lấy số bằng `./gradlew :tools:run --args="scan <bảng>.png <thư mục tạm>"`). Có file này thì tool dùng nó, và in bảng so với kết quả tự tìm: trên hai bảng của Kai tự tìm theo hình dạng ra đúng 19/19 mảnh, nhưng đó là luật đoán đã chỉnh trên chính hai bảng này, nên với nhân vật mới vẫn dựa vào lưới cố định. Thêm `kai` ở cuối lệnh `cutauto` để in bảng so từng điểm cắt tự tìm với điểm đo tay trong `KaiRefSpec.kt`; `cutref` (điểm đo tay) vẫn giữ để so sánh.

Kiểm tra độ bền không cần Gemini:

- `./gradlew :tools:run --args="autotest assets/kai/gemini <thư mục tạm>"` tạo các bản biến thể của `ref.png` (đổi màu toàn bộ, đổi màu áo, quần, da tối hơn, thu nhỏ 0,85 và dời chỗ, kéo giãn ngang 1,12), cắt từng bản bằng `cutauto` và so với điểm đo tay đã biến đổi theo cùng phép biến đổi; cuối cùng chạy `facetest`.
- `./gradlew :tools:run --args="facetest assets/kai/gemini <thư mục tạm>"` xếp các mảnh mặt của Kai thành một bảng đúng lưới 6 hàng, rồi đọc lại không cần `face_map.csv`: phải ra 23/23 tên đúng ô.

Giới hạn của `cutauto`: cần dáng của hình nộm (tay giang, có khe nền dưới nách và giữa hai chân), nền hồng tím. Váy hoặc áo choàng che kín khe giữa hai chân, tóc dài chạm vai, áo liền quần cùng màu với áo thì phải kiểm tra kỹ `regions.png`, có thể phải sửa tay. Bảng mặt không theo lưới thì phải viết `face_map.csv`.

Việc còn làm tay cho mỗi nhân vật: duyệt ảnh lượt 1 và lượt 2 theo danh sách kiểm tra, mở ảnh xem thử. Trong app, `KaiScene` vẫn có hai hằng số đo trên Kai (`ROOT_TO_SOLE`, `KaiRig.HEIGHT`); `KaiAssets.rootToSole` và `KaiAssets.height` tính sẵn hai số này từ dữ liệu.

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
- Kai trong app được cắt bằng `cutref` từ `ref.png` (v5) đến 2026-10-17, sau đó bằng `cutauto`. Lệnh `build` (cắt từ bảng bộ phận) vẫn còn nhưng không dùng nữa.
- Trước 2026-10-17 bảng mặt của Kai và vùng lưới (`MESH_GROUPS`, `INFLUENCES` trong `KaiArt.kt`) là hằng số viết tay; nay là `face_map.csv` và `mesh.csv`.
