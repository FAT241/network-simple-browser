
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.StringTokenizer;

public class httpServer implements Runnable {

    static final File WEB_ROOT = new File(".");
    static final String DEFAULT_FILE = "index.html";
    static final String FILE_NOT_FOUND = "404.html";
    static final int PORT = 8080;
    static final boolean verbose = true;
    private Socket connect;

    public httpServer(Socket c) {
        connect = c;
    }

    public static void main(String[] args) {
        try (ServerSocket serverConnect = new ServerSocket(PORT)) {
            System.out.println("Server Started.\nListening for connections on port: " + PORT + " ...\n");
            while (true) {
                httpServer myServer = new httpServer(serverConnect.accept());
                if (verbose) {
                    System.out.println("Connection opened (" + new Date() + ")");
                }
                Thread thread = new Thread(myServer);
                thread.start();
            }
        } catch (IOException e) {
            System.err.println("Server connection error: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        BufferedReader in = null;
        PrintWriter out = null;
        BufferedOutputStream dataOut = null;
        String fileRequested = null;

        try {
            in = new BufferedReader(new InputStreamReader(connect.getInputStream()));
            out = new PrintWriter(connect.getOutputStream());
            dataOut = new BufferedOutputStream(connect.getOutputStream());

            String input = in.readLine();
            if (input == null || input.isEmpty()) return;

            StringTokenizer parse = new StringTokenizer(input);
            String method = parse.nextToken().toUpperCase();
            fileRequested = parse.nextToken().toLowerCase();

            // Đọc các dòng Request Header để lấy Content-Length nếu là POST
            int postContentLength = 0;
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase().startsWith("content-length:")) {
                    postContentLength = Integer.parseInt(headerLine.substring(15).trim());
                }
            }

            // Sửa lại điều kiện kiểm tra phương thức chuẩn RFC 2616
            if (!method.equals("GET") && !method.equals("HEAD") && !method.equals("POST")) {
                if (verbose) {
                    System.out.println("501 Not Implemented: " + method + " method.");
                }
                String errorHtml = "<html><body><h1>501 Not Implemented</h1></body></html>";
                byte[] fileData = errorHtml.getBytes();

                out.println("HTTP/1.1 501 Not Implemented");
                out.println("Server: Java HTTP Server VKU: 1.0");
                out.println("Date: " + new Date());
                out.println("Content-Type: text/html");
                out.println("Content-Length: " + fileData.length);
                out.println(); // Dòng trống ngăn cách Header và Body theo RFC 2616
                out.flush();

                dataOut.write(fileData, 0, fileData.length);
                dataOut.flush();
            } else {
                // Nếu là POST, đọc dữ liệu Client gửi lên
                String postBody = "";
                if (method.equals("POST") && postContentLength > 0) {
                    char[] bodyChars = new char[postContentLength];
                    in.read(bodyChars, 0, postContentLength);
                    postBody = new String(bodyChars);
                    System.out.println("POST Data received: " + postBody);
                }

                if (fileRequested.endsWith("/")) {
                    fileRequested += DEFAULT_FILE;
                }
                if (fileRequested.startsWith("/")) {
                    fileRequested = fileRequested.substring(1);
                }

                File file = new File(WEB_ROOT, fileRequested);
                byte[] fileData;
                String content = getContentType(fileRequested);

                if (file.exists() && !file.isDirectory()) {
                    fileData = readFileData(file, (int) file.length());
                } else {
                    // Nếu chưa tạo file index.html sẵn trong project, tự sinh trang HTML mẫu để dễ test
                    String sampleHtml = "<html><head><title>VKU Lab</title></head><body>"
                            + "<div><h1>Trang Web Mau Test Lab</h1>"
                            + "<p>Doan van 1</p><p>Doan van 2</p>"
                            + "<span>The span 1</span><span>The span 2</span><span>The span 3</span>"
                            + "<img src='logo.png' /><img src='banner.jpg' />"
                            + (method.equals("POST") ? "<p>Du lieu POST nhan duoc: " + postBody + "</p>" : "")
                            + "</div></body></html>";
                    fileData = sampleHtml.getBytes("UTF-8");
                    content = "text/html; charset=UTF-8";
                }

                // Trả về HTTP Response Headers (cả GET, HEAD, POST đều nhận được phần này)
                out.println("HTTP/1.1 200 OK");
                out.println("Server: Java HTTP Server VKU: 1.0");
                out.println("Date: " + new Date());
                out.println("Content-Type: " + content);
                out.println("Content-Length: " + fileData.length);
                out.println(); // Dòng trống bắt buộc theo RFC 2616
                out.flush();

                // Theo RFC 2616 Mục 9.4: HEAD KHÔNG trả về Message Body, chỉ GET và POST mới trả về Body
                if (method.equals("GET") || method.equals("POST")) {
                    dataOut.write(fileData, 0, fileData.length);
                    dataOut.flush();
                }

                if (verbose) {
                    System.out.println("Request " + method + " " + fileRequested + " handled successfully.");
                }
            }
        } catch (IOException ioe) {
            System.err.println("Server Error: " + ioe.getMessage());
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (dataOut != null) dataOut.close();
                if (connect != null) connect.close();
            } catch (Exception e) {
                System.err.println("Error closing stream: " + e.getMessage());
            }
        }
    }

    private byte[] readFileData(File file, int fileLength) throws IOException {
        byte[] fileData = new byte[fileLength];
        try (FileInputStream fileIn = new FileInputStream(file)) {
            fileIn.read(fileData);
        }
        return fileData;
    }

    private String getContentType(String fileRequested) {
        if (fileRequested.endsWith(".htm") || fileRequested.endsWith(".html"))
            return "text/html";
        else
            return "text/plain";
    }
}