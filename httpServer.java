import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.StringTokenizer;

public class httpServer implements Runnable {

    static final File WEB_ROOT = new File(".");
    static final String DEFAULT_FILE = "index.html";
    static final int PORT = 8080;
    private final Socket connect;

    public httpServer(Socket c) {
        this.connect = c;
    }

    public static void main(String[] args) {
        try (ServerSocket serverConnect = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))) {
            System.out.println("=== HTTP SERVER (RFC 2616) DA KHOI DONG ===");
            System.out.println("Dang lang nghe tai cong: " + PORT);
            System.out.println("Hay mo SimpleBrowser va truy cap: http://localhost:8080/index.html\n");

            while (true) {
                Socket clientSocket = serverConnect.accept();
                new Thread(new httpServer(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server connection error: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try (InputStream rawIn = connect.getInputStream();
             OutputStream rawOut = connect.getOutputStream()) {

            connect.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(rawIn, StandardCharsets.UTF_8));

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;
            System.out.println("[Request]: " + requestLine);

            StringTokenizer tokenizer = new StringTokenizer(requestLine);
            String method = tokenizer.nextToken().toUpperCase();
            String fileRequested = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : "/";

            // Đọc toàn bộ HTTP Request Headers
            int contentLength = 0;
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(headerLine.substring(15).trim());
                }
            }

            // Nếu là POST, đọc chính xác số ký tự có sẵn trong buffer
            StringBuilder postData = new StringBuilder();
            if ("POST".equals(method) && contentLength > 0) {
                char[] buf = new char[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int r = in.read(buf, totalRead, contentLength - totalRead);
                    if (r == -1) break;
                    totalRead += r;
                }
                postData.append(buf, 0, totalRead);
                System.out.println("[POST Body]: " + postData);
            }

            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
                byte[] errBytes = "<html><body><h1>501 Not Implemented</h1></body></html>".getBytes(StandardCharsets.UTF_8);
                sendHttpResponse(rawOut, "501 Not Implemented", "text/html; charset=UTF-8", errBytes, true);
                return;
            }

            if (fileRequested.endsWith("/")) {
                fileRequested += DEFAULT_FILE;
            }
            if (fileRequested.startsWith("/")) {
                fileRequested = fileRequested.substring(1);
            }

            File file = new File(WEB_ROOT, fileRequested);
            byte[] bodyBytes;
            String contentType = getContentType(fileRequested);

            if (file.exists() && !file.isDirectory() && !"POST".equals(method)) {
                bodyBytes = Files.readAllBytes(file.toPath());
            } else {
                // Trang HTML mẫu có giao diện rõ ràng và đầy đủ các thẻ <p>, <div>, <span>, <img>, <imag> để test
                String demoHtml = "<!DOCTYPE html><html><head><title>VKU Network Lab - Nhom 1</title></head><body>"
                        + "<div class='card'>"
                        + "<h1>Chào mừng đến với Web Server Nhóm 1 (RFC 2616)</h1>"
                        + "<p>Đây là đoạn văn thứ nhất dùng thẻ <span>&lt;p&gt;</span> và thẻ <span>&lt;span&gt;</span> để kiểm thử bộ đếm của Browser.</p>"
                        + "<p>Đây là đoạn văn thứ hai: Phương thức bạn vừa gọi là <span>" + method + "</span>.</p>"
                        + "</div>"
                        + ("POST".equals(method)
                        ? "<div class='card'><h2>Kết quả xử lý dữ liệu POST từ Client:</h2>"
                        + "<p>Server đã nhận thành công chuỗi dữ liệu: <span>" + postData + "</span></p></div>"
                        : "")
                        + "<div class='card'>"
                        + "<h2>Danh sách Hình ảnh & Liên kết kiểm thử:</h2>"
                        + "<p>Thẻ ảnh chuẩn HTML: <img src='vku_logo.png' /></p>"
                        + "<p>Thẻ ảnh theo đề bài: <imag src='network_diagram.jpg' /></p>"
                        + "<p>Bấm thử vào liên kết: <a href='http://example.com'>Chuyển sang trang Example.com</a></p>"
                        + "</div>"
                        + "</body></html>";
                bodyBytes = demoHtml.getBytes(StandardCharsets.UTF_8);
                contentType = "text/html; charset=UTF-8";
            }

            // Với HEAD: chỉ gửi Header (includeBody = false). Với GET và POST: gửi cả Header + Body (includeBody = true)
            boolean includeBody = method.equals("GET") || method.equals("POST");
            sendHttpResponse(rawOut, "200 OK", contentType, bodyBytes, includeBody);

        } catch (Exception e) {
            System.err.println("Lỗi xử lý kết nối: " + e.getMessage());
        } finally {
            try {
                connect.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void sendHttpResponse(OutputStream out, String status, String contentType, byte[] body, boolean includeBody) throws IOException {
        String headers = "HTTP/1.1 " + status + "\r\n"
                + "Server: VKU-Java-HTTP-Server/2.0\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"; // Dòng trống bắt buộc ngăn cách Header và Body theo RFC 2616

        out.write(headers.getBytes(StandardCharsets.UTF_8));
        if (includeBody) {
            out.write(body);
        }
        out.flush();
    }

    private String getContentType(String fileRequested) {
        if (fileRequested.endsWith(".htm") || fileRequested.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }
        return "text/plain; charset=UTF-8";
    }
}