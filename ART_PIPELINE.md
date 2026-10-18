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
| B2 | **Bộ phận rời góc nghiêng** (cho đi và chạy): thân nghiêng, một cánh tay nghiêng có đầu vai tròn, một chân nghiêng có đầu đùi tròn, giày nghiêng. Tay chân phía xa lấy từ bản sao, tô sẫm hơn |
| B3 | **Tóc tách lớp:** tóc phía sau, tóc mái hoặc tóc phía trước; đuôi ngựa hoặc tóc dài riêng nếu có, gốc tóc vẽ dư |

### Tờ 2: mặt và tay (`rig_face_hands.png`, prompt `face_hands_sheet.txt`)
| Hàng | Nội dung |
| --- | --- |
| C1 | **Mắt trực diện:** một mắt vẽ riêng lòng trắng và tròng đen; mắt nhắm hờ, nhắm hẳn (một nét, để chớp), nhắm cười, mở to; mắt kia lấy đối xứng. Lông mày: thường, nhướng, cau, buồn |
| C2 | **12 miệng trực diện** (nhép môi cho phonics): nghỉ hoặc M/B/P, A/I, E, O, U/W, F/V, L/TH, S/T, cười, cười lớn, buồn, ngạc nhiên |
| C3 | **Mặt nghiêng:** mắt nghiêng (mở, nhắm, cười, mở to), lông mày, 12 miệng nghiêng |
| C4 | **Bàn tay phải** (tay trái lấy đối xứng). Trực diện: xoè thả lỏng, nắm, chỉ, giơ ngón cái, xoè lòng bàn tay (vẫy), ngửa (mời), cầm (chữ C), đếm 1–4 (5 là xoè). Nghiêng: thả lỏng, nắm |

**Tuỳ chọn, hỏi người dùng sau:** phụ kiện tách lớp để đung đưa (balo của Kai, kính của Bo). Đồ vật cầm tay thuộc cảnh, không thuộc nhân vật.

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
- **Bảng kiểm tra vai và nách (bắt buộc sau mỗi lần sửa tool cắt, rig hay clip):** `tools/pose_sheet.sh <out.png> [chars]`. Mỗi nhân vật một hàng gồm đứng yên, vẫy tay, dang tay, đang giơ và chữ V, phóng to phần vai. Phải mở ra xem cả bộ trực diện lẫn 3/4 trước khi báo là đã sửa xong. Đã có lần sửa lỗi này lại làm hỏng lại lỗi cũ vì chỉ soi một tư thế.

## 4. Lắp rig

### Chuẩn: lắp từ bộ phận rời (`cutparts`, sẽ làm)
- Đặt từng bộ phận vào đúng chỗ trên người trực diện ở hàng A, bằng cách so viền (silhouette). Tận dụng `AutoSpec` để đo khớp.
- Vai và hông đặt ở tâm đường tròn đầu bộ phận. Khuỷu, gối, cổ tay, cổ chân cắt và đặt nắp như cách cắt hình phẳng.
- Thứ tự vẽ: bắp tay sau thân, cẳng tay và bàn tay trước thân. Mesh tách nhóm bắp tay và nhóm cẳng tay.
- Không có điểm xoay khi buông tay, nên `slideShoulders` tự không trượt. Nhún vai (`SHRUG`) vẫn dùng.
- Selftest so tư thế đứng lắp xong với người trực diện. Bảng vai và nách phải sạch mà không cần nắp hay phần tô thêm.

### Dự phòng: cắt từ hình phẳng (tools/…/AutoSpec.kt, RefCut.kt)
Dùng cho 5 bộ hình hiện có, cho các góc chưa có bộ phận rời (3/4, 3/4 sau, sau lưng), và khi bộ phận Gemini vẽ không khớp. Các quy tắc dưới đây là để đoán phần bị che.


- **Vai:** đường cắt dọc theo hông áo, từ đỉnh nách lên đỉnh vai, vượt ra mỗi đầu `SHOULDER_MARGIN` (6 px).
- **Khớp vai trượt:** khi tay ở góc vẽ hoặc giơ lên, tay xoay quanh giữa đường cắt (ngang nách), nhờ vậy mặt dưới tay vẫn áp vào hông áo. Khi tay buông, tay xoay quanh điểm ở 20 % đường cắt tính từ đỉnh (`SHOULDER_HANG_PIVOT`, ghi ở cột `hangX,hangY` của `bones.csv`), nên đỉnh tay áo không văng ra như độn vai. Engine trượt dần giữa hai điểm theo góc tay (`KaiRig.slideShoulders`), giống xương bả vai. Một điểm xoay cố định không làm được cả hai việc: đặt thấp thì vai phồng khi buông tay, đặt cao thì rách nách khi giơ tay. Khi giơ tay quá góc vẽ, cả cánh tay nhấc lên (nhún vai, `SHRUG` 40 %, đủ ở 100° trên góc vẽ), để tay duỗi thẳng không mọc ra từ nách. Đổi điểm xoay lên cao khi giơ tay đã thử và không dùng, vì mặt dưới tay áo văng ra thành vạt.
- **Mép thân dọc đường cắt vai** (`cleanShoulderSide`): đường hông áo dưới nách được kéo tiếp lên vai bằng màu áo (tối đa 12 px), và điểm ảnh lạc màu trong khoảng ±4 px quanh đường cắt được tô lại. Như vậy chỗ thân lộ ra khi tay giơ không còn khấc hay lấm tấm. Mặt trong ống tay vẫn chuyển sang tay, còn thân giữ một bản màu áo trơn ở dưới.
- **Vai cứng trong mesh:** lưới thân không lấy trọng số theo xương vai, và cánh tay không neo vào thân. Nếu có, khi giơ tay cao (chữ V, khoảng 150°) vải hông áo bị kéo rách thành vạt.
- **Đầu cánh tay:** đĩa cắt theo dây cung từ đầu nách của đường cắt lên mép đĩa: nửa trên che đường vai khi tay buông, mặt dưới tay chạy thẳng từ nách khi giơ. Bán kính trừ `SHOULDER_MARGIN`.
- **Màu nắp vai** (`sleeveColour`): mỗi điểm của nắp đi dọc hướng sọc tay áo (vuông góc trục tay, cả hai phía) tới điểm tay áo gần nhất có màu chính của tay áo, rồi lấy màu đó. Không chép điểm ảnh của hình gốc ở chỗ nắp, vì chỗ đó là ngực áo: sọc ngực sẽ xoay theo tay thành một mảng lệch (lỗi của Leo). Ở tư thế đứng, nắp phủ đầu sọc ngực như một đường may vai; lệch màu với hình gốc vẫn dưới 1 % (Leo trực diện 0,43 %).
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

## 7. Tiến độ

Plan đầy đủ: bộ kit 2 tờ (mục 2), lắp rig từ bộ phận rời (mục 4). Làm ở session riêng (`NEXT_SESSION.md`), theo thứ tự dưới đây: làm trọn Kai, người dùng xác nhận, rồi mới sang nhân vật khác.

| Bước | Việc | Trạng thái |
| --- | --- | --- |
| — | Vai và nách khi cắt hình phẳng: mặt trong ống tay sang tay, nắp theo dây cung, khớp vai trượt, nhún vai, vai cứng trong mesh, làm sạch mép thân, `pose_sheet.sh` | xong 2026-10-18 (`e677b7f`) |
| 0 | Giữ app dùng được trong lúc làm kit. Nắp vai lấy hoa văn tay áo: đi dọc hướng sọc tay áo (vuông góc trục tay) tới điểm tay áo gần nhất có màu chính, thay vì chép ngực áo. Sửa lỗi Leo "mảng ngực xoay theo tay". Nếu Leo lệch màu ở tư thế đứng vượt 1 %, loại điểm thuộc nắp vai khỏi phép so màu và ghi lý do. Soi `pose_sheet.sh` trực diện và 3/4, so với `e677b7f` | xong 2026-10-18: lệch màu tư thế đứng Leo trực diện 0,43 %, 3/4 0,24 % (dưới 1 %, không cần loại nắp khỏi phép so); selftest 15/15, `motionCheck` đạt. Còn: vài đốm trắng nhỏ ở góc vai Leo (đầu sọc tay áo kéo vào nắp); tay xa của Bo trực diện tô bóng sẫm từ hình gốc, chờ bộ kit mới |
| 1 | 2 tờ mẫu (`rig_body.png`, `rig_face_hands.png`, kèm `.csv` ô) trong `MannequinSheet.kt`. 2 prompt (`body_sheet.txt`, `face_hands_sheet.txt`) nói rõ phần phải vẽ dư, 12 miệng, bàn tay đếm số | chưa |
| 2 | `splitsheet` và `sheetcheck` (10 mục ở mục 3), thử trên `turnaround_v1.jpg` | chưa |
| 3 | Gemini cho **Kai**: 2 lượt, 4K, **người dùng duyệt riêng**. Chạy `sheetcheck`, báo bằng lời thường kèm ảnh khoanh lỗi | chờ duyệt |
| 4 | `cutparts` cho Kai: lắp rig trực diện và nghiêng từ bộ phận rời (mục 4). Engine đọc thêm: tròng mắt tách (đảo mắt), 12 miệng, bàn tay hoán đổi, tóc tách lớp, góc SIDE 90 và Q3_BACK 135, clip đi ở góc nghiêng | chưa |
| 5 | Kai: góc 3/4, 3/4 sau, sau lưng cắt từ hàng A (dự phòng). Đưa đủ các góc của Kai vào app | chưa |
| 6 | **Người dùng xem Kai trong app và xác nhận ổn hết.** Chưa xác nhận thì không gọi Gemini cho nhân vật khác | chưa |
| 7 | Lần lượt Mia → Leo → Hana → Bo: mỗi nhân vật 2 lượt, người dùng duyệt trước khi gọi, xong và xem trong app rồi mới sang nhân vật sau | chưa |

Mỗi bước: soi `tools/pose_sheet.sh` (trực diện và 3/4), selftest, `motionCheck`, rồi mở lại app desktop cho người dùng xem.

Lượt Gemini ngày 2026-10-18: 12/20 paid (11 hình trực diện, 1 tờ thử 5 góc). Từ 2026-10-19 dùng `STORYRIG_TIER=free`.
