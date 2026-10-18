
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleBrowser extends JFrame {

    private JTextField txtUrl;
    private JComboBox<String> cbMethod;
    private JTextField txtPostData;
    private JButton btnSend;
    private JTextArea txtStatsAndHeaders;
    private JTextArea txtRawHtml;
    private JEditorPane htmlRenderPane;

    public SimpleBrowser() {
        setTitle("Simple Web Browser - Nhóm 1 (RFC 2616 HTTP Client)");
        setSize(950, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // 1. Panel điều khiển phía trên (URL, Method, POST Data)
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        JPanel urlPanel = new JPanel(new BorderLayout(5, 5));
        cbMethod = new JComboBox<>(new String[]{"GET", "POST", "HEAD"});
        txtUrl = new JTextField("http://localhost:8080/index.html");
        btnSend = new JButton("Gửi Request");

        urlPanel.add(cbMethod, BorderLayout.WEST);
        urlPanel.add(txtUrl, BorderLayout.CENTER);
        urlPanel.add(btnSend, BorderLayout.EAST);

        JPanel postPanel = new JPanel(new BorderLayout(5, 5));
        postPanel.add(new JLabel("Dữ liệu POST (ví dụ: user=sinhvien&lop=jit): "), BorderLayout.WEST);
        txtPostData = new JTextField("user=nhuanphat&school=VKU");
        txtPostData.setEnabled(false);
        postPanel.add(txtPostData, BorderLayout.CENTER);

        topPanel.add(urlPanel);
        topPanel.add(postPanel);
        add(topPanel, BorderLayout.NORTH);

        // 2. Khu vực hiển thị kết quả ở giữa (Chia làm 2 phần: Thống kê/Header và Hiển thị Web)
        txtStatsAndHeaders = new JTextArea(9, 80);
        txtStatsAndHeaders.setEditable(false);
        txtStatsAndHeaders.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scrollStats = new JScrollPane(txtStatsAndHeaders);
        scrollStats.setBorder(BorderFactory.createTitledBorder("Thông tin Tài nguyên (HEAD) / Thống kê Thẻ HTML (GET, POST)"));

        // TabbedPane chứa trang Web được Render và mã HTML gốc
        JTabbedPane tabbedPane = new JTabbedPane();

        htmlRenderPane = new JEditorPane();
        htmlRenderPane.setEditable(false);
        htmlRenderPane.setContentType("text/html");
        tabbedPane.addTab("Biểu diễn trang HTML (Rendered View)", new JScrollPane(htmlRenderPane));

        txtRawHtml = new JTextArea();
        txtRawHtml.setEditable(false);
        txtRawHtml.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tabbedPane.addTab("Mã nguồn HTML gốc (Raw HTML)", new JScrollPane(txtRawHtml));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollStats, tabbedPane);
        splitPane.setDividerLocation(200);
        add(splitPane, BorderLayout.CENTER);

        // 3. Xử lý sự kiện
        cbMethod.addActionListener(e -> {
            String method = (String) cbMethod.getSelectedItem();
            txtPostData.setEnabled("POST".equals(method));
        });

        btnSend.addActionListener(e -> executeRequest());
        txtUrl.addActionListener(e -> executeRequest());
    }

    private void executeRequest() {
        String urlStr = txtUrl.getText().trim();
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "http://" + urlStr;
            txtUrl.setText(urlStr);
        }
        String method = (String) cbMethod.getSelectedItem();
        String postData = txtPostData.getText();

        btnSend.setEnabled(false);
        txtStatsAndHeaders.setText("Đang gửi " + method + " request tới: " + urlStr + " ...");

        String finalUrlStr = urlStr;
        new Thread(() -> {
            try {
                URL url = new URL(finalUrlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setRequestProperty("User-Agent", "SimpleJavaBrowser/1.0");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                // Nếu là phương thức POST, gửi dữ liệu kèm theo trong Request Body
                if ("POST".equals(method)) {
                    conn.setDoOutput(true);
                    byte[] outBytes = postData.getBytes(StandardCharsets.UTF_8);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    conn.setRequestProperty("Content-Length", String.valueOf(outBytes.length));
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(outBytes);
                        os.flush();
                    }
                }

                int responseCode = conn.getResponseCode();
                String responseMsg = conn.getResponseMessage();

                if ("HEAD".equals(method)) {
                    // Xử lý phương thức HEAD: Chỉ hiển thị thông tin về tài nguyên (Response Headers)
                    StringBuilder headInfo = new StringBuilder();
                    headInfo.append("=== THÔNG TIN TÀI NGUYÊN (PHƯƠNG THỨC HEAD - RFC 2616) ===\n");
                    headInfo.append("URL yêu cầu   : ").append(finalUrlStr).append("\n");
                    headInfo.append("Trạng thái    : ").append(responseCode).append(" ").append(responseMsg).append("\n");
                    headInfo.append("----------------------------------------------------------\n");

                    for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                        if (entry.getKey() != null) {
                            headInfo.append(String.format("%-20s: %s\n", entry.getKey(), String.join(", ", entry.getValue())));
                        } else {
                            headInfo.append(String.format("%-20s: %s\n", "Status-Line", String.join(", ", entry.getValue())));
                        }
                    }

                    SwingUtilities.invokeLater(() -> {
                        txtStatsAndHeaders.setText(headInfo.toString());
                        txtRawHtml.setText("(Phương thức HEAD không tải phần thân trang HTML)");
                        htmlRenderPane.setText("<html><body><h3>Phương thức HEAD chỉ lấy thông tin Header của tài nguyên.</h3></body></html>");
                        btnSend.setEnabled(true);
                    });

                } else {
                    // Xử lý phương thức GET hoặc POST: Đọc HTML, thống kê thẻ và biểu diễn trang
                    InputStream is = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
                    StringBuilder htmlBuilder = new StringBuilder();
                    if (is != null) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                htmlBuilder.append(line).append("\n");
                            }
                        }
                    }

                    String htmlContent = htmlBuilder.toString();
                    int charLength = htmlContent.length();
                    int byteLength = htmlContent.getBytes(StandardCharsets.UTF_8).length;

                    // Đếm số lượng các thẻ <p>, <div>, <span>, <img> (và <imag>)
                    int countP = countHtmlTag(htmlContent, "p");
                    int countDiv = countHtmlTag(htmlContent, "div");
                    int countSpan = countHtmlTag(htmlContent, "span");
                    int countImg = countHtmlTag(htmlContent, "img") + countHtmlTag(htmlContent, "imag");

                    StringBuilder stats = new StringBuilder();
                    stats.append("=== KẾT QUẢ PHÂN TÍCH TRANG HTML (PHƯƠNG THỨC ").append(method).append(") ===\n");
                    stats.append("Trạng thái HTTP        : ").append(responseCode).append(" ").append(responseMsg).append("\n");
                    stats.append("Chiều dài trang HTML   : ").append(charLength).append(" ký tự (").append(byteLength).append(" bytes)\n");
                    stats.append("----------------------------------------------------------\n");
                    stats.append("• Số lượng thẻ p       : ").append(countP).append("\n");
                    stats.append("• Số lượng thẻ div     : ").append(countDiv).append("\n");
                    stats.append("• Số lượng thẻ span    : ").append(countSpan).append("\n");
                    stats.append("• Số lượng thẻ img     : ").append(countImg).append("\n");

                    SwingUtilities.invokeLater(() -> {
                        txtStatsAndHeaders.setText(stats.toString());
                        txtRawHtml.setText(htmlContent);
                        txtRawHtml.setCaretPosition(0);
                        // Biểu diễn (Render) nội dung HTML
                        htmlRenderPane.setContentType("text/html; charset=UTF-8");
                        htmlRenderPane.setText(htmlContent);
                        htmlRenderPane.setCaretPosition(0);
                        btnSend.setEnabled(true);
                    });
                }
                conn.disconnect();

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    txtStatsAndHeaders.setText("Lỗi khi kết nối: " + ex.getMessage());
                    btnSend.setEnabled(true);
                });
            }
        }).start();
    }

    // Hàm đếm thẻ mở HTML chính xác bằng Regex (không phân biệt chữ hoa/thường, hỗ trợ thẻ có thuộc tính)
    private int countHtmlTag(String html, String tagName) {
        String regex = "<" + tagName + "\\b[^>]*>";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimpleBrowser().setVisible(true));
    }
}