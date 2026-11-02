# HƯỚNG DẪN DEMO LAB 4: WEB SERVER & WEB CLIENT (BROWSER)
**Học phần**: Lập trình mạng (VKU)  
**Tiêu chuẩn**: Giao thức HTTP RFC 2616, Server đa dịch vụ (.pdf, .mp3, .mp4, .css, .html), Browser có GET, HEAD, POST, Cơ sở dữ liệu Lịch sử (Database), Chức năng Tải file (Download).

---

## 📌 1. CÁC TỆP NGUỒN (KHÔNG DÙNG FILE BAT ĐÓNG GÓI)
- `httpServer.java`: Web Server đa luồng xử lý Socket TCP cổng 8080. Hỗ trợ đầy đủ các phương thức `GET`, `HEAD`, `POST` và stream đa phương tiện (`.pdf`, `.mp3`, `.mp4`, `.css`, `.png`).
- `SimpleBrowser.java`: Trình duyệt Web đồ họa Java Swing hiện đại có CSS Parser, bộ đếm thẻ `<p>`, `<div>`, `<span>`, `<img>`, `<imag>`, nút Tải file (Download) và Cơ sở dữ liệu lịch sử (`browser_history.csv`).
- `index.html`: Cổng thông tin trang chủ với các liên kết dịch vụ đa phương tiện và form POST.
- `style.css`: Tệp định dạng giao diện chuẩn CSS.
- `sample.pdf`: Tệp tài liệu PDF mẫu.
- `sample.mp3`: Tệp âm thanh MP3 mẫu.
- `sample.mp4`: Tệp video MP4 mẫu.

---

## 🚀 2. LỆNH CHẠY TRỰC TIẾP (TERMINAL HOẶC IDE)

### Biên dịch:
```bash
cd "D:\Lập trình mạng\network-simple-browser"
javac -encoding UTF-8 httpServer.java SimpleBrowser.java
```

### Bước 1: Khởi động HTTP Server
```bash
java httpServer
```

### Bước 2: Khởi động SimpleBrowser
```bash
java SimpleBrowser
```

---

## 🎯 3. KỊCH BẢN DEMO ĐẠT ĐIỂM TỐI ĐA

1. **Kiểm thử Phương thức GET**:
   - Nhập URL: `http://localhost:8080/index.html` -> Chọn phương thức `GET` -> Bấm **"Gửi Request"**.
   - Quan sát:
     - Khung trên hiển thị thống kê: Số lượng thẻ `<p>`, `<div>`, `<span>`, `<imag>`.
     - Khung dưới render giao diện HTML có áp dụng đầy đủ stylesheet `style.css`.
2. **Kiểm thử Đa Dịch Vụ Máy Chủ (PDF, MP3, MP4) & Chức năng Download**:
   - Thầy yêu cầu: *"Có hơn 1 dịch vụ của Server (vd: .pdf - .mp3 - .mp4...)"*.
   - Nhập URL: `http://localhost:8080/sample.pdf` -> Bấm **"Gửi Request"**.
     -> Trình duyệt nhận dạng MIME Type `application/pdf`, hiển thị dung lượng bytes và nút màu xanh **"📥 Tải file (Download)"** sáng lên. Bấm nút để lưu file PDF về máy.
   - Nhập URL: `http://localhost:8080/sample.mp3` -> Trình duyệt nhận dạng `audio/mpeg` và hỗ trợ tải file.
   - Nhập URL: `http://localhost:8080/sample.mp4` -> Trình duyệt nhận dạng `video/mp4` và hỗ trợ tải file.
3. **Kiểm thử Phương thức HEAD**:
   - Chọn phương thức `HEAD` -> Bấm **"Gửi Request"**.
   - Trình duyệt chỉ lấy về Headers của Server (Server, Content-Type, Content-Length, Date...) mà không tải body, đúng chuẩn RFC 2616.
4. **Kiểm thử Phương thức POST**:
   - Chọn `POST` -> Nhập dữ liệu vào ô POST: `message=ChaoThayLTM` -> Bấm **"Gửi Request"**.
   - Server đọc độ dài Content-Length, đọc dữ liệu từ body và trả về trang HTML thông báo kết quả.
5. **Kiểm thử Cơ sở Dữ liệu Lịch sử (Database)**:
   - Bấm nút **"🗄️ CSDL"** trên thanh công cụ.
   - Cửa sổ bảng CSDL hiện ra lưu trữ toàn bộ các lượt truy cập với thời gian, phương thức, mã HTTP, URL, dung lượng. Có thể bấm chọn dòng để mở lại URL đó.
