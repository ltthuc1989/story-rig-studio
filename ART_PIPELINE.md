# ART_PIPELINE.md — Hình nhân vật, tool cắt và chuyển động

Spec cho phần nhân vật bằng ảnh (P5 trong `SPIKE.md`): làm hình thế nào, cắt thành mảnh ra sao, engine chuyển động những gì. Cập nhật khi xong mỗi bước ở mục 7.

## 1. Nguyên tắc: một bộ kit cho mỗi nhân vật, chuyển động bằng code

Làm theo cách của các xưởng làm hoạt hình cắt mảnh (Spine, Rive, Moho, Toon Boom Harmony):

1. **Các góc toàn thân (turnaround):** mỗi góc là một rig riêng. Khi xoay, đổi góc trong 2–3 khung, có che hoặc nhoè (như demo Bobo). Cắt mảnh không xoay liên tục 360° được.
2. **Thư viện hoán đổi (swap)** cho những thứ đổi hình dạng: khẩu hình miệng, mắt, lông mày, bàn tay (xoè, nắm, chỉ…).
3. **Mọi chuyển động bằng xương và mesh:** đi, chạy, vẫy, cúi chào, nhảy, ngồi, theo nguyên tắc hoạt hình (nhanh dần chậm dần, lấy đà, chuyển động nối tiếp, co giãn, khoá bàn chân).

**Không tạo hình cho từng động tác.** Mỗi hình Gemini lệch nhau một chút, ghép theo khung sẽ rung, chi phí lại nhân theo số động tác.

| Code làm được | Phải có hình |
| --- | --- |
| Tay chân co duỗi, đi, chạy, ngồi, cúi, nhảy, thở | Mặt sau, mặt nghiêng (gáy, tóc sau, mũi nghiêng) |
| Co theo chiều sâu (`pitch`), lật bàn tay (`roll`) | Hình dạng bàn tay (nắm, chỉ…) |
| Lấy đối xứng: mắt, tay, chân phía bên kia; góc nhìn trái lấy từ phải | Khẩu hình miệng, mắt nhắm hoặc cười |
| Mặt 3/4 suy từ mặt trực diện (co ngang ~0,85) | |

## 2. Tờ "character constructor" (một lượt Gemini mỗi nhân vật)

- **Cỡ:** 16:9, 4K (khoảng 5504×3072), nền magenta #FF00FF, không chữ, không bóng đổ.
- **Dải trên (~58 % chiều cao):** 5 góc toàn thân dáng chữ A: trực diện (0°), 3/4 (45°), nghiêng (90°), 3/4 sau (135°), sau lưng (180°). Tất cả quay về phía bên phải ảnh, cùng chiều cao, cùng đường đất. Ở góc nghiêng, tay gần đưa nhẹ ra trước để có khoảng trống với thân.
- **Dải dưới:**
  - **Bộ mặt trực diện:** 1 mắt (mở, nhắm hờ, cười, ngạc nhiên); lông mày (thường, nhướng, cau, buồn); 7 miệng (cười khép M/B/P, E, A, O, S, cười lớn, buồn).
  - **Bộ mặt nghiêng:** mắt nghiêng (4 kiểu), 1 lông mày, 7 miệng nghiêng.
  - **7 bàn tay phải:** xoè thả lỏng, nắm, chỉ, giơ ngón cái, xoè lòng bàn tay (vẫy), ngửa (mời), cầm (chữ C). Tay trái lấy đối xứng.
- **Tờ mẫu hình nộm** (Gemini chép bố cục): `./gradlew :tools:run --args="mannequin sheet assets/templates/rig_sheet.png"`. Toạ độ từng ô nằm trong file `.csv` cùng tên. Hiện có bản 5 góc (21:9); bản constructor đầy đủ là bước 1 ở mục 7.
- **Prompt:** `assets/templates/prompts/turnaround_sheet.txt` (5 góc). Bản đầy đủ sẽ là `constructor_sheet.txt`. Đính kèm `ref.png` (3/4 đã duyệt), `view_front.png` (trực diện đã duyệt) và tờ mẫu.

### Chọn cỡ ảnh

Giá gemini-3.1-flash-image (không có 3K):

| Mức | Cỡ tờ 16:9 | Giá/tờ | Người toàn thân cao | Miệng rộng |
| --- | --- | --- | --- | --- |
| 1K | 1376×768 | $0,067 | ~400 px | ~30 px |
| 2K | 2752×1536 | $0,101 | ~800 px | ~65 px |
| 4K | 5504×3072 | $0,151 | ~1550 px | ~130 px |

Nhân vật cận cảnh cao khoảng 650–860 px trên điện thoại (sân khấu 1920×1080) và 1200 px trở lên trên iPad. Vì vậy chọn **4K**: chỉ đắt hơn 2K $0,05 mỗi nhân vật, đủ cho iPad và cận mặt, cắt ngón tay chính xác hơn. Sau khi tách, tool thu nhỏ về cỡ app cần nên bộ nhớ trong app không tăng.

## 3. Quy trình

```
tờ mẫu → Gemini (1 lượt, người dùng duyệt) → splitsheet → sheetcheck → cutauto từng góc → selftest → composeResources/files/<tên>_<góc>
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
  8. góc nghiêng chỉ có 1 mắt, góc sau lưng không có mặt.
- **Cắt:** `./gradlew :tools:run --args="cutauto <geminiDir> <outDir> <previewDir>"`. Kiểm tra: `./gradlew :tools:run --args="selftest <dir>"`.

## 4. Quy tắc cắt (tools/…/AutoSpec.kt, RefCut.kt)

- **Vai:** đường cắt dọc theo hông áo, từ đỉnh nách lên đỉnh vai, vượt ra mỗi đầu `SHOULDER_MARGIN` (6 px). Khớp vai nằm giữa đường cắt.
- **Đầu cánh tay:** đĩa cắt theo dây cung từ đầu nách của đường cắt lên mép đĩa: nửa trên che đường vai khi tay buông, mặt dưới tay chạy thẳng từ nách khi giơ. Bán kính trừ `SHOULDER_MARGIN`.
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

| Bước | Việc | Trạng thái |
| --- | --- | --- |
| 0 | Sửa nách rách khi giơ tay: mặt trong ống tay và đầu viền tay áo nằm lại trên thân được chuyển sang tay (`innerSleeveToArm`); đầu cánh tay cắt theo dây cung từ nách lên mép đĩa, không còn góc bậc | xong 2026-10-18 |
| 1 | Tờ mẫu constructor 16:9 + prompt `constructor_sheet.txt` | chưa |
| 2 | `splitsheet`, thử trên `assets/kai/gemini/turnaround_v1.jpg` | chưa |
| 2b | `sheetcheck` tự kiểm tờ | chưa |
| 3 | Cắt góc nghiêng, 3/4 sau; selftest các góc mới | chưa |
| 4 | Engine: thêm góc SIDE 90 và Q3_BACK 135, xoay qua đủ vòng; bàn tay hoán đổi; mặt 3/4 suy từ trực diện; clip đi ở góc nghiêng | chưa |
| 5 | Gemini: 1 tờ cho Kai (4K), chạy `sheetcheck`, rồi 4 nhân vật còn lại (mỗi đợt người dùng duyệt) | chờ duyệt |

Đã có: tờ thử 5 góc của Kai, `assets/kai/gemini/turnaround_v1.jpg` (6336×2688, 1 lượt, 2026-10-18). Nhân vật đồng nhất giữa các góc; ở góc nghiêng tay buông sát thân.
