# Khung nhân vật vector (vẽ cho xương, như Ben)

Mỗi nhân vật là một thư mục `shared/src/commonMain/composeResources/files/<char>/` gồm 4 file dữ liệu. Code vẽ dùng chung (KaiArt.kt: `KaiAssets`, `KaiRig`). Mẫu: `kai`.

| File | Nội dung |
|---|---|
| `bones.csv` | Bộ xương chuẩn: `root, torso, head, hair, shoulder_*, elbow_*, wrist_*, hip_*, knee_*, ankle_*` (near = bên trái người xem). Chốt khớp tính bằng px trong khung 1024, đế giày ở khoảng y 960. Tên xương giữ nguyên, để mọi clip (DanceClips) chạy được. |
| `shapes.csv` | `part,bone,drawn,colour,alpha,stroke,d`: mỗi dòng một hình. `d` là path SVG trong toạ độ khung; `stroke` 0 là tô kín, > 0 là nét tròn rộng bấy nhiêu px. Thứ tự dòng là thứ tự vẽ. |
| `hoses.csv` | `limb,upper,lower,colour,t0,t1,width,cap`: tay chân là ống, `t` 0–1 trên xương trên, 1–2 trên xương dưới. Dòng sau vẽ đè dòng trước (da trước, quần áo sau). |
| `character.csv` | `heightScale`, `view` (3q/front), `yaw`. |

## Thứ tự mảnh (3/4)

1. `backpack` (nếu có)
2. dòng đánh dấu `arm_upper_far` (`d` để trống: chỗ đặt ống tay xa), rồi `hand_far`
3. dòng đánh dấu `thigh_far`, `shoe_far`
4. dòng đánh dấu `thigh_near`, `shoe_near`
5. `pelvis` (cạp quần)
6. `neck`, `torso` (áo, kèm vòng tròn vai xa)
7. `hair_back`, `head` (tai, mặt, bóng dưới mái, má, mũi)
8. mắt `eye_near_*` và `eye_far_*`, mày `brow_*`, miệng `mouth_*`
9. `hair`
10. dòng đánh dấu `arm_upper_near`, `shoulder_near` (vòng tròn vai gần), `hand_near`

## Biểu cảm

- **Mắt:** `eye_<side>_open` (drawn=1), `_half`, `_happy`, `_wide` (drawn=0).
- **Miệng:** `mouth_smile` (drawn=1), `mouth_e`, `mouth_a`, `mouth_o`, `mouth_s`, `mouth_laugh` (drawn=0).

`KaiRig.face(mouth, eye)` bật đúng một biến thể.

## Quy ước để không có lỗi ở khớp

- **Tay:** ống da tròn đầu; tay áo là ống đầu bằng, bắt đầu ở vai (t = 0). Một **vòng tròn màu áo ở chốt vai** vẽ đè lên đầu ống, nên vai luôn tròn dù tay giơ hay buông.
- **Chân:** ống da tròn đầu; quần là ống đầu bằng, bắt đầu từ t = −0,3, giấu dưới `pelvis`. Gấu quần là một đoạn rộng hơn, cũng đầu bằng. Không dùng đầu tròn cho quần áo, vì đầu tròn áp cho cả 2 đầu nét vẽ.
- Bàn tay và giày là hình cứng ở cổ tay, cổ chân, và che kín đầu ống.
- **Bóng:** mỗi khối thêm một mảng sẫm hơn khoảng 15%, ở phía xa. Tay chân phía xa dùng màu sẫm hơn.

## Kiểm

- `tools/joint_sheet.sh <out> <char>`: xem hết 16 tư thế khó.
- `./gradlew :desktopApp:motionCheck`.
- Mở app `scene=kai char=<char>`, và `dance=walk` để xem đi bộ.
