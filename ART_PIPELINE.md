# ART_PIPELINE.md — Hình nhân vật, tool cắt và chuyển động

Spec cho phần nhân vật bằng ảnh (P5 trong `SPIKE.md`): làm hình thế nào, cắt thành mảnh ra sao, engine chuyển động những gì. Cập nhật khi xong mỗi bước ở mục 7.

## 1. Nguyên tắc: bộ kit vẽ sẵn bộ phận, chuyển động bằng code

Quy trình chuẩn của các xưởng làm hoạt hình cắt mảnh (Spine, Moho, Toon Boom Harmony, Rive) có 3 tầng, tầng nào cũng cần:

1. **Hình nguồn là các bộ phận rời, vẽ cả phần bị che** (overdraw):
   - thân vẽ đủ hông áo và nách dưới tay;
   - đầu bắp tay và đầu đùi là hình tròn có tâm ở khớp (ball joint);
   - cổ vẽ dư lên dưới cằm, cạp quần vẽ dư lên dưới vạt áo, cổ giày vẽ dư lên dưới gấu quần.

   **Mesh chỉ uốn những điểm ảnh đã có, không tự vẽ ra phần chưa có.**
2. **Thư viện hoán đổi (swap):** các góc nhìn, khẩu hình miệng, mắt, lông mày, bàn tay.
3. **Mọi chuyển động bằng xương, mesh và clip:** đi, chạy, vẫy, cúi chào, nhảy, ngồi, theo nguyên tắc hoạt hình (nhanh dần chậm dần, lấy đà, lệch pha, co giãn, khoá bàn chân). Khi xoay người, đổi góc trong 2–3 khung, có che hoặc nhoè (như demo Bobo). Cắt mảnh không xoay liên tục 360° được.

**Không tạo hình cho từng động tác.** Mỗi hình Gemini lệch nhau một chút, ghép theo khung sẽ rung, chi phí lại nhân theo số động tác. Chỉ tạo hình cho những gì code không làm ra được.

**Bài học (2026-10-18):** bản đầu của spec này chỉ có tầng 2 và 3. Bộ cắt hiện tại cắt từ một hình phẳng dáng chữ A, nên phải đoán phần khuất (nắp tròn, phần tô thêm, phần tô lại màu). Vì vậy các lỗi vai và nách cứ quay lại:
- độn vai;
- rách nách;
- vạt áo khi giơ tay;
- một mảng ngực xoay theo tay.

Tầng 1 là cách sửa tận gốc. Cắt hình phẳng chỉ còn là đường dự phòng (mục 4).

| Code làm được | Phải có hình |
| --- | --- |
| Tay chân co duỗi, đi, chạy, ngồi (đùi co ngắn theo chiều sâu), cúi, nhảy, thở, chỉ, vỗ tay | Phần bị che ở khớp: nách, đầu vai, háng, cổ, cạp quần, cổ chân |
| Gập khuỷu và gối (mesh uốn trên bộ phận có sẵn) | Mặt sau, mặt nghiêng (gáy, tóc sau, mũi nghiêng) |
| Co theo chiều sâu (`pitch`), lật bàn tay (`roll`), nhún vai | Hình dạng bàn tay (nắm, chỉ, đếm số…) |
| Lấy đối xứng: mắt, tay, chân phía bên kia; góc nhìn trái lấy từ phải | Khẩu hình miệng, mắt nhắm, nhắm cười, chớp |
| Mặt 3/4 suy từ mặt trực diện (co ngang ~0,85); đảo mắt (khi tròng đen là lớp riêng) | Tóc tách lớp (tóc sau, mái, đuôi ngựa) |

## 2. Bộ kit: 2 tờ Gemini 4K (16:9) mỗi nhân vật

Hai tờ, không phải một: gộp tất cả vào một tờ thì các ô miệng, mắt, bàn tay quá nhỏ và Gemini dễ vẽ sai chỗ. Đính kèm thiết kế đã duyệt `ref.png` (3/4) và `view_front.png` (trực diện) làm chuẩn nhận diện, kèm tờ mẫu hình nộm. Nền magenta #FF00FF, không chữ, không bóng đổ.

### Tờ 1: thân (`rig_body.png`, prompt `body_sheet.txt`)
| Hàng | Nội dung |
| --- | --- |
| A | 5 góc toàn thân dáng chữ A: trực diện (0°), 3/4 (45°), nghiêng (90°), 3/4 sau (135°), sau lưng (180°). Quay về phía bên phải ảnh, cùng chiều cao, cùng đường đất. Góc nghiêng: tay gần đưa nhẹ ra trước, có khoảng trống với thân |
| B1 | **Bộ phận rời góc trực diện, vẽ đủ phần khuất:**<br>• thân áo kèm cổ vẽ dư lên dưới cằm, không tay; hông áo, nách và vai tròn vẽ đủ<br>• quần hoặc váy riêng, cạp quần vẽ dư lên<br>• một cánh tay trong tay áo, đầu vai tròn<br>• một chân, đầu đùi tròn vẽ dư lên trong ống quần<br>• một chiếc giày riêng, cổ giày vẽ dư lên |
| B2 | **Bộ phận rời góc nghiêng** (cho đi và chạy): thân nghiêng, một cánh tay nghiêng có đầu vai tròn, một chân nghiêng có đầu đùi tròn, giày nghiêng. Tay chân phía xa lấy từ bản sao. Không tô sẫm trong hình: Gemini vẽ tay xa sẫm hẳn thành màu da như bị bệnh (2026-10-18); nếu cần bóng cho phía xa thì engine làm tối nhẹ lúc vẽ (chưa có, chỉnh được mức) |
| B3 | **Tóc tách lớp:** tóc phía sau, tóc mái hoặc tóc phía trước; đuôi ngựa hoặc tóc dài riêng nếu có, gốc tóc vẽ dư |

### Tờ 2: mặt và tay (`rig_face_hands.png`, prompt `face_hands_sheet.txt`)
| Hàng | Nội dung |
| --- | --- |
| C1 | **Mắt trực diện:** một mắt vẽ riêng lòng trắng và tròng đen; mắt nhắm hờ, nhắm hẳn (một nét, để chớp), nhắm cười, mở to; mắt kia lấy đối xứng. Lông mày: thường, nhướng, cau, buồn |
| C2 | **12 miệng trực diện** (nhép môi cho phonics): nghỉ hoặc M/B/P, A/I, E, O, U/W, F/V, L/TH, S/T, cười, cười lớn, buồn, ngạc nhiên |
| C3 | **Mặt nghiêng:** mắt nghiêng (mở, nhắm, cười, mở to), lông mày, 12 miệng nghiêng |
| C4 | **Bàn tay phải** (tay trái lấy đối xứng). Trực diện: xoè thả lỏng, nắm, chỉ, giơ ngón cái, xoè lòng bàn tay (vẫy), ngửa (mời), cầm (chữ C), đếm 1–4 (5 là xoè). Nghiêng: thả lỏng, nắm |

**Bổ sung sau rà soát khớp (2026-10-18, mục 8):**
- **Tờ 1:**
  - ống chân vẽ dư xuống trong giày;
  - chân một mảnh có gối thật (gấu quần đùi nằm trên đùi);
  - Hana: đùi vẽ dư lên tới hông dưới váy;
  - đầu không tóc (để đầu không giữ bản sao tóc), dây buộc tóc nằm trên đuôi tóc;
  - bộ phận rời góc 3/4: thử xếp vừa 2 tờ (chuyển B3 sang tờ 2, xếp sát; nếu vẫn thiếu thì thu hàng A còn khoảng 1150 px). Đo cỡ ô ở Bước 1; chỉ thêm tờ 3 khi không vừa và người dùng đồng ý;
  - dáng ngồi nhìn trực diện và 3/4 (đùi hướng về người xem, code không làm được), nếu cảnh cần ngồi quay mặt ra.
- **Tờ 2:** lòng bàn tay và mu bàn tay của cùng dáng; bàn tay khi vỗ (nhìn cạnh); cổ tay vẽ dư.
- **Phụ kiện tách lớp, nay bắt buộc:** kính và ria của Bo, balo của Kai (mặt sau, quai). Đồ vật cầm tay thuộc cảnh, không thuộc nhân vật.
- **Lật đối xứng cho góc nhìn trái:** người dùng chấp nhận hình in ngực và tóc mái đổi bên.
- **sheetcheck thêm:** miệng giày đủ vòng; có đầu không tóc; có lòng và mu bàn tay; phụ kiện tách riêng; đầu tròn có tâm ở khớp; các góc cùng độ dài tay áo (hình gốc của Hana: trực diện tay dài, 3/4 tay ngắn).

### Tạo lại cho cả 5 nhân vật (quyết định 2026-10-18)
- **Giữ nguyên thiết kế:** `ref.png` và `view_front.png` của từng nhân vật chỉ dùng làm chuẩn nhận diện, không tạo lại.
- **Tạo mới 2 tờ cho mỗi nhân vật:**
  - không hình cũ nào có bộ phận rời có vẽ phần khuất, bộ phận nghiêng, tóc tách lớp, mắt tách tròng, 12 khẩu hình, bàn tay đếm số;
  - vẽ chung 2 tờ thì mọi góc và bộ phận cùng nét, cùng tỉ lệ, cùng màu.
- **Chi phí:** 10 lượt × $0,151 ≈ $1,51; nếu mỗi tờ phải gọi lại 1 lần, tối đa khoảng $3.
- **Thứ tự: làm trọn một nhân vật trước (Kai).** Gồm 2 tờ, `sheetcheck`, lắp rig, mọi góc, engine, rồi xem trong app. Người dùng thấy ổn hết mới gọi Gemini cho nhân vật tiếp theo, mỗi lần một nhân vật. Nếu quy trình còn lỗi thì chỉ tốn lượt gọi của Kai, không tốn cho cả 5.
- Dữ liệu cắt hiện có trong app giữ lại làm dự phòng cho tới khi kit mới của nhân vật đó đạt `sheetcheck` và bảng kiểm tra vai và nách.
- Tờ thử cũ (`rig_sheet.png` 21:9, `turnaround_sheet.txt`, `assets/kai/gemini/turnaround_v1.jpg`) dùng để thử `splitsheet` và `sheetcheck`.

### Chọn cỡ ảnh

Giá gemini-3.1-flash-image (không có 3K):

| Mức | Cỡ tờ 16:9 | Giá/tờ | Người toàn thân cao | Miệng rộng |
| --- | --- | --- | --- | --- |
| 1K | 1376×768 | $0,067 | ~400 px | ~30 px |
| 2K | 2752×1536 | $0,101 | ~800 px | ~65 px |
| 4K | 5504×3072 | $0,151 | ~1550 px | ~130 px |

Nhân vật cận cảnh cao khoảng 650–860 px trên điện thoại (sân khấu 1920×1080) và 1200 px trở lên trên iPad. Vì vậy chọn **4K**: chỉ đắt hơn 2K $0,05 mỗi tờ, đủ cho iPad và cận mặt, cắt ngón tay chính xác hơn. Sau khi tách, tool thu nhỏ về cỡ app cần nên bộ nhớ trong app không tăng.

## 3. Quy trình

```
2 tờ mẫu → Gemini (2 lượt, người dùng duyệt) → splitsheet → sheetcheck → cutparts (góc có bộ phận rời) / cutauto (các góc còn lại) → selftest + pose_sheet → composeResources/files/<tên>_<góc>
```

- **`splitsheet`:** tách nền, tìm từng cụm điểm ảnh, gán vào ô mẫu, đưa toàn thân về chiều cao hình nộm (đỉnh đầu 110, đế 940 trên khung 1024). Ra `view_<góc>.png`, `face_front.png` kèm `face_map.csv`, `face_side.png`, `hands.png`.
- **`sheetcheck`:** tự kiểm, để người không chuyên thiết kế khỏi phải tự tìm lỗi. Mỗi mục ra ĐẠT hoặc LỖI kèm số đo, có ảnh khoanh đỏ chỗ sai:
  1. đủ ô, không có gì thừa;
  2. 5 người cao bằng nhau (lệch ≤ 2 %), cùng đường đế;
  3. mặt trực diện đối xứng;
  4. màu khớp giữa các góc;
  5. có khoảng trống giữa tay và thân, giữa hai chân;
  6. không có hồng hay magenta trên nhân vật;
  7. cắt thử qua `cutauto` và selftest;
  8. góc nghiêng chỉ có 1 mắt, góc sau lưng không có mặt;
  9. bộ phận rời khớp với người trực diện về màu và cỡ (bề ngang thân, độ dài tay chân, lệch ≤ 5 %); đầu vai và đầu đùi là hình tròn; thân không khuyết ở nách;
  10. đủ 12 miệng, đủ bàn tay; mắt có tròng đen tách riêng.
- **Cắt:** `./gradlew :tools:run --args="cutauto <geminiDir> <outDir> <previewDir>"`. Kiểm tra: `./gradlew :tools:run --args="selftest <dir>"`.
- **Bảng thử khớp (bắt buộc sau mỗi lần sửa tool cắt, rig hay clip):** `tools/joint_sheet.sh <outDir> [cuts]`.
  - Mỗi bộ cắt (mặc định cả 11) chụp 16 tư thế cực hạn của clip `STRESS` (DanceClips.kt): đứng yên, dang tay, chữ V, tay vào ngực, gập khuỷu, lật bàn tay, nhấc gối, ngồi, đá chân ra sau, xoay cổ chân, cúi, nghiêng đầu, lắc tóc và váy, đưa tay ra trước, chống nạnh, sải bước.
  - Ra `<cut>.png` (8×2 ô) và `<cut>/pose_NN.png` ở cỡ thật để phóng to từng khớp.
  - Xem hết mọi ô của mọi bộ, so với bảng của commit tốt gần nhất, rồi mới báo đã sửa.
- **Bảng vai và nách** (cũ, vẫn dùng được để soi nhanh vai): `tools/pose_sheet.sh <out.png> [chars]`. Mỗi nhân vật một hàng gồm đứng yên, vẫy tay, dang tay, đang giơ và chữ V, phóng to phần vai. Phải mở ra xem cả bộ trực diện lẫn 3/4 trước khi báo là đã sửa xong. Đã có lần sửa lỗi này lại làm hỏng lại lỗi cũ vì chỉ soi một tư thế.

## 4. Lắp rig

### Quy tắc khớp chuẩn (mọi khớp, cả bộ kit lẫn cắt hình phẳng)
1. **Mảnh che:** ở mỗi khớp, mảnh có mép vẽ (vai áo, cổ áo, gấu áo, gấu quần, cổ tay áo, cổ giày, chân tóc) được vẽ trên và không xoay theo mảnh con.
2. **Mảnh con có đầu tròn:** tâm đầu tròn ở điểm khớp, nằm dưới mảnh che. Đầu tròn tô bằng màu hoặc hoa văn của chính mảnh con, không chép điểm ảnh của mảnh khác (quai balo, sọc ngực, bóng cằm).
3. **Điểm khớp ở khớp xương thật** (theo hình nộm), không theo mép quần áo.
4. **Thứ tự vẽ đổi theo tư thế** khi tay chân đi qua trước hoặc sau thân.
5. **Code không làm được thì đưa vào bộ kit** (lòng bàn tay, tóc tách lớp, kính, dáng ngồi nhìn trực diện…).

Lỗi khớp nào cũng thuộc một trong 5 loại: A mép vẽ nằm trên mảnh xoay; B phần khuất chép từ hình gốc; C điểm khớp sai chỗ; D thứ tự vẽ cố định; E thiếu hình. Danh sách đã rà ở mục 8.

### Chuẩn: lắp từ bộ phận rời (`cutparts`, sẽ làm)
- Đặt từng bộ phận vào đúng chỗ trên người trực diện ở hàng A, bằng cách so viền (silhouette). Tận dụng `AutoSpec` để đo khớp.
- Vai và hông đặt ở tâm đường tròn đầu bộ phận. Khuỷu, gối, cổ tay, cổ chân cắt và đặt nắp như cách cắt hình phẳng.
- Thứ tự vẽ: bắp tay sau thân, cẳng tay và bàn tay trước thân. Mesh tách nhóm bắp tay và nhóm cẳng tay.
- Không có điểm xoay khi buông tay, nên `slideShoulders` tự không trượt. Nhún vai (`SHRUG`) vẫn dùng.
- Selftest so tư thế đứng lắp xong với người trực diện. Bảng vai và nách phải sạch mà không cần nắp hay phần tô thêm.

### Dự phòng: cắt từ hình phẳng (tools/…/AutoSpec.kt, RefCut.kt)
Dùng cho 5 bộ hình hiện có, cho các góc chưa có bộ phận rời (3/4, 3/4 sau, sau lưng), và khi bộ phận Gemini vẽ không khớp. Các quy tắc dưới đây là để đoán phần bị che.


- **Vai:** đường cắt dọc theo hông áo, từ đỉnh nách lên đỉnh vai, vượt ra mỗi đầu `SHOULDER_MARGIN` (6 px).
- **Quả cầu vai** (`shoulderBall`, 2026-10-18, thay khớp vai trượt): vai là một mảnh tròn gắn vào thân trên xương `shoulder_ball_<bên>` không bao giờ xoay, vẽ đè lên đầu tay (thứ tự: bắp tay, cẳng tay, quả cầu, bàn tay). Viền vai đứng yên, chỉ tay xoay và chui ra từ dưới vai.
  - **Tâm và bán kính** (`shoulderJoint`): đỉnh vai chiếu vuông góc xuống trục tay; bán kính là khoảng cách từ đỉnh vai tới trục tay (quả cầu nội tiếp đỉnh tay áo). Tay xoay quanh đúng tâm này, không trượt và không nhún (`bones.csv` không còn cột hang, nên `slideShoulders` tự bỏ qua).
  - **Màu:** phía thân lấy chính điểm ảnh của thân; phía tay lấy hoa văn thân áo kéo ngang (sọc của Leo chạy liền qua vai), chỉ lấy màu chính chung của thân và tay áo (không lấy quai balo, hình in). Gấu tay áo ngắn không vào quả cầu, vì nó là mép của tay áo và đi theo tay.
  - **Đầu tay** (`roundHead`): ngoài bán kính quả cầu, tay chỉ giữ phần phía trước tâm (dọc trục tay) và ở phía tay của đường cắt. Phần sau tâm từng văng ra như độn vai; nắp và mặt trong ống tay phía thân từng quét lên ngực khi tay buông.
  - **Đã thử và bỏ** (2026-10-18): tâm ở giữa đường cắt với bán kính nửa đường cắt. Tay xiên thì đường cắt dài, nên vai phồng như tay áo bồng khi tay buông và để lại cục tay áo cố định ở vai khi giơ (rõ nhất ở Bo).
- **Mép thân dọc đường cắt vai** (`cleanShoulderSide`): đường hông áo dưới nách được kéo tiếp lên vai bằng màu áo (tối đa 12 px), và điểm ảnh lạc màu trong khoảng ±4 px quanh đường cắt được tô lại. Như vậy chỗ thân lộ ra khi tay giơ không còn khấc hay lấm tấm. Mặt trong ống tay vẫn chuyển sang tay, còn thân giữ một bản màu áo trơn ở dưới.
- **Vai cứng trong mesh:** lưới thân không lấy trọng số theo xương vai, và cánh tay không neo vào thân. Nếu có, khi giơ tay cao (chữ V, khoảng 150°) vải hông áo bị kéo rách thành vạt.
- **Đầu cánh tay:** đĩa cắt theo dây cung từ đầu nách của đường cắt lên mép đĩa: nửa trên che đường vai khi tay buông, mặt dưới tay chạy thẳng từ nách khi giơ. Bán kính trừ `SHOULDER_MARGIN`.
- **Màu nắp vai** (`sleeveColour`): mỗi điểm của nắp đi dọc hướng sọc tay áo (vuông góc trục tay, cả hai phía) tới điểm tay áo gần nhất có màu chính của tay áo, rồi lấy màu đó. Không chép điểm ảnh của hình gốc ở chỗ nắp, vì chỗ đó là ngực áo: sọc ngực sẽ xoay theo tay thành một mảng lệch (lỗi của Leo). Ở tư thế đứng, nắp phủ đầu sọc ngực như một đường may vai; lệch màu với hình gốc vẫn dưới 1 % (Leo trực diện 0,43 %). Từ khi có quả cầu vai, nắp nằm dưới quả cầu và chỉ còn để lấp khe.
- **Mặt trong ống tay:** điểm ảnh trên thân sát đường cắt vai, có màu là sắc sẫm hơn của màu áo (mặt trong ống tay, đầu viền tay áo chạy vào nách), được chuyển sang tay. Khác tông màu (quai balo, hình in) thì giữ trên thân.
- **Thân:** chỉ tô `ARM_FILL` (2 px) sau tay để khử răng cưa. Tô sâu hơn sẽ lộ ra như một cục áo khi giơ tay.
- **Trục tay:** khi trục dò từ mẫu lệch quá 15°, lấy trục theo chính điểm ảnh cánh tay (PCA).
- **Bàn tay:** mảnh cứng (`BitmapPart`) trên xương cổ tay, không nằm trong lưới cánh tay.
- **Thứ tự vẽ:** 3/4 dùng `drawOrder`; trực diện dùng `drawOrderFront` (tay trước thân, sau đầu); sau lưng dùng `drawOrderFrontBack` (tay sau thân).
- **Góc nghiêng (sẽ làm):** tay và chân xa lấy từ bản sao tay và chân gần, tô sẫm hơn, vì phần xa bị thân che gần hết.
- **Selftest:** so tư thế đứng với hình gốc (thiếu ≤ 0,5 %, thừa ≤ 1 %, lệch màu ≤ 1 %); Kai 3/4 khớp từng byte với bản chuẩn; tay đủ dài và dày; Hana có váy ở mọi góc.

## 5. Chuyển động (shared/…/engine)

- **Kênh của clip** (`Clip.kt`):
  - `ROT`: xoay trong mặt phẳng ảnh;
  - `PITCH`: đưa ra trước hoặc sau, chiếu theo góc nhìn: rot = −atan2(sin P·sin yaw, cos P); độ dài = √(cos²P + sin²P·sin²yaw);
  - `ROLL`: xoay quanh trục dài của xương; 180° là lật mặt kia, ví dụ lật bàn tay.
- **Lệch pha giữa vai và khuỷu (overlap):** khi dang hay giơ tay, khuỷu duỗi thẳng trước rồi vai mới vung (ease `IN` rồi `BACK`); khi hạ tay, vai dẫn với tay thẳng, khuỷu gập sau cùng. Vai và khuỷu chạy cùng lúc thì cẳng tay gập ngược chiều vung, trông như tay gãy (`DanceClips.PUMP`).
- **Góc nhìn của mỗi bộ cắt:** đo từ vị trí hai mắt so với đầu; ghi đè được bằng `yaw` trong `character.csv`.
- **FootLock** (`Motion.kt`): khoá bàn chân trên đất khi chạm đất; nhìn nghiêng thì trộn IK theo |sin yaw|.
- **AutoLayers:** co giãn lấy gốc ở lòng bàn chân, nên bàn chân không trượt.
- **Mesh** (`SoftMesh.kt`): tay chân trộn trọng số theo khoảng cách dọc xương (dải trộn hẹp ở khớp); thân dùng khoảng cách thẳng.
- **`motionCheck`** (`./gradlew :desktopApp:motionCheck`), với chân đang chạm đất:
  - đầu gối lệch ≤ 0,06 + 0,3·|sin yaw|;
  - bàn chân trượt ≤ 1,5 px;
  - bàn chân nghiêng ≤ 5°.

## 6. Quy tắc Gemini

- Key riêng của StoryRig trong `Storyrig/.env` (gitignored). Không dùng key của dự án khác, không in key ra.
- **Mỗi lượt gọi phải được người dùng duyệt cho đúng việc đó.** Xem kỹ hình đang có và báo lỗi cụ thể trước khi tạo lại. Báo số lượt sau mỗi đợt.
- `tools/gemini_gen.py gen --prompt … --out … --attach … --aspect 16:9 --size 4K`. `STORYRIG_TIER=free` (mặc định) chỉ chờ giữa các lượt và đặt trần mỗi ngày; nó không làm lượt gọi thành miễn phí.
- Gemini trả về JPEG, nên đặt đuôi file `.jpg`.
- **Thử 1K trước (người dùng, 2026-10-18):** mỗi prompt hoặc tờ mẫu mới (hoặc vừa sửa) gọi `--size 1K` ($0,067) để kiểm tra bố cục, đủ món, màu. Bản 1K đạt mới gọi 4K ($0,15), có thể đính kèm thêm bản 1K đạt làm tham chiếu bố cục. Lỗi tờ mẫu làm hỏng 2 lượt 4K ngày 2026-10-18.
- **`NO_IMAGE`:** model hay từ chối ngẫu nhiên với prompt dài kèm 3 ảnh (chỉ tính token đầu vào, khoảng $0,0005). Câu đầu prompt nói rõ "Generate one image"; hỏi người dùng rồi thử lại tối đa 2 lần.

## 7. Tiến độ

Plan đầy đủ: bộ kit 2 tờ (mục 2), lắp rig từ bộ phận rời (mục 4). Làm ở session riêng (`NEXT_SESSION.md`), theo thứ tự dưới đây: làm trọn Kai, người dùng xác nhận, rồi mới sang nhân vật khác.

| Bước | Việc | Trạng thái |
| --- | --- | --- |
| — | Vai và nách khi cắt hình phẳng: mặt trong ống tay sang tay, nắp theo dây cung, khớp vai trượt, nhún vai, vai cứng trong mesh, làm sạch mép thân, `pose_sheet.sh` | xong 2026-10-18 (`e677b7f`) |
| 0 | Giữ app dùng được trong lúc làm kit. Nắp vai lấy hoa văn tay áo: đi dọc hướng sọc tay áo (vuông góc trục tay) tới điểm tay áo gần nhất có màu chính, thay vì chép ngực áo. Sửa lỗi Leo "mảng ngực xoay theo tay". Nếu Leo lệch màu ở tư thế đứng vượt 1 %, loại điểm thuộc nắp vai khỏi phép so màu và ghi lý do. Soi `pose_sheet.sh` trực diện và 3/4, so với `e677b7f` | xong 2026-10-18: lệch màu tư thế đứng Leo trực diện 0,43 %, 3/4 0,24 % (dưới 1 %, không cần loại nắp khỏi phép so); selftest 15/15, `motionCheck` đạt. Còn: vài đốm trắng nhỏ ở góc vai Leo (đầu sọc tay áo kéo vào nắp); tay xa của Bo trực diện tô bóng sẫm từ hình gốc, chờ bộ kit mới |
| 0b | Người dùng: vai vẫn xoay theo tay (viền vai nằm trên mảnh tay). Rà soát mọi khớp × mọi góc × mọi động tác; quy tắc khớp chuẩn (mục 4); bảng thử `tools/joint_sheet.sh` với clip `STRESS`; danh sách lỗi ở mục 8 | xong 2026-10-18 |
| 0c | **Dừng sau mục vai (2026-10-18):** nách vẫn rách, chỉ bộ kit sửa được; các mục code còn lại làm sau khi Kai có bộ kit. Đã xong: quả cầu vai (selftest 15/15, lệch màu tư thế đứng tối đa 0,26 %, `motionCheck` đạt). Còn lại → thứ tự vẽ tay xa → gối theo hình nộm → cổ giày → tóc và dây buộc → váy Hana → mặt sau (quần đùi khi ngồi, đá chân) → cổ tay → nắp tô trơn (cổ, khuỷu, đùi, quai balo trên tay) → kính Bo | chưa |
| 1 | 2 tờ mẫu (`rig_body.png`, `rig_face_hands.png`, kèm `.csv` ô) trong `MannequinSheet.kt` (`mannequin kit <dir>`). 2 prompt (`body_sheet.txt`, `face_hands_sheet.txt`) nói rõ phần phải vẽ dư, 12 miệng, bàn tay đếm số | xong 2026-10-18, **chờ người dùng duyệt**. Tờ thân: hàng A 5 góc, hàng B bộ phận rời trực diện và nghiêng, hàng C bộ phận rời 3/4 (vừa 2 tờ, không cần tờ 3). Cỡ ở 4K: người toàn thân 1411 px, miệng 146–246 px, mắt 180 px, bàn tay 368×448 px, đầu không tóc 494 px (mặt và tay vẽ gấp 2,4 lần cỡ thân, `splitsheet` thu về). Prompt của Kai: `assets/kai/prompts/5_body_sheet.txt`, `6_face_hands_sheet.txt` |
| 2 | `splitsheet` và `sheetcheck` (10 mục ở mục 3), thử trên `turnaround_v1.jpg` | chưa |
| 3 | Gemini cho **Kai**: 2 lượt, 4K, **người dùng duyệt riêng**. Chạy `sheetcheck`, báo bằng lời thường kèm ảnh khoanh lỗi | đang làm. 2026-10-18 tờ thân 2 lần ra hình (`attempts/kit_body_v1.jpg`, `v2`) cộng 3 lượt `NO_IMAGE` (model hay từ chối ngẫu nhiên với prompt dài kèm 3 ảnh; thêm câu đầu "Generate one image" và thử lại thì ra). Hàng 5 góc đẹp và đồng nhất; tay rời có đầu tay áo tròn; chân rời có đầu ống quần tròn và tất vẽ dư; v2 thân trực diện đúng dáng không tay (vai tròn, nách, hông áo đủ). Lỗi do tờ mẫu: đĩa vai sáng trên thân thành mảng da (đã bỏ, thân một khối xám vai tròn); ô chân giống ô tay nên nhóm trực diện ra 2 tay, thiếu chân và giày (ô chân giờ là khối ống quần rộng). Quần rời vẫn có 2 ống (ô quần là khối chữ nhật trông như cả chiếc quần): sửa ở tờ mẫu và prompt chứ không để tool cắt chữa, ô quần giờ là dải ngắn (cạp quần sẫm, mông, đáy cắt phẳng), prompt nói rõ chỉ cao khoảng một phần ba chiếc quần, ống quần thuộc mảnh chân. Chân 3/4 vẫn dính giày. Đo màu v2: da, áo, quần khớp hình gốc; tay xa 3/4 sẫm hẳn (#CC795E, gốc #F2B387) do xám đậm trên tờ mẫu, đã đổi tay chân xa sang xám nhạt. Bản 1K v3 (`attempts/kit_body_v3_1k.jpg`, 1 lượt, sau khi sửa tờ mẫu): đúng bố cục mọi ô; thân rời trực diện và 3/4 không tay áo; tay rời đầu tay áo tròn, tay 3/4 duỗi thẳng; chân trực diện không giày, tất vẽ dư; màu khớp hình gốc và **tay xa cùng màu tay gần** (da #ECAF86 cả tay gần lẫn tay xa, áo #E94E4A, quần #3E7CAF). Còn 3 lỗi: mảnh quần trực diện và 3/4 vẫn là cả chiếc quần có 2 ống và gấu (mảnh nghiêng cũng có gấu); chân nghiêng và chân 3/4 dính giày; giày rời có cổ tất sọc đỏ bên trong. Sửa prompt: mảnh quần gọi là "waistband strip", đáy một đường thẳng, không ống, không gấu, "không phải chiếc quần"; thêm câu chung cho cả 3 chân không giày, 3 mảnh quần là dải ngắn; giày rỗng, không tất. Bản 1K v4 (`attempts/kit_body_v4_1k.jpg`, prompt đã sửa) **tệ hơn v3**: quần rời vẫn 2 ống (lỗi này lặp lại ở cả 4 lần gọi dù tờ mẫu là dải ngắn), thân mất dây balo, tay nghiêng mất tay áo, 2 đĩa da thừa ở ô giày, hàng 3/4 vỡ bố cục (thân có tay áo, 4 tay, thiếu giày). Gọi lại cả tờ cho ra kết quả ngẫu nhiên, sửa prompt không chắc kéo đúng lỗi. **Tiếp (chờ người dùng chọn): dùng v3 làm gốc, gọi kiểu sửa ảnh (đính kèm v3, chỉ yêu cầu sửa 3 lỗi) thử 1K, đạt thì 4K** |
| 4 | `cutparts` cho Kai: lắp rig trực diện và nghiêng từ bộ phận rời (mục 4). Engine đọc thêm: tròng mắt tách (đảo mắt), 12 miệng, bàn tay hoán đổi, tóc tách lớp, góc SIDE 90 và Q3_BACK 135, clip đi ở góc nghiêng | chưa |
| 5 | Kai: góc 3/4, 3/4 sau, sau lưng cắt từ hàng A (dự phòng). Đưa đủ các góc của Kai vào app | chưa |
| 6 | **Người dùng xem Kai trong app và xác nhận ổn hết.** Chưa xác nhận thì không gọi Gemini cho nhân vật khác | chưa |
| 7 | Lần lượt Mia → Leo → Hana → Bo: mỗi nhân vật 2 lượt, người dùng duyệt trước khi gọi, xong và xem trong app rồi mới sang nhân vật sau | chưa |

Mỗi bước: soi `tools/joint_sheet.sh` (cả 11 bộ cắt), selftest, `motionCheck`, rồi mở lại app desktop cho người dùng xem.

Lượt Gemini ngày 2026-10-18: 12 hình paid buổi sáng (11 hình trực diện, 1 tờ thử 5 góc), tối thêm 2 tờ thân bộ kit ra hình và 3 lượt `NO_IMAGE` (chạy `STORYRIG_TIER=free`, người dùng cho nâng trần có chủ ý). Bộ đếm của script ghi 44 (tính cả lượt lỗi). Từ 2026-10-19 dùng `STORYRIG_TIER=free`.

## 8. Rà soát khớp (2026-10-18)

Nguồn: đọc code tool cắt và engine, các ảnh mảnh cắt, và bảng thử `joint_sheet.sh` của cả 11 bộ. Loại lỗi (A–E) xem mục 4. "Đã thấy" là đã thấy trên bảng thử.

| # | Khớp | Lỗi | Loại | Sửa ở tool cắt / engine | Bộ kit |
| --- | --- | --- | --- | --- | --- |
| 1 | Vai, nách | **Vai đã sửa 2026-10-18 (quả cầu vai, mục 4). Nách vẫn rách** sau 3 lần sửa ở tool cắt (Kai chữ V: hông áo dưới nách là mép cắt thẳng có khấc; Mia dang tay: lởm chởm). Hình phẳng không có điểm ảnh của hông áo dưới tay, tool chỉ đoán được: **không vá thêm ở tool cắt, nách do bộ kit vẽ** (thân có nách và hông áo đủ). Trước đó: viền vai xoay theo tay (đã thấy, mọi nhân vật). Góc 3/4 tay xa và góc sau lưng: thân để góc vai vuông. Mảng sẫm mặt trong ống tay xoay theo tay. Xoay người: vai trượt khác nhịp với thân | A, B | Quả cầu vai: mảnh trên xương không xoay, vẽ đè đầu tay, có trong `lateral` của PseudoYaw; khớp vai đồng tâm, không trượt | Thân có vai tròn và nách; tay có đầu tròn |
| 2 | Quai balo (Kai) | **Đã sửa cùng quả cầu vai** (đầu tay bỏ phần nằm trên thân). Một đoạn quai balo nằm trên mảnh tay gần, xoay theo tay (đã thấy, 3/4) | B | Điểm ảnh khác tông áo trên mảnh tay sát đường cắt vai trả về thân | Balo tách lớp |
| 3 | Cổ | Nắp cổ chép bóng cằm; nghiêng đầu lộ khấc ở cổ áo (Leo) | B | Nắp cổ tô màu da trơn | Cổ vẽ dư |
| 4 | Khuỷu (tay áo dài) | Nắp cẳng tay chép sọc, xoay theo (chỉ ở ảnh cứng) | B | Nắp khuỷu theo `sleeveColour` | Tay áo dài liền mảnh |
| 5 | Cổ tay | Tay áo dài: bàn tay đè lên cổ tay áo. Lật bàn tay: đầu tròn cẳng tay thò ra; lật 180° chỉ là mu bàn tay soi gương | A, E | Bàn tay vẽ dưới cổ tay áo dài; đầu tròn co theo độ lật | Lòng và mu bàn tay; cổ tay vẽ dư |
| 6 | Eo | Bóng dưới gấu áo nằm trên quần. Hana: khoang thân trong mesh lộn ngược (thắt lưng bị kéo 50/50) | B | Sửa công thức khoang thân cho váy liền (MeshSpec) | Gấu áo vẽ đè, cạp quần vẽ dư |
| 7 | Hông (quần) | Ảnh cứng: nắp đùi chép túi, nếp | B | Nắp đùi tô trơn | Đầu đùi tròn |
| 8 | Hông (váy Hana) | Mảng váy mờ như bóng ma khi nhấc gối và khi ngồi (đã thấy); vụn da trên mép váy; hông neo `root` không neo `skirt`; đùi trượt dưới váy đứng yên | A, B, D | Không chia điểm ảnh đường cắt hông cho váy; hông neo `skirt`; váy lắc theo đùi | Đùi vẽ dư dưới váy |
| 9 | Gối (quần đùi) | Gối đặt ở gấu quần; Mia đùi 29 px, ống chân 179 px, gập ngay ở gấu quần (đã thấy). DanceClips dùng độ dài chân của Kai cho mọi người | C | Gối theo hình nộm; gấu quần trên đùi che; độ dài chân theo nhân vật | Chân một mảnh có gối |
| 10 | Gối (quần dài, gối trần) | Ảnh cứng: đầu đùi vuông thò ra khi gập | A | Đầu đùi bo tròn | — |
| 11 | Cổ chân | Miệng giày cong, đường cắt thẳng: phần miệng giày nằm lại trên ống chân. Leo: khe da sẫm giữa gấu quần và giày khi xoay cổ chân (đã thấy) | A | Cắt giày theo vùng màu giày; ống chân kéo dài xuống dưới giày | Ống chân vẽ dư trong giày |
| 12 | Tóc | Đầu giữ bản sao tóc (viền đôi khi lắc); dây buộc tóc của Mia đứng yên khi đuôi ngựa lắc | A, B, E | Bản sao co vào trong theo biên độ lắc; dây buộc theo đuôi tóc | Tóc tách lớp, đầu không tóc |
| 13 | Mặt khi xoay người | Kính, ria Bo vẽ chết trên đầu: mắt trượt khỏi kính | E | Kính thành mảnh mặt riêng | Kính, ria tách lớp |
| 14 | Thứ tự vẽ | 3/4: tay xa đưa vào ngực biến mất sau thân (đã thấy, Kai, Mia, Hana). Sau lưng: đùi đè ống chân khi nhấc gối | D | Thứ tự vẽ theo tư thế | — |
| 15 | Mặt sau (Kai) | Ngồi: quần đùi rách vụn; đá chân ra sau: lộ một mảng ống quần (đã thấy) | A, D | Lưới hông và thứ tự vẽ ở góc sau lưng | — |
| 16 | Góc trực diện | Ngồi, cúi chào, đưa tay ra trước: đùi và tay chĩa về người xem co thành mẩu cụt (đã thấy, mọi nhân vật) | E | Clip làm các động tác này ở góc 3/4 hoặc nghiêng | Dáng ngồi nhìn trực diện nếu cảnh cần |
| 17 | Góc nhìn trái | Hình in ngực, tóc mái lệch bị lật | — | Người dùng chấp nhận | — |
| 18 | Xoay người | Balo hiện đột ngột khi đổi sang mặt sau; chưa có lớp nhoè hoặc che lúc đổi góc | D, E | Nhoè hoặc che lúc đổi góc (như demo Bobo) | Balo tách lớp |
| 19 | Hình gốc | Hana: trực diện tay áo dài, 3/4 tay áo ngắn | E | — | Vẽ mọi góc trên cùng tờ; `sheetcheck` so độ dài tay áo |

Động tác có trong kế hoạch nhưng chưa có clip (đi, chạy, cúi chào, ngồi, nhảy, vỗ tay, chỉ, mời, đếm số, cầm đồ, cưỡi ngựa): soi tư thế cực hạn tương ứng trong `STRESS` trước khi viết clip; thêm tư thế vào `STRESS` khi có động tác mới.
