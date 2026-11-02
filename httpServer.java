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
            System.out.println("=== HTTP SERVER ĐA DỊCH VỤ (RFC 2616) ĐÃ KHỞI ĐỘNG ===");
            System.out.println("Đang lắng nghe tại cổng: " + PORT);
            System.out.println("Hỗ trợ các định dạng: .html, .css, .pdf, .mp3, .mp4, .png, .jpg, .json");
            System.out.println("Truy cập thử nghiệm: http://localhost:" + PORT + "/index.html\n");

            while (true) {
                Socket clientSocket = serverConnect.accept();
                new Thread(new httpServer(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Lỗi máy chủ HTTP: " + e.getMessage());
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

            // Nếu là POST, đọc nội dung body
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
            } else if ("POST".equals(method)) {
                String postResponseHtml = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>POST Result</title><link rel='stylesheet' href='style.css'></head><body>"
                        + "<div class='header'><h1>Kết quả Xử lý Yêu cầu POST từ Client</h1></div>"
                        + "<div class='card'>"
                        + "<h2>Dữ liệu đã nhận thành công từ Body:</h2>"
                        + "<p><span>" + postData + "</span></p>"
                        + "<p><a href='index.html'>⬅ Quay lại trang chủ</a></p>"
                        + "</div></body></html>";
                bodyBytes = postResponseHtml.getBytes(StandardCharsets.UTF_8);
                contentType = "text/html; charset=UTF-8";
            } else {
                // File 404
                String notFoundHtml = "<!DOCTYPE html><html><head><meta charset='UTF-8'><title>404 Not Found</title></head><body>"
                        + "<h1>404 File Not Found</h1><p>Tài nguyên " + fileRequested + " không tồn tại trên máy chủ.</p></body></html>";
                bodyBytes = notFoundHtml.getBytes(StandardCharsets.UTF_8);
                sendHttpResponse(rawOut, "404 Not Found", "text/html; charset=UTF-8", bodyBytes, !method.equals("HEAD"));
                return;
            }

            // Với HEAD: chỉ gửi Header. Với GET và POST: gửi cả Header + Body
            boolean includeBody = method.equals("GET") || method.equals("POST");
            sendHttpResponse(rawOut, "200 OK", contentType, bodyBytes, includeBody);

        } catch (Exception e) {
            System.err.println("Lỗi xử lý kết nối: " + e.getMessage());
        } finally {
            try {
                connect.close();
            } catch (IOException ignored) {}
        }
    }

    private void sendHttpResponse(OutputStream out, String status, String contentType, byte[] body, boolean includeBody) throws IOException {
        String headers = "HTTP/1.1 " + status + "\r\n"
                + "Server: VKU-Java-MultiService-HTTP-Server/3.0\r\n"
                + "Date: " + new Date() + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Accept-Ranges: bytes\r\n"
                + "Connection: close\r\n"
                + "\r\n";

        out.write(headers.getBytes(StandardCharsets.UTF_8));
        if (includeBody) {
            out.write(body);
        }
        out.flush();
    }

    private String getContentType(String fileRequested) {
        String lower = fileRequested.toLowerCase();
        if (lower.endsWith(".htm") || lower.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        return "text/plain; charset=UTF-8";
    }
}
