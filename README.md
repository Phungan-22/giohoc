# Giờ Học – app Android (bản 0.1)

App khóa các ứng dụng gây xao nhãng trong giờ học theo thời khóa biểu. Hết tiết, học sinh làm câu hỏi ôn tập rồi mới được mở máy.

## Bản 0.1 làm được gì

**Học sinh**
- Vào lớp bằng mã lớp do giáo viên gửi (hoặc dùng lớp mẫu 10A1 để thử).
- Trong tiết có bật khóa: mọi app có biểu tượng trên màn hình chính bị khóa, trừ:
  - Gọi điện, tin nhắn, máy tính, màn hình chính
  - App giáo viên cho phép theo môn
- Mở app bị khóa thì màn hình "Đang trong giờ học" hiện đè lên.
- Hết tiết có câu hỏi: máy khóa đến khi làm xong câu hỏi (2 phút, hết giờ tự nộp).
  - Làm xong: máy mở đến tiết sau.
  - Nếu lớp tắt giải lao: máy khóa tiếp đến tiết sau.
- Mở khẩn 5 phút, giới hạn số lần mỗi tuần.
- Tự khóa khi học ở nhà theo nhóm: Mạng xã hội, Video, Game, hoặc tất cả. Đã bắt đầu thì không dừng được giữa chừng.
- Xem kết quả câu hỏi và gửi kết quả cho thầy cô qua Zalo.
- Tự chạy lại khi khởi động máy.

**Giáo viên**
- Soạn thời khóa biểu theo thứ, bật/tắt khóa từng tiết.
- Chọn app được mở theo môn, chọn từ danh sách app trên máy hoặc nhập tên gói.
- Soạn câu hỏi sau tiết theo môn.
- Đặt số lần Mở khẩn mỗi tuần và bật/tắt giải lao.
- Tạo mã lớp (chữ bắt đầu bằng `GH1-`) để gửi vào nhóm Zalo.

**Chưa có (bản sau, cần máy chủ như Firebase)**
- Giáo viên xem trạng thái xanh/đỏ của cả lớp theo thời gian thực.
- Báo cho phụ huynh khi học sinh tắt quyền hoặc dùng Mở khẩn.
- Lấy giờ từ máy chủ để chống đổi giờ trên máy.
- Quét mã QR (bản này dán mã chữ).

## Cách khóa hoạt động

- Mỗi giây, app xem app nào đang mở qua `UsageStatsManager`, cần quyền "Truy cập dữ liệu sử dụng".
- Nếu app đó không được phép thì mở màn hình chặn, cần quyền "Hiển thị trên ứng dụng khác".
- App không dùng Accessibility Service, không đọc nội dung màn hình, tin nhắn hay ảnh.

Giới hạn: học sinh vào Cài đặt tắt quyền thì app không khóa được nữa. Trong giờ khóa, app Cài đặt cũng bị khóa nên khó tắt hơn, nhưng ngoài giờ thì tắt được. Bản sau sẽ báo cho giáo viên và phụ huynh khi việc này xảy ra.

## Build bằng Android Studio (máy tính)

1. Cài Android Studio (miễn phí): https://developer.android.com/studio
2. **File → Open**, chọn thư mục `GioHoc`. Đợi Gradle tải thư viện, lần đầu mất vài phút.
3. Nếu Android Studio đề nghị nâng cấp Gradle/AGP, có thể đồng ý.
4. Trên điện thoại Android, bật **Tùy chọn nhà phát triển** (bấm 7 lần vào "Số hiệu bản tạo"), rồi bật **Gỡ lỗi USB**.
5. Cắm cáp, bấm nút ▶ Run. App cài thẳng vào điện thoại.

Muốn có file APK để gửi bạn bè: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. File nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

## Build không cần máy mạnh (GitHub Actions)

1. Tạo repo mới trên GitHub và đẩy toàn bộ thư mục này lên.
2. Vào tab **Actions**, chờ job "Build APK" chạy xong (khoảng 5 phút).
3. Mở lần chạy đó, tải `GioHoc-debug-apk` ở mục Artifacts.

## Thử nhanh

1. Mở app, chọn **Học sinh**, nhập tên, bấm **Dùng lớp mẫu 10A1 để thử**.
2. Cấp 2 quyền theo hướng dẫn trên màn hình.
3. Lớp mẫu có tiết từ 7:00 đến 11:20, thứ Hai đến thứ Sáu. Muốn thử ngay:
   - Chọn **Giáo viên**, thêm một tiết bắt đầu vài phút tới.
   - Tạo mã lớp, sao chép.
   - Chuyển sang **Học sinh** → **Nhập mã lớp mới** → dán mã.
4. Hoặc thử **Tự khóa khi học ở nhà** với nhóm "Tất cả app": khóa ngay lập tức.

## Đưa lên Google Play

- Tài khoản nhà phát triển: 25 USD, trả một lần.
- Tài khoản cá nhân mới phải chạy thử kín với ít nhất 12 người trong 14 ngày liên tục, sau đó mới xin phát hành công khai.
- `targetSdk` cần nâng theo yêu cầu hiện hành của Google Play (Android Studio sẽ nhắc).
- Khi nộp phải khai báo:
  - Lý do dùng quyền dữ liệu sử dụng và foreground service loại `specialUse`
  - Chính sách quyền riêng tư (cần một trang web)
