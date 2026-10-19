# ART_PIPELINE.md — Hình nhân vật, tool cắt và chuyển động

Spec cho phần nhân vật bằng ảnh (P5 trong `SPIKE.md`): làm hình thế nào, cắt thành mảnh ra sao, engine chuyển động những gì. Cập nhật khi xong mỗi bước ở mục 7.

## 1. Nguyên tắc: thiết kế để rig, ít góc, ít mảnh, kiểm bằng số

Flow tối giản (quyết định 2026-10-19, thay cho bộ kit 2 tờ ở mục 2 cũ, nay ở `legacy/`). Mọi lỗi đã gặp (nách rách, vai phồng, tay xa sẫm, quần 2 ống, tờ vỡ bố cục, 1K không đủ nét, Gemini vẽ lại khác mỗi lần) có chung 3 gốc, và flow xử lý từng gốc:

1. **Dáng hình gốc che mất phần cần có.** Dáng chữ A (tay xuôi 45°) che nách và hông áo, code phải đoán, AI phải vá. → **Vẽ nhân vật ở dáng lộ hết khớp:** tay giơ ra hai bên, 15° dưới ngang vai; chân dạng 10° mỗi bên; bàn tay xòe. Nách, hông áo, đùi trong đều có điểm ảnh thật. Chỉ còn 3 chỗ màu trơn phải tô: cổ dưới cằm, cạp quần dưới gấu áo, đầu tròn ở khớp (`capInto`, `extendUp`, `shoulderBall` đã có).
2. **Yêu cầu quá nhiều so với app tương tác cơ bản.** 5 góc, xoay liên tục, 12 khẩu hình, mặt nghiêng rời, 15 bàn tay, tay chân uốn lưới: mỗi thứ thêm là thêm chỗ sai, tờ nhiều ô thì Gemini vỡ bố cục. → **2 góc (trực diện, 3/4), quay người bằng đổi góc kèm 3 khung ép dẹt; tờ mặt 22 ô** (4 mắt, 3 mày, 9 miệng, 6 bàn tay; đầu trọc và tóc tách lớp code cắt từ hình gốc); **tay chân là mảnh cứng có khớp tròn**, lưới chỉ cho tóc, váy, đuôi; **biên độ khớp giới hạn trong engine** (vai −30…160°, khuỷu 0…130°, hông ±90°, gối 0…120°, cổ ±25°).
3. **Lỗi chỉ thấy bằng mắt, từng lần một.** → **3 lệnh kiểm ra ĐẠT/LỖI kèm số đo và ảnh khoanh đỏ:** `viewcheck`, `facecheck`, `jointcheck`; số đo chuẩn lưu trong `tools/jointcheck_baseline.txt`, tăng là hồi quy.

**Khóa thiết kế:** hình đã đạt 3 lệnh kiểm thì không tạo lại nữa. Gemini vẽ lại cả hình mỗi lần sửa, nên trước khi khóa, mỗi hình mới phải chạy lại toàn bộ lệnh kiểm; sau khi khóa, lỗi sửa ở tool cắt hoặc engine.

Cách này là cách các engine cut-out (Live2D, Spine, Character Animator, app trẻ em) vẫn làm: vẽ ở dáng lộ khớp, 1–2 góc, mảnh vừa đủ, giới hạn biên độ. Tạo hình trên Gemini web, miễn phí; model ảnh qua API không có free tier (mục 6).

| Code làm được | Phải có hình |
| --- | --- |
| Tay chân co duỗi, đi, chạy, ngồi, cúi, nhảy, thở, chỉ, vỗ tay; gập khuỷu và gối bằng mảnh cứng có khớp tròn | 2 góc nhìn (trực diện, 3/4) ở dáng rig |
| Lấy đối xứng: mắt, tay, chân bên kia; góc nhìn trái lấy từ phải; mắt 3/4 co ngang | Mắt mở, nhắm, nhắm cười, mở to; mày thường, nhướn, cau |
| Đầu trọc (xóa mắt, miệng), tóc tách lớp, cổ và cạp quần vẽ dư, đầu tròn ở khớp | 9 miệng (M, AI, E, O, U, FV, cười, buồn, cười to) |
| Quay người: đổi góc + ép dẹt 3 khung | 6 bàn tay (xòe, nắm, chỉ, lòng bàn tay, ngón cái, cầm) |

**Mở rộng** (ghi hướng, làm sau Kai): động vật cùng khung, thêm hình nộm 4 chân, 1–2 góc; đạo cụ một hình trên nền magenta, thứ hình học đơn giản vẽ bằng code; phong cảnh tách lớp, vẽ bằng vector (code hoặc AI → vtracer) nếu đo hiệu năng đạt. **Nâng cao** (chỉ khi cần cận cảnh hay hình in vắt qua khớp): 5 góc, vá vùng khuất bằng mặt nạ (AI chỉ vẽ trong vùng xanh, code ghép lại), ở `legacy/` và lịch sử git (`4ef5374`).

## 2. Hình của một nhân vật: 3 hình trên Gemini web

| Hình | Khổ | Tờ mẫu (`assets/templates/`, `rigtemplate <dir>` trong `Templates.kt`) | Prompt (`assets/templates/prompts/web/`) | Lưu về |
| --- | --- | --- | --- | --- |
| trực diện, dáng rig | 1:1, 1024 | `rig_front.png` + `.csv` khớp | `views.txt` tin 1 (điền `<<BRIEF>>` từ `assets/<char>/brief.txt`) | `assets/<char>/design/view_front.png` |
| 3/4 quay phải, cùng dáng | 1:1, 1024 | `rig_3q.png` + `.csv` | `views.txt` tin 2, cùng cuộc trò chuyện | `design/view_3q.png` |
| mặt và tay | 16:9, 1376×768 | `rig_face.png` + `.csv` 22 ô (mặt, tay vẽ gấp 2 cỡ thân) | `face.txt`, cuộc trò chuyện mới, đính kèm trực diện đã đạt | `design/face.png` |

- Brief: `assets/templates/brief_template.md` có mục **thiết kế dễ rig** (tay áo trên khuỷu, ống quần trên gối, tóc không chạm vai, không hoa văn vắt qua khớp, không gì vắt chéo thân). Rà trước khi tạo.
- 1024 đủ cho app: nhân vật cao khoảng 830 px, app cần 670–950 px. Không cần 4K.
- Lệnh kiểm từng hình: `viewcheck <char> front|3q` (vuông, nền sạch, một cụm, cao như hình nộm, đế trên đường đất, tay giơ như hình nộm, khe nền dưới tay và giữa chân, không hồng, không lỗ, bảng màu; 3/4 còn so chiều cao, đường đất, đũng quần, bảng màu với trực diện) và `facecheck <char>` (16:9, mỗi ô một mảnh, không mảnh lạ, miệng và bàn tay cùng cỡ, màu da bàn tay như thân). Ra `design/viewcheck_<view>.txt/.png`, `design/facecheck.txt/.png`.
- Hình chưa đạt: tin nhắn sửa có sẵn trong `fixes.txt`, dán vào cùng cuộc trò chuyện, rồi kiểm lại từ đầu. Hướng dẫn đầy đủ: `assets/templates/prompts/web/README.md`.

## 3. Quy trình

```
brief → view_front (viewcheck) → view_3q (viewcheck) → face (facecheck) → cutview front, 3q → joint_sheet.sh green (jointcheck = 0) → files/<char>_front, files/<char>
```

`tools/new_character.sh <char> front|3q|face|check-front|check-3q|check-face|cut` in sẵn prompt đã điền, file đính kèm, nơi lưu, và chạy lệnh kiểm hoặc cắt.

- **Cắt:** `cutview <char> front|3q` (đang làm) đo khớp bằng `AutoSpec` với `rig_<view>.png` mới, cắt bằng `cutWith`, ra `tools/build/cut/<char>_<view>/out` và `preview`. Góc 3/4 vào app dưới tên `<char>`, trực diện `<char>_front`.
- **Kiểm khớp bằng số (bắt buộc sau mỗi lần sửa tool cắt, rig hay clip):** `tools/joint_sheet.sh <outDir> <cuts> green`.
  - `greencut` tô xanh #00FF00 mọi điểm ảnh mà tool cắt bịa (hình gốc trong suốt ở đó, hoặc khác màu rõ); `KaiScene` chạy `bg=0` (nền magenta phẳng, không bóng); 16 tư thế cực hạn của clip `STRESS`; `jointcheck` đếm từng tư thế: điểm ảnh xanh lộ ra, lỗ hở (nền kín mỏng trong dáng; vòng kín to như tay chống hông chỉ liệt kê), so với `tools/jointcheck_baseline.txt` (`baseline` để ghi lại sau khi người dùng duyệt). Chưa về 0 chưa báo xong.
  - Trên Kai cũ (cắt từ dáng chữ A) lệnh báo đúng nách: 843 / 875 / 803 điểm xanh ở tư thế dang tay, chữ V, gập khuỷu (`tools/jointcheck_kai_front_old.txt`), không lỗ hở.
  - Không có `green`: bảng xem bằng mắt như cũ, `<cut>.png` 8×2 ô và `<cut>/pose_NN.png`.
- Vẫn chạy selftest (`./gradlew :tools:run --args="selftest <dir>"`, bộ cắt cũ với tờ mẫu `legacy/`) và `./gradlew :desktopApp:motionCheck`.

## 4. Lắp rig

### Quy tắc khớp chuẩn (mọi khớp, cả bộ kit lẫn cắt hình phẳng)
1. **Mảnh che:** ở mỗi khớp, mảnh có mép vẽ (vai áo, cổ áo, gấu áo, gấu quần, cổ tay áo, cổ giày, chân tóc) được vẽ trên và không xoay theo mảnh con.
2. **Mảnh con có đầu tròn:** tâm đầu tròn ở điểm khớp, nằm dưới mảnh che. Đầu tròn tô bằng màu hoặc hoa văn của chính mảnh con, không chép điểm ảnh của mảnh khác (quai balo, sọc ngực, bóng cằm).
3. **Điểm khớp ở khớp xương thật** (theo hình nộm), không theo mép quần áo.
4. **Thứ tự vẽ đổi theo tư thế** khi tay chân đi qua trước hoặc sau thân.
5. **Code không làm được thì đưa vào bộ kit** (lòng bàn tay, tóc tách lớp, kính, dáng ngồi nhìn trực diện…).

Lỗi khớp nào cũng thuộc một trong 5 loại: A mép vẽ nằm trên mảnh xoay; B phần khuất chép từ hình gốc; C điểm khớp sai chỗ; D thứ tự vẽ cố định; E thiếu hình. Danh sách đã rà ở mục 8.

### Cũ: lắp từ bộ phận rời (`cutparts`, bỏ 2026-10-19 cùng bộ kit 2 tờ; giữ để tham khảo)
- Đặt từng bộ phận vào đúng chỗ trên người trực diện ở hàng A, bằng cách so viền (silhouette). Tận dụng `AutoSpec` để đo khớp.
- Vai và hông đặt ở tâm đường tròn đầu bộ phận. Khuỷu, gối, cổ tay, cổ chân cắt và đặt nắp như cách cắt hình phẳng.
- Thứ tự vẽ: bắp tay sau thân, cẳng tay và bàn tay trước thân. Mesh tách nhóm bắp tay và nhóm cẳng tay.
- Không có điểm xoay khi buông tay, nên `slideShoulders` tự không trượt. Nhún vai (`SHRUG`) vẫn dùng.
- Selftest so tư thế đứng lắp xong với người trực diện. Bảng vai và nách phải sạch mà không cần nắp hay phần tô thêm.

### Cắt từ hình phẳng (tools/…/AutoSpec.kt, RefCut.kt): cách chính của flow tối giản
Dùng cho mọi bộ cắt: 5 bộ hình cũ (dáng chữ A, tờ mẫu `legacy/`) và các hình mới ở dáng rig (`cutview`, tờ mẫu `rig_front`, `rig_3q`). Ở dáng rig, nách và hông áo có điểm ảnh thật nên các quy tắc đoán phần bị che dưới đây chỉ còn tác dụng ở cổ, cạp quần và đầu khớp; `greencut` cho thấy đúng những điểm ảnh còn phải bịa.


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
- **Tạo trên Gemini web, miễn phí (từ 2026-10-19):** model tạo ảnh không có free tier qua API (bảng giá ai.google.dev: "Not available"), nên lượt API nào cũng tính tiền, kể cả khi `STORYRIG_TIER=free`, vì chế độ đó chỉ giãn nhịp. Hình tạo trên gemini.google.com bằng prompt dùng chung ở `assets/templates/prompts/web/` (README, `views.txt`, `face.txt` có chỗ `<<BRIEF>>`, `fixes.txt`); đặc điểm nhân vật ở `assets/<char>/brief.txt`. Sửa trong cùng cuộc trò chuyện, mỗi lần một tin, rồi kiểm lại từ đầu. Không dùng API nữa (1024 của web đủ cho app).
- **Thử 1K trước (người dùng, 2026-10-18):** mỗi prompt hoặc tờ mẫu mới (hoặc vừa sửa) gọi `--size 1K` ($0,067) để kiểm tra bố cục, đủ món, màu. Bản 1K đạt mới gọi 4K ($0,15), có thể đính kèm thêm bản 1K đạt làm tham chiếu bố cục. Lỗi tờ mẫu làm hỏng 2 lượt 4K ngày 2026-10-18.
- **`NO_IMAGE`:** model hay từ chối ngẫu nhiên với prompt dài kèm 3 ảnh (chỉ tính token đầu vào, khoảng $0,0005). Câu đầu prompt nói rõ "Generate one image"; hỏi người dùng rồi thử lại tối đa 2 lần.

## 7. Tiến độ

**Đã qua (2026-10-18 → 19, chi tiết trong lịch sử git tới `4ef5374`):** vai và nách khi cắt hình phẳng (quả cầu vai, `joint_sheet.sh`, quy tắc khớp, rà soát mục 8); bộ kit 2 tờ 16:9: 5 lượt API tờ thân (v1–v5, $0,07–0,15 mỗi lượt) và 7 hình trên web (`assets/kai/legacy/gemini/`) đều không dùng được: Gemini vẽ lại khác mỗi lần, quần 2 ống 4/4 lần, tờ 58 ô và 21 ô vỡ bố cục, web chỉ cho 1K. Người dùng chốt làm lại từ đầu theo flow tối giản (mục 1), không dùng hình cũ.

| Bước | Việc | Trạng thái |
| --- | --- | --- |
| 1 | Tờ mẫu dáng rig (`rig_front`, `rig_3q`, `rig_face` kèm `.csv`), prompt web (`views.txt`, `face.txt`, `fixes.txt`, README), `brief_template.md`, brief Kai, `tools/new_character.sh` | xong 2026-10-19 (`95291d6`) |
| 2 | `viewcheck`, `facecheck` (`ViewCheck.kt`): thử trên Kai cũ dáng chữ A (báo đúng tay thấp, không khe nách) và trên tờ vuông vỡ (báo 24 cụm lạ) | xong 2026-10-19 |
| 3 | `jointcheck` (`JointCheck.kt`, `greencut`, `KaiScene bg=0`, `joint_sheet.sh … green`, baseline): chạy trên Kai cũ, báo đúng nách (xanh 843/875/803 ở tư thế 1, 2, 4) | xong 2026-10-19 |
| 4 | Người dùng tạo 3 hình Kai trên web theo `tools/new_character.sh kai front / 3q / face`; mỗi hình qua lệnh kiểm; khóa thiết kế | chưa |
| 5 | `cutview <char> front\|3q`: cắt với tờ mẫu mới từ `design/`; đầu tròn mọi khớp, tô 3 vùng trơn; tay chân mảnh cứng (mesh chỉ tóc, váy); so điểm khớp với `.csv` hình nộm; tờ mặt 22 ô vào engine (mắt, mày, 9 miệng, 6 bàn tay thay cho `face_map.csv`) | chưa |
| 6 | Engine: giới hạn biên độ khớp (`ClipPlayer.limitDeg`, bảng `KaiArt.jointLimitDeg`: vai 165, khuỷu 135, cổ tay 60, hông 95, gối 125, cổ chân 40, đầu 25, thân 35; `motionCheck` đạt); quay người bằng đổi cut + ép dẹt đã có sẵn (`TurnMove`, `--es turn`); còn: PseudoYaw chỉ còn đầu ±15°, STRESS thêm 2 nhịp quay người và đổi miệng, bàn tay (sau khi tờ mặt vào engine) | giới hạn khớp xong 2026-10-19; phần còn lại chưa |
| 7 | Kai mới: `joint_sheet.sh … green` về 0 xanh, 0 lỗ ở 16 tư thế × 2 góc; selftest; `motionCheck`; tờ duyệt tổng; người dùng duyệt rồi mới thay `files/kai`, `files/kai_front`; ghi baseline | chưa |
| 8 | Lần lượt Mia → Leo → Hana → Bo theo cùng flow (brief, 3 hình, kiểm, cắt), mỗi lần một nhân vật | chưa |

Mỗi bước: `joint_sheet.sh … green`, selftest, `motionCheck`, commit tiếng Việt, mở lại app desktop cho người dùng xem.

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
