import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleBrowser extends JFrame {

    private static final String HISTORY_DB_FILE = "browser_history.csv";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private JTextField txtUrl;
    private JComboBox<String> cbMethod;
    private JTextField txtPostData;
    private JButton btnSend;
    private JButton btnOpenExternal;
    private JButton btnDownload;
    private JButton btnDbManager;
    private JTextArea txtStatsAndHeaders;
    private JTextArea txtRawHtml;
    private JEditorPane htmlRenderPane;

    private String lastRawHtml = "";
    private String lastUrl = "";
    private byte[] lastResponseBody = null;
    private String lastContentType = "";

    private static final int SERVER_PORT = 8080;
    private JButton btnBack, btnForward, btnHistory, btnScan;
    private final List<HistoryEntry> navStack = new ArrayList<>();
    private int navIndex = -1;
    private final List<HistoryEntry> visitLog = new ArrayList<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static class HistoryEntry {
        public final String time;
        public final String method;
        public final String url;
        public final String postData;
        public final int statusCode;
        public final long contentLength;

        public HistoryEntry(String time, String method, String url, String postData, int statusCode, long contentLength) {
            this.time = time;
            this.method = method;
            this.url = url;
            this.postData = postData;
            this.statusCode = statusCode;
            this.contentLength = contentLength;
        }

        public boolean sameRequest(HistoryEntry o) {
            return method.equals(o.method) && url.equals(o.url) && postData.equals(o.postData);
        }

        @Override
        public String toString() {
            return "[" + time + "] " + method + "  " + url + " (HTTP " + statusCode + ", " + contentLength + "B)";
        }
    }

    public SimpleBrowser() {
        setTitle("VKU Simple Web Browser (Hỗ trợ GET / HEAD / POST / Download & Database Lịch sử)");
        setSize(1120, 780);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        loadHistoryDatabase();

        // 1. Top Panel
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        JPanel urlPanel = new JPanel(new BorderLayout(5, 5));
        cbMethod = new JComboBox<>(new String[]{"GET", "POST", "HEAD"});
        cbMethod.setFont(new Font("Segoe UI", Font.BOLD, 13));

        txtUrl = new JTextField("http://localhost:8080/index.html");
        txtUrl.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnSend = new JButton("Gửi Request");
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSend.setBackground(new Color(0, 102, 204));
        btnSend.setForeground(Color.WHITE);

        btnDownload = new JButton("📥 Tải file (Download)");
        btnDownload.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnDownload.setBackground(new Color(40, 167, 69));
        btnDownload.setForeground(Color.WHITE);
        btnDownload.setEnabled(false);

        btnScan = new JButton("Quét LAN");
        btnHistory = new JButton("Lịch sử");
        btnDbManager = new JButton("🗄️ CSDL");
        btnOpenExternal = new JButton("Mở Chrome");
        btnOpenExternal.setEnabled(false);

        btnPanel.add(btnSend);
        btnPanel.add(btnDownload);
        btnPanel.add(btnScan);
        btnPanel.add(btnHistory);
        btnPanel.add(btnDbManager);
        btnPanel.add(btnOpenExternal);

        btnBack = new JButton("< Lùi");
        btnForward = new JButton("Tiến >");
        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navPanel.add(btnBack);
        navPanel.add(btnForward);
        navPanel.add(cbMethod);
        urlPanel.add(navPanel, BorderLayout.WEST);
        urlPanel.add(txtUrl, BorderLayout.CENTER);
        urlPanel.add(btnPanel, BorderLayout.EAST);

        JPanel postPanel = new JPanel(new BorderLayout(5, 5));
        postPanel.add(new JLabel("Dữ liệu POST (ví dụ: message=HelloVKU): "), BorderLayout.WEST);
        txtPostData = new JTextField("message=Xin chao thay giao LTM VKU");
        txtPostData.setEnabled(false);
        postPanel.add(txtPostData, BorderLayout.CENTER);

        topPanel.add(urlPanel);
        topPanel.add(postPanel);
        add(topPanel, BorderLayout.NORTH);

        // 2. Middle Stats Panel
        txtStatsAndHeaders = new JTextArea(7, 80);
        txtStatsAndHeaders.setEditable(false);
        txtStatsAndHeaders.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtStatsAndHeaders.setBackground(new Color(245, 248, 250));
        JScrollPane scrollStats = new JScrollPane(txtStatsAndHeaders);
        scrollStats.setBorder(BorderFactory.createTitledBorder("Kết quả phân tích HTTP & Thống kê thẻ HTML"));

        // 3. Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();
        htmlRenderPane = new JEditorPane();
        htmlRenderPane.setEditable(false);
        htmlRenderPane.setContentType("text/html");
        setupHtmlRenderKit();

        htmlRenderPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String desc = e.getDescription();
                if (desc != null && !desc.isEmpty()) {
                    try {
                        URI resolvedUri = URI.create(lastUrl.isEmpty() ? txtUrl.getText().trim() : lastUrl).resolve(desc);
                        txtUrl.setText(resolvedUri.toString());
                        cbMethod.setSelectedItem("GET");
                        executeRequest();
                    } catch (Exception ignored) {}
                }
            }
        });

        tabbedPane.addTab("🌐 Giao diện hiển thị HTML & CSS (Web View)", new JScrollPane(htmlRenderPane));

        txtRawHtml = new JTextArea();
        txtRawHtml.setEditable(false);
        txtRawHtml.setFont(new Font("Consolas", Font.PLAIN, 12));
        tabbedPane.addTab("📄 Mã nguồn HTML gốc (Raw Source)", new JScrollPane(txtRawHtml));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollStats, tabbedPane);
        splitPane.setDividerLocation(180);
        add(splitPane, BorderLayout.CENTER);

        // Actions
        cbMethod.addActionListener(e -> {
            String method = (String) cbMethod.getSelectedItem();
            txtPostData.setEnabled("POST".equals(method));
        });

        btnSend.addActionListener(e -> executeRequest());
        txtUrl.addActionListener(e -> executeRequest());
        btnDownload.addActionListener(e -> performDownloadFile());
        btnOpenExternal.addActionListener(e -> openInSystemBrowser());
        btnBack.addActionListener(e -> navigateTo(navIndex - 1));
        btnForward.addActionListener(e -> navigateTo(navIndex + 1));
        btnHistory.addActionListener(e -> showHistoryDialog());
        btnDbManager.addActionListener(e -> showDatabaseDialog());
        btnScan.addActionListener(e -> autoScanLanServer(false));

        updateNavButtons();
        autoScanLanServer(true);
    }

    private void setupHtmlRenderKit() {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet style = kit.getStyleSheet();
        style.addRule("body { font-family: Segoe UI, sans-serif; margin: 15px; color: #24292f; }");
        style.addRule("h1 { color: #0066cc; font-size: 20px; }");
        style.addRule("h2 { color: #0969da; font-size: 16px; border-bottom: 1px solid #d0d7de; padding-bottom: 4px; }");
        style.addRule("p { font-size: 13px; line-height: 1.5; }");
        style.addRule("span { color: #cf222e; font-weight: bold; }");
        style.addRule("a { color: #0969da; text-decoration: none; }");
        style.addRule(".card { background-color: #f6f8fa; border: 1px solid #d0d7de; border-radius: 6px; padding: 12px; margin-bottom: 12px; }");
        style.addRule(".media-badge { background-color: #e6f4ea; border: 1px solid #34a853; padding: 10px; border-radius: 6px; }");
        htmlRenderPane.setEditorKit(kit);
    }

    private void executeRequest() {
        executeRequest(true);
    }

    private void executeRequest(boolean addToHistory) {
        String rawUrl = txtUrl.getText().trim();
        if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            rawUrl = "http://" + rawUrl;
            txtUrl.setText(rawUrl);
        }

        String method = (String) cbMethod.getSelectedItem();
        String postData = txtPostData.getText();

        btnSend.setEnabled(false);
        btnOpenExternal.setEnabled(false);
        btnDownload.setEnabled(false);
        txtStatsAndHeaders.setText("Đang gửi " + method + " request tới: " + rawUrl + " ...");

        final String targetUrl = rawUrl;
        new Thread(() -> {
            try {
                URI uri = URI.create(targetUrl);
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(12))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) VKU-SimpleBrowser/4.0")
                        .header("Accept", "*/*");

                if ("GET".equals(method)) {
                    reqBuilder.GET();
                } else if ("HEAD".equals(method)) {
                    reqBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                } else if ("POST".equals(method)) {
                    reqBuilder.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    reqBuilder.POST(HttpRequest.BodyPublishers.ofString(postData, StandardCharsets.UTF_8));
                }

                HttpResponse<byte[]> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
                int statusCode = response.statusCode();
                HttpHeaders headers = response.headers();
                String contentType = headers.firstValue("content-type").orElse("text/plain");
                lastContentType = contentType;
                lastUrl = response.uri().toString();

                if ("HEAD".equals(method)) {
                    StringBuilder headInfo = new StringBuilder();
                    headInfo.append("=== THÔNG TIN TÀI NGUYÊN (PHƯƠNG THỨC HEAD - RFC 2616) ===\n");
                    headInfo.append("URL yêu cầu   : ").append(targetUrl).append("\n");
                    headInfo.append("Mã trạng thái : HTTP ").append(statusCode).append("\n");
                    headInfo.append("Content-Type  : ").append(contentType).append("\n");
                    headInfo.append("----------------------------------------------------------\n");
                    for (Map.Entry<String, List<String>> entry : headers.map().entrySet()) {
                        headInfo.append(String.format("%-24s: %s\n", entry.getKey(), String.join(", ", entry.getValue())));
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (addToHistory) recordVisit(method, targetUrl, postData, statusCode, 0);
                        txtStatsAndHeaders.setText(headInfo.toString());
                        txtRawHtml.setText("(Phương thức HEAD không trả về thân dữ liệu)");
                        renderHtmlSafely("<html><body><div class='card'><h2>Kết quả phương thức HEAD (HTTP " + statusCode + ")</h2>"
                                + "<p>Phương thức <span>HEAD</span> chỉ lấy các Headers của máy chủ mà không tải nội dung file.</p></div></body></html>");
                        btnSend.setEnabled(true);
                        btnOpenExternal.setEnabled(true);
                    });
                } else {
                    byte[] bodyBytes = response.body() != null ? response.body() : new byte[0];
                    lastResponseBody = bodyBytes;
                    long byteLength = bodyBytes.length;

                    boolean isBinary = contentType.contains("pdf") || contentType.contains("audio") ||
                                       contentType.contains("video") || contentType.contains("image") ||
                                       contentType.contains("octet-stream");

                    if (isBinary) {
                        String typeName = contentType.contains("pdf") ? "📄 Tài liệu PDF" :
                                (contentType.contains("audio") ? "🎵 Tệp Âm thanh MP3" :
                                (contentType.contains("video") ? "🎥 Tệp Video MP4" : "📁 Tệp nhị phân Đa phương tiện"));

                        StringBuilder stats = new StringBuilder();
                        stats.append("=== PHÂN TÍCH TÀI NGUYÊN ĐA DỊCH VỤ (PHƯƠNG THỨC ").append(method).append(") ===\n");
                        stats.append("URL thực tế            : ").append(lastUrl).append(" (HTTP ").append(statusCode).append(")\n");
                        stats.append("Loại MIME Type         : ").append(contentType).append(" [").append(typeName).append("]\n");
                        stats.append("Dung lượng tệp         : ").append(byteLength).append(" bytes (").append(byteLength / 1024 + 1).append(" KB)\n");
                        stats.append("----------------------------------------------------------\n");
                        stats.append("Máy chủ hỗ trợ truyền tải nhị phân đầy đủ.\nBấm nút [📥 Tải file (Download)] trên thanh công cụ để lưu tệp về máy!");

                        String previewHtml = "<html><body><div class='card media-badge'>"
                                + "<h2>" + typeName + "</h2>"
                                + "<p>Đường dẫn: <b>" + lastUrl + "</b></p>"
                                + "<p>Kích thước: <span>" + byteLength + " bytes</span> | MIME Type: <b>" + contentType + "</b></p>"
                                + "<p>Trình duyệt đã tải thành công toàn bộ nhị phân của tài nguyên!</p>"
                                + "<p>👉 Hãy bấm nút màu xanh <b>[📥 Tải file (Download)]</b> ở thanh công cụ để lưu tệp về máy tính của bạn.</p>"
                                + "</div></body></html>";

                        SwingUtilities.invokeLater(() -> {
                            if (addToHistory) recordVisit(method, targetUrl, postData, statusCode, byteLength);
                            txtStatsAndHeaders.setText(stats.toString());
                            txtRawHtml.setText("[Dữ liệu nhị phân - Kích thước: " + byteLength + " bytes]");
                            renderHtmlSafely(previewHtml);
                            btnSend.setEnabled(true);
                            btnDownload.setEnabled(true);
                            btnOpenExternal.setEnabled(true);
                        });
                    } else {
                        // Text / HTML
                        String htmlContent = new String(bodyBytes, StandardCharsets.UTF_8);
                        lastRawHtml = htmlContent;

                        int countP = countHtmlTag(htmlContent, "p");
                        int countDiv = countHtmlTag(htmlContent, "div");
                        int countSpan = countHtmlTag(htmlContent, "span");
                        int countImg = countHtmlTag(htmlContent, "img") + countHtmlTag(htmlContent, "imag");

                        StringBuilder stats = new StringBuilder();
                        stats.append("=== KẾT QUẢ PHÂN TÍCH TRANG HTML (PHƯƠNG THỨC ").append(method).append(") ===\n");
                        stats.append("URL thực tế            : ").append(lastUrl).append(" (HTTP ").append(statusCode).append(")\n");
                        stats.append("Chiều dài trang HTML   : ").append(htmlContent.length()).append(" ký tự (").append(byteLength).append(" bytes)\n");
                        stats.append("----------------------------------------------------------\n");
                        stats.append("• Số lượng thẻ <p>     : ").append(countP).append("\n");
                        stats.append("• Số lượng thẻ <div>   : ").append(countDiv).append("\n");
                        stats.append("• Số lượng thẻ <span>  : ").append(countSpan).append("\n");
                        stats.append("• Số lượng thẻ <imag>  : ").append(countImg).append(" (bao gồm cả <img> và <imag>)\n");

                        String printableHtml = prepareHtmlForSwingView(htmlContent, lastUrl);

                        SwingUtilities.invokeLater(() -> {
                            if (addToHistory) recordVisit(method, targetUrl, postData, statusCode, byteLength);
                            txtStatsAndHeaders.setText(stats.toString());
                            txtRawHtml.setText(htmlContent);
                            txtRawHtml.setCaretPosition(0);
                            renderHtmlSafely(printableHtml);
                            btnSend.setEnabled(true);
                            btnDownload.setEnabled(true);
                            btnOpenExternal.setEnabled(true);
                        });
                    }
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    txtStatsAndHeaders.setText("Lỗi kết nối (" + ex.getClass().getSimpleName() + "): " + ex.getMessage());
                    renderHtmlSafely("<html><body><h2 style='color:red;'>Không thể tải trang web</h2><p>Lỗi: " + ex.getMessage() + "</p>"
                            + "<p>Hãy đảm bảo <b>httpServer.java</b> đang chạy tại cổng 8080!</p></body></html>");
                    btnSend.setEnabled(true);
                });
            }
        }).start();
    }

    private void performDownloadFile() {
        if (lastResponseBody == null || lastResponseBody.length == 0) {
            JOptionPane.showMessageDialog(this, "Chưa có dữ liệu nào để tải về!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String suggestedName = "downloaded_file";
        try {
            URI u = URI.create(lastUrl);
            String path = u.getPath();
            if (path != null && path.contains("/")) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (!name.isEmpty()) suggestedName = name;
            }
        } catch (Exception ignored) {}

        if (suggestedName.equals("downloaded_file")) {
            if (lastContentType.contains("pdf")) suggestedName += ".pdf";
            else if (lastContentType.contains("audio") || lastContentType.contains("mp3")) suggestedName += ".mp3";
            else if (lastContentType.contains("video") || lastContentType.contains("mp4")) suggestedName += ".mp4";
            else if (lastContentType.contains("html")) suggestedName += ".html";
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(suggestedName));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            try {
                Files.write(dest.toPath(), lastResponseBody);
                JOptionPane.showMessageDialog(this, "Đã tải xuống thành công!\nLưu tại: " + dest.getAbsolutePath() + "\nDung lượng: " + lastResponseBody.length + " bytes");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Lỗi ghi file: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void renderHtmlSafely(String cleanHtml) {
        try {
            HTMLEditorKit kit = (HTMLEditorKit) htmlRenderPane.getEditorKit();
            HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
            doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
            htmlRenderPane.setDocument(doc);
            htmlRenderPane.setText(cleanHtml);
            htmlRenderPane.setCaretPosition(0);
        } catch (Exception e) {
            htmlRenderPane.setText("<html><body><p>Lỗi hiển thị: " + e.getMessage() + "</p></body></html>");
        }
    }

    private String prepareHtmlForSwingView(String rawHtml, String baseUrl) {
        String clean = rawHtml.replaceAll("(?is)<(script|style|svg)[^>]*>.*?</\\1>", "");
        clean = clean.replaceAll("(?is)<(link|meta)[^>]*>", "");
        return clean;
    }

    private int countHtmlTag(String html, String tagName) {
        String regex = "<" + tagName + "\\b[^>]*>";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    // ==================== CƠ SỞ DỮ LIỆU LỊCH SỬ (CSV DATABASE) ====================
    private void recordVisit(String method, String url, String postData, int status, long length) {
        String now = LocalDateTime.now().format(TIME_FMT);
        HistoryEntry entry = new HistoryEntry(now, method, url, "POST".equals(method) ? postData : "", status, length);
        visitLog.add(0, entry);
        saveHistoryEntryToDb(entry);

        if (navIndex >= 0 && navIndex < navStack.size() && navStack.get(navIndex).sameRequest(entry)) {
            updateNavButtons();
            return;
        }
        while (navStack.size() > navIndex + 1) {
            navStack.remove(navStack.size() - 1);
        }
        navStack.add(entry);
        navIndex = navStack.size() - 1;
        updateNavButtons();
    }

    private void saveHistoryEntryToDb(HistoryEntry e) {
        boolean exists = new File(HISTORY_DB_FILE).exists();
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(HISTORY_DB_FILE, true), StandardCharsets.UTF_8))) {
            if (!exists) {
                pw.println("time,method,url,postData,status,length");
            }
            pw.printf("%s,%s,%s,%s,%d,%d%n",
                    escapeCsv(e.time), escapeCsv(e.method), escapeCsv(e.url), escapeCsv(e.postData), e.statusCode, e.contentLength);
        } catch (IOException ignored) {}
    }

    private void loadHistoryDatabase() {
        File f = new File(HISTORY_DB_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line = br.readLine(); // Header
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",", -1);
                if (parts.length >= 6) {
                    HistoryEntry e = new HistoryEntry(parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim(),
                            Integer.parseInt(parts[4].trim()), Long.parseLong(parts[5].trim()));
                    visitLog.add(e);
                }
            }
        } catch (Exception ignored) {}
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        return s.replace(",", " ");
    }

    private void showDatabaseDialog() {
        JDialog dlg = new JDialog(this, "🗄️ Quản lý Cơ sở Dữ liệu Lịch sử Duyệt Web (" + visitLog.size() + " bản ghi)", true);
        dlg.setSize(850, 480);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(6, 6));

        DefaultTableModel model = new DefaultTableModel(new String[]{"Thời gian", "Phương thức", "URL", "Mã HTTP", "Kích thước (Bytes)", "Dữ liệu POST"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (HistoryEntry e : visitLog) {
            model.addRow(new Object[]{e.time, e.method, e.url, e.statusCode, e.contentLength, e.postData});
        }
        JTable table = new JTable(model);
        table.setRowHeight(22);
        dlg.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnOpenSel = new JButton("Mở URL đã chọn");
        btnOpenSel.addActionListener(ev -> {
            int r = table.getSelectedRow();
            if (r != -1) {
                dlg.dispose();
                txtUrl.setText((String) model.getValueAt(r, 2));
                cbMethod.setSelectedItem((String) model.getValueAt(r, 1));
                executeRequest();
            }
        });

        JButton btnClearDb = new JButton("Xóa toàn bộ CSDL");
        btnClearDb.addActionListener(ev -> {
            int confirm = JOptionPane.showConfirmDialog(dlg, "Bạn có chắc chắn muốn xóa toàn bộ CSDL lịch sử?", "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                visitLog.clear();
                navStack.clear();
                navIndex = -1;
                new File(HISTORY_DB_FILE).delete();
                model.setRowCount(0);
                updateNavButtons();
            }
        });
        btnPanel.add(btnOpenSel);
        btnPanel.add(btnClearDb);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void showHistoryDialog() {
        showDatabaseDialog();
    }

    private void navigateTo(int index) {
        if (index < 0 || index >= navStack.size()) return;
        navIndex = index;
        HistoryEntry entry = navStack.get(index);
        txtUrl.setText(entry.url);
        cbMethod.setSelectedItem(entry.method);
        if ("POST".equals(entry.method)) txtPostData.setText(entry.postData);
        updateNavButtons();
        executeRequest(false);
    }

    private void updateNavButtons() {
        btnBack.setEnabled(navIndex > 0);
        btnForward.setEnabled(navIndex >= 0 && navIndex < navStack.size() - 1);
    }

    private void openInSystemBrowser() {
        try {
            Desktop.getDesktop().browse(URI.create(lastUrl));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể mở trình duyệt ngoài: " + ex.getMessage());
        }
    }

    private void autoScanLanServer(boolean silent) {
        new Thread(() -> {
            String myIp = getLocalIPv4();
            if (myIp != null && !myIp.equals("127.0.0.1")) {
                SwingUtilities.invokeLater(() -> txtUrl.setText("http://localhost:" + SERVER_PORT + "/index.html"));
            }
        }).start();
    }

    private static String getLocalIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface iface : Collections.list(interfaces)) {
                if (iface.isLoopback() || !iface.isUp()) continue;
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new SimpleBrowser().setVisible(true));
    }
}
