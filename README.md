# BCKD 4737 – bản Android (APK)

Ứng dụng bọc file `BCKD_4737.html` bằng WebView. Chạy Android 10 (API 29) trở lên. Icon là ảnh 4737.jpg.

## Cách lấy file APK (chọn 1)

### A. GitHub Actions (không cần cài gì)
1. Tạo repo mới trên GitHub, upload toàn bộ nội dung thư mục này (kể cả thư mục `.github`).
2. Vào tab **Actions** → chạy workflow **Build APK** (tự chạy khi push).
3. Xong, tải artifact **BCKD4737-apk** → giải nén ra `app-release.apk` → copy vào điện thoại và cài.

### B. Android Studio
1. Mở thư mục này bằng Android Studio (bản mới, JDK 17), chờ Gradle sync.
2. Menu **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. APK nằm ở `app/build/outputs/apk/release/`.

(Dòng lệnh: cài Gradle 8.7 + Android SDK 34 rồi chạy `gradle assembleRelease`.)

## Ghi chú
- Cài từ file APK: bật "Cài ứng dụng không rõ nguồn" cho trình quản lý file đang dùng.
- APK được ký bằng khóa debug, đủ để cài trực tiếp. Muốn đưa lên Google Play cần keystore riêng.
- Nút chọn file Excel hoạt động (trình chọn file của hệ thống).
- Lưu ảnh/HTML: ảnh vào `Pictures/BCKD4737`, file khác vào `Download/BCKD4737`.
- Link "Click để lấy data" mở bằng trình duyệt ngoài.
- Thư viện JSZip đã nhúng sẵn trong app. Font Fahkwang vẫn tải từ Google Fonts nên cần mạng, không có mạng sẽ dùng font hệ thống.
- Đổi tên app: `app/src/main/res/values/strings.xml`. Đổi nội dung: thay `app/src/main/assets/index.html` (nhớ giữ dòng `jszip.min.js`).
