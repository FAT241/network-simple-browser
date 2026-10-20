import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleBrowser extends JFrame {

    private JTextField txtUrl;
    private JComboBox<String> cbMethod;
    private JTextField txtPostData;
    private JButton btnSend;
    private JButton btnOpenExternal;
    private JTextArea txtStatsAndHeaders;
    private JTextArea txtRawHtml;
    private JEditorPane htmlRenderPane;

    private String lastRawHtml = "";
    private String lastUrl = "";

    private static final int SERVER_PORT = 8080;
    private JButton btnBack, btnForward, btnHistory, btnScan;
    private final List<HistoryEntry> navStack = new ArrayList<>(); // ngăn xếp cho Back/Forward
    private int navIndex = -1;
    private final List<HistoryEntry> visitLog = new ArrayList<>(); // nhật ký mọi lượt truy cập

    // Sử dụng HttpClient hiện đại của Java (Hỗ trợ tự động Redirect HTTP <-> HTTPS, không bị treo luồng)
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public SimpleBrowser() {
        setTitle("Simple Web Browser - Nhóm 1 (GET / POST / HEAD & Trình hiển thị HTML)");
        setSize(1080, 760);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(8, 8));

        // 1. Thanh công cụ nhập URL và chọn phương thức
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));

        JPanel urlPanel = new JPanel(new BorderLayout(5, 5));
        cbMethod = new JComboBox<>(new String[]{"GET", "POST", "HEAD"});
        cbMethod.setFont(new Font("SansSerif", Font.BOLD, 13));

        txtUrl = new JTextField("http://localhost:8080/index.html");
        txtUrl.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        btnSend = new JButton("Gửi Request");
        btnOpenExternal = new JButton("Mở trên Trình duyệt ngoài");
        btnOpenExternal.setEnabled(false);
        btnScan = new JButton("Quét LAN");
        btnHistory = new JButton("Lịch sử");
        btnPanel.add(btnSend);
        btnPanel.add(btnScan);
        btnPanel.add(btnHistory);
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
        postPanel.add(new JLabel("Dữ liệu POST (ví dụ: hoten=LeNhuanPhat&lop=JIT): "), BorderLayout.WEST);
        txtPostData = new JTextField("hoten=LeNhuanPhat&truong=VKU");
        txtPostData.setEnabled(false);
        postPanel.add(txtPostData, BorderLayout.CENTER);

        topPanel.add(urlPanel);
        topPanel.add(postPanel);
        add(topPanel, BorderLayout.NORTH);

        // 2. Khung hiển thị kết quả phân tích (Chiều dài, số thẻ <p>, <div>, <span>, <img> hoặc HEAD)
        txtStatsAndHeaders = new JTextArea(8, 80);
        txtStatsAndHeaders.setEditable(false);
        txtStatsAndHeaders.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtStatsAndHeaders.setBackground(new Color(245, 248, 250));
        JScrollPane scrollStats = new JScrollPane(txtStatsAndHeaders);
        scrollStats.setBorder(BorderFactory.createTitledBorder("Kết quả phân tích HTML (GET, POST) / Thông tin tài nguyên (HEAD)"));

        // 3. Khung hiển thị Giao diện trang Web ngay trong cửa sổ ứng dụng
        JTabbedPane tabbedPane = new JTabbedPane();

        htmlRenderPane = new JEditorPane();
        htmlRenderPane.setEditable(false);
        setupSwingHtmlEngine();

        // Bấm vào link trên trang web sẽ tự động nhảy sang trang đó
        htmlRenderPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String desc = e.getDescription();
                if (desc != null && !desc.isEmpty()) {
                    try {
                        URI resolvedUri = URI.create(lastUrl).resolve(desc);
                        txtUrl.setText(resolvedUri.toString());
                        cbMethod.setSelectedItem("GET");
                        executeRequest();
                    } catch (Exception ignored) {
                    }
                }
            }
        });

        tabbedPane.addTab("Giao diện trang Web (Rendered HTML View)", new JScrollPane(htmlRenderPane));

        txtRawHtml = new JTextArea();
        txtRawHtml.setEditable(false);
        txtRawHtml.setFont(new Font("Consolas", Font.PLAIN, 12));
        tabbedPane.addTab("Mã nguồn HTML gốc (Raw HTML Source)", new JScrollPane(txtRawHtml));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollStats, tabbedPane);
        splitPane.setDividerLocation(195);
        add(splitPane, BorderLayout.CENTER);

        // Xử lý sự kiện
        cbMethod.addActionListener(e -> {
            String method = (String) cbMethod.getSelectedItem();
            txtPostData.setEnabled("POST".equals(method));
        });

        btnSend.addActionListener(e -> executeRequest());
        txtUrl.addActionListener(e -> executeRequest());
        btnOpenExternal.addActionListener(e -> openInSystemBrowser());
        btnBack.addActionListener(e -> navigateTo(navIndex - 1));
        btnForward.addActionListener(e -> navigateTo(navIndex + 1));
        btnHistory.addActionListener(e -> showHistoryDialog());
        btnScan.addActionListener(e -> autoScanLanServer(false));

        updateNavButtons();
        autoScanLanServer(true); // Tự động dò tìm & kết nối server ngay khi mở trình duyệt
    }

    // ==================== LỊCH SỬ DUYỆT WEB (BACK / FORWARD / DANH SÁCH) ====================
    private static class HistoryEntry {
        final String method, url, postData, time;

        HistoryEntry(String method, String url, String postData) {
            this.method = method;
            this.url = url;
            this.postData = postData;
            this.time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }

        boolean sameRequest(HistoryEntry o) {
            return method.equals(o.method) && url.equals(o.url) && postData.equals(o.postData);
        }

        @Override
        public String toString() {
            return "[" + time + "] " + method + "  " + url + ("POST".equals(method) ? "   (" + postData + ")" : "");
        }
    }

    // Ghi nhận 1 lượt truy cập THÀNH CÔNG (luôn chạy trên luồng giao diện EDT)
    private void recordVisit(String method, String url, String postData) {
        HistoryEntry entry = new HistoryEntry(method, url, "POST".equals(method) ? postData : "");
        visitLog.add(entry);

        // Tải lại đúng trang đang xem thì không thêm trùng vào ngăn xếp Back/Forward
        if (navIndex >= 0 && navStack.get(navIndex).sameRequest(entry)) {
            updateNavButtons();
            return;
        }
        // Đi trang mới sau khi đã Back => xóa phần "Tiến" cũ (giống trình duyệt thật)
        while (navStack.size() > navIndex + 1) {
            navStack.remove(navStack.size() - 1);
        }
        navStack.add(entry);
        navIndex = navStack.size() - 1;
        updateNavButtons();
    }

    private void updateNavButtons() {
        btnBack.setEnabled(navIndex > 0);
        btnForward.setEnabled(navIndex >= 0 && navIndex < navStack.size() - 1);
    }

    private void navigateTo(int index) {
        if (!btnSend.isEnabled()) return; // đang tải trang, bỏ qua
        if (index < 0 || index >= navStack.size()) return;
        navIndex = index;
        HistoryEntry entry = navStack.get(index);
        txtUrl.setText(entry.url);
        cbMethod.setSelectedItem(entry.method);
        if ("POST".equals(entry.method)) txtPostData.setText(entry.postData);
        updateNavButtons();
        executeRequest(false); // false: không ghi thêm vào lịch sử khi Back/Forward
    }

    private void showHistoryDialog() {
        if (visitLog.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chưa có trang nào trong lịch sử duyệt web.",
                    "Lịch sử duyệt web", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DefaultListModel<HistoryEntry> model = new DefaultListModel<>();
        for (int i = visitLog.size() - 1; i >= 0; i--) { // mới nhất ở trên cùng
            model.addElement(visitLog.get(i));
        }
        JList<HistoryEntry> list = new JList<>(model);
        list.setFont(new Font("Consolas", Font.PLAIN, 13));
        list.setSelectedIndex(0);

        JDialog dlg = new JDialog(this, "Lịch sử duyệt web (" + visitLog.size() + " lượt truy cập)", true);
        Runnable openSelected = () -> {
            HistoryEntry sel = list.getSelectedValue();
            if (sel == null) return;
            dlg.dispose();
            txtUrl.setText(sel.url);
            cbMethod.setSelectedItem(sel.method);
            if ("POST".equals(sel.method)) txtPostData.setText(sel.postData);
            executeRequest(true);
        };
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent ev) {
                if (ev.getClickCount() == 2) openSelected.run();
            }
        });

        JButton btnOpen = new JButton("Mở trang đã chọn");
        JButton btnClear = new JButton("Xóa lịch sử");
        JButton btnClose = new JButton("Đóng");
        btnOpen.addActionListener(e -> openSelected.run());
        btnClear.addActionListener(e -> {
            visitLog.clear();
            navStack.clear();
            navIndex = -1;
            updateNavButtons();
            dlg.dispose();
        });
        btnClose.addActionListener(e -> dlg.dispose());

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        bottom.add(btnOpen);
        bottom.add(btnClear);
        bottom.add(btnClose);

        JScrollPane sp = new JScrollPane(list);
        sp.setPreferredSize(new Dimension(760, 320));
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.add(sp, BorderLayout.CENTER);
        dlg.add(bottom, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // ==================== TỰ ĐỘNG DÒ TÌM HTTP SERVER TRONG MẠNG LAN ====================
    private void autoScanLanServer(boolean isStartup) {
        btnScan.setEnabled(false);
        txtStatsAndHeaders.setText("Đang dò tìm HTTP Server trong mạng LAN (cổng " + SERVER_PORT + ")...");

        new Thread(() -> {
            Map<String, String> found = new ConcurrentHashMap<>();
            ExecutorService pool = Executors.newFixedThreadPool(64);
            try {
                // Khi vừa mở app: thử localhost trước cho nhanh
                if (isStartup) {
                    String banner = probeHttpServer("127.0.0.1", SERVER_PORT);
                    if (banner != null) found.put("localhost", banner);
                }
                if (found.isEmpty()) {
                    // Lấy dải mạng từ IP LAN của máy mình, VD "192.168.43." rồi quét .1 -> .254 song song
                    String localIp = getLocalIPv4();
                    String subnet = localIp.substring(0, localIp.lastIndexOf('.') + 1);
                    List<Callable<Void>> tasks = new ArrayList<>();
                    for (int i = 1; i < 255; i++) {
                        final String ip = subnet + i;
                        tasks.add(() -> {
                            String banner = probeHttpServer(ip, SERVER_PORT);
                            if (banner != null) found.put(ip, banner);
                            return null;
                        });
                    }
                    pool.invokeAll(tasks);
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    txtStatsAndHeaders.setText("Lỗi quét mạng LAN: " + ex.getMessage());
                    btnScan.setEnabled(true);
                });
                return;
            } finally {
                pool.shutdownNow();
            }

            List<String> ips = new ArrayList<>(found.keySet());
            Collections.sort(ips);
            SwingUtilities.invokeLater(() -> onScanFinished(ips, found, isStartup));
        }).start();
    }

    private void onScanFinished(List<String> ips, Map<String, String> banners, boolean isStartup) {
        btnScan.setEnabled(true);
        if (ips.isEmpty()) {
            txtStatsAndHeaders.setText("Không tìm thấy HTTP Server nào ở cổng " + SERVER_PORT
                    + " trong mạng LAN.\nHãy chạy httpServer.java hoặc nhập URL thủ công.");
            if (!isStartup) {
                JOptionPane.showMessageDialog(this,
                        "Không tìm thấy HTTP Server nào trong mạng LAN.\n"
                                + "- Kiểm tra httpServer đã chạy chưa.\n"
                                + "- Hai máy phải cùng mạng và Firewall phải cho phép cổng " + SERVER_PORT + ".",
                        "Quét LAN", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        String chosen = ips.get(0);
        if (ips.size() > 1) {
            String[] options = new String[ips.size()];
            for (int i = 0; i < options.length; i++) {
                options[i] = ips.get(i) + "   [" + banners.get(ips.get(i)) + "]";
            }
            Object sel = JOptionPane.showInputDialog(this, "Tìm thấy " + ips.size() + " Server, chọn 1 máy:",
                    "Quét LAN", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (sel == null) {
                txtStatsAndHeaders.setText("Đã hủy chọn Server.");
                return;
            }
            chosen = sel.toString().split("\\s+")[0];
        }

        // Tự điền URL và kết nối luôn
        txtUrl.setText("http://" + chosen + ":" + SERVER_PORT + "/index.html");
        cbMethod.setSelectedItem("GET");
        executeRequest(true);
    }

    // Gửi thử HEAD / để xác nhận đây thật sự là HTTP Server; trả về giá trị header Server (hoặc null nếu không phải)
    private String probeHttpServer(String ip, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 250);
            s.setSoTimeout(700);
            OutputStream os = s.getOutputStream();
            os.write(("HEAD / HTTP/1.1\r\nHost: " + ip + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.ISO_8859_1));
            String status = in.readLine();
            if (status == null || !status.startsWith("HTTP/")) return null;
            String server = "Server không rõ";
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("server:")) {
                    server = line.substring(7).trim();
                    break;
                }
            }
            return server;
        } catch (Exception e) {
            return null;
        }
    }

    // Lấy IPv4 LAN thật của máy hiện tại (bỏ qua card ảo VMware/Docker/WSL...)
    private static String getLocalIPv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                String name = iface.getDisplayName().toLowerCase();
                if (name.contains("vmware") || name.contains("virtual") || name.contains("wsl") || name.contains("docker")) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) return a.getHostAddress();
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private void setupSwingHtmlEngine() {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet style = kit.getStyleSheet();
        style.addRule("body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 14px; margin: 16px; background-color: #ffffff; color: #1e293b; }");
        style.addRule("h1 { color: #0f172a; font-size: 24px; margin-bottom: 10px; }");
        style.addRule("h2 { color: #1d4ed8; font-size: 19px; margin-top: 12px; }");
        style.addRule("h3 { color: #0369a1; font-size: 16px; }");
        style.addRule("p { margin: 8px 0; line-height: 1.5; }");
        style.addRule("div { margin: 6px 0; padding: 6px; }");
        style.addRule("span { color: #be123c; font-weight: bold; }");
        style.addRule("a { color: #2563eb; text-decoration: underline; }");
        style.addRule("table { border-collapse: collapse; width: 100%; margin: 10px 0; }");
        style.addRule("th { background-color: #e2e8f0; color: #0f172a; padding: 8px; border: 1px solid #cbd5e1; }");
        style.addRule("td { padding: 8px; border: 1px solid #cbd5e1; }");
        style.addRule(".card { background-color: #f8fafc; border: 1px solid #e2e8f0; padding: 12px; margin-bottom: 12px; }");
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
        txtStatsAndHeaders.setText("Đang thực hiện " + method + " request tới " + rawUrl + " ...");

        final String targetUrl = rawUrl;
        new Thread(() -> {
            try {
                URI uri = URI.create(targetUrl);
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(12))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SimpleJavaBrowser/3.0")
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

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

                if ("HEAD".equals(method)) {
                    StringBuilder headInfo = new StringBuilder();
                    headInfo.append("=== THÔNG TIN TÀI NGUYÊN (PHƯƠNG THỨC HEAD - RFC 2616) ===\n");
                    headInfo.append("URL yêu cầu   : ").append(targetUrl).append("\n");
                    headInfo.append("Mã trạng thái : HTTP ").append(statusCode).append("\n");
                    headInfo.append("----------------------------------------------------------\n");

                    for (Map.Entry<String, List<String>> entry : headers.map().entrySet()) {
                        headInfo.append(String.format("%-24s: %s\n", entry.getKey(), String.join(", ", entry.getValue())));
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (addToHistory) recordVisit(method, targetUrl, postData);
                        txtStatsAndHeaders.setText(headInfo.toString());
                        txtRawHtml.setText("(Phương thức HEAD không trả về thân trang HTML)");
                        renderHtmlSafely("<html><body><div class='card'><h2>Kết quả phương thức HEAD (HTTP " + statusCode + ")</h2>"
                                + "<p>Phương thức <span>HEAD</span> chỉ lấy thông tin Header của tài nguyên (hiển thị ở khung trên) mà không tải nội dung trang web.</p></div></body></html>");
                        btnSend.setEnabled(true);
                    });

                } else {
                    byte[] bodyBytes = response.body() != null ? response.body() : new byte[0];
                    String htmlContent = new String(bodyBytes, StandardCharsets.UTF_8);
                    lastRawHtml = htmlContent;
                    lastUrl = response.uri().toString();

                    int charLength = htmlContent.length();
                    int byteLength = bodyBytes.length;

                    // Đếm chính xác các thẻ theo yêu cầu đề bài
                    int countP = countHtmlTag(htmlContent, "p");
                    int countDiv = countHtmlTag(htmlContent, "div");
                    int countSpan = countHtmlTag(htmlContent, "span");
                    int countImg = countHtmlTag(htmlContent, "img") + countHtmlTag(htmlContent, "imag");

                    StringBuilder stats = new StringBuilder();
                    stats.append("=== KẾT QUẢ PHÂN TÍCH TRANG HTML (PHƯƠNG THỨC ").append(method).append(") ===\n");
                    stats.append("URL thực tế            : ").append(lastUrl).append(" (HTTP ").append(statusCode).append(")\n");
                    stats.append("Chiều dài trang HTML   : ").append(charLength).append(" ký tự (").append(byteLength).append(" bytes)\n");
                    stats.append("----------------------------------------------------------\n");
                    stats.append("• Số lượng thẻ <p>     : ").append(countP).append("\n");
                    stats.append("• Số lượng thẻ <div>   : ").append(countDiv).append("\n");
                    stats.append("• Số lượng thẻ <span>  : ").append(countSpan).append("\n");
                    stats.append("• Số lượng thẻ <imag>  : ").append(countImg).append(" (bao gồm cả <img> và <imag>)\n");

                    // Chuyển đổi HTML phức tạp về dạng HTML chuẩn mà bộ vẽ Java Swing hiển thị được 100%
                    String printableHtml = prepareHtmlForSwingView(htmlContent, lastUrl);

                    SwingUtilities.invokeLater(() -> {
                        if (addToHistory) recordVisit(method, targetUrl, postData);
                        txtStatsAndHeaders.setText(stats.toString());
                        txtRawHtml.setText(htmlContent);
                        txtRawHtml.setCaretPosition(0);

                        renderHtmlSafely(printableHtml);

                        btnSend.setEnabled(true);
                        btnOpenExternal.setEnabled(true);
                    });
                }

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    txtStatsAndHeaders.setText("Lỗi kết nối (" + ex.getClass().getSimpleName() + "): " + ex.getMessage());
                    renderHtmlSafely("<html><body><h2 style='color:red;'>Không thể tải trang web</h2><p>Chi tiết lỗi: " + ex.getMessage() + "</p>"
                            + "<p>Hãy chắc chắn rằng bạn đã chạy <b>httpServer.java</b> (nếu test localhost:8080) hoặc nhập đúng địa chỉ web.</p></body></html>");
                    btnSend.setEnabled(true);
                });
            }
        }).start();
    }

    // Nạp HTML vào JEditorPane an toàn, không bao giờ bị lỗi trắng trang hay đơ giao diện
    private void renderHtmlSafely(String cleanHtml) {
        try {
            HTMLEditorKit kit = (HTMLEditorKit) htmlRenderPane.getEditorKit();
            HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
            doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
            htmlRenderPane.setDocument(doc);
            htmlRenderPane.setText(cleanHtml);
            htmlRenderPane.setCaretPosition(0);
        } catch (Exception e) {
            htmlRenderPane.setText("<html><body><p>Lỗi hiển thị HTML: " + e.getMessage() + "</p></body></html>");
        }
    }

    /**
     * Bộ tiền xử lý HTML thông minh cho Java Swing:
     * Loại bỏ các thẻ gây lỗi trắng trang trên JEditorPane (<head>, <script>, <style>, <link>, <meta>, <svg>)
     * và biến thẻ <img>/<imag> thành khung hiển thị trực quan để không bị treo mạng khi load ảnh ngoài.
     */
    private String prepareHtmlForSwingView(String rawHtml, String baseUrl) {
        String title = "Trang Web";
        Matcher titleMatcher = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(rawHtml);
        if (titleMatcher.find()) {
            title = titleMatcher.group(1).trim();
        }

        // Lấy phần nội dung trong <body> nếu có
        String bodyContent = rawHtml;
        Matcher bodyMatcher = Pattern.compile("(?is)<body[^>]*>(.*)</body>").matcher(rawHtml);
        if (bodyMatcher.find()) {
            bodyContent = bodyMatcher.group(1);
        }

        // Xóa các thẻ làm JEditorPane bị lỗi parser hoặc đơ
        bodyContent = bodyContent
                .replaceAll("(?is)<!--.*?-->", "")
                .replaceAll("(?is)<script.*?>.*?</script>", "")
                .replaceAll("(?is)<style.*?>.*?</style>", "")
                .replaceAll("(?is)<noscript.*?>.*?</noscript>", "")
                .replaceAll("(?is)<svg.*?>.*?</svg>", "")
                .replaceAll("(?is)<iframe.*?>.*?</iframe>", "")
                .replaceAll("(?is)<(meta|link|base)\\b[^>]*>", "")
                // Chuyển các thẻ HTML5 (header, nav, section, article, footer, main) thành <div> để Swing hiểu được
                .replaceAll("(?i)<(/?)(header|nav|main|section|article|aside|footer)\\b", "<$1div")
                // Thay thế thẻ <img> và <imag> bằng hộp hiển thị tên ảnh để giao diện hiện ngay lập tức không bị treo tải ảnh
                .replaceAll("(?i)<ima?g\\b[^>]*src=[\"']([^\"']+)[\"'][^>]*>",
                        "<div style='background-color:#e0f2fe; border:1px solid #7dd3fc; padding:6px; margin:4px 0; color:#0369a1;'>[Hình ảnh: $1]</div>");

        return "<html><body>"
                + "<div style='background-color:#eff6ff; border-bottom:2px solid #3b82f6; padding:10px; margin-bottom:12px;'>"
                + "<b>Tiêu đề trang:</b> " + title + " &nbsp;|&nbsp; <b>Nguồn:</b> " + baseUrl
                + "</div>"
                + bodyContent
                + "</body></html>";
    }

    private void openInSystemBrowser() {
        try {
            if (lastRawHtml.isEmpty()) return;
            File temp = File.createTempFile("browser_full_view_", ".html");
            temp.deleteOnExit();
            String baseTag = "<base href=\"" + lastUrl + "\">\n";
            String content = lastRawHtml.toLowerCase().contains("<head>")
                    ? lastRawHtml.replaceFirst("(?i)<head>", "<head>\n" + baseTag)
                    : baseTag + lastRawHtml;
            Files.write(temp.toPath(), content.getBytes(StandardCharsets.UTF_8));
            Desktop.getDesktop().browse(temp.toURI());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể mở trình duyệt ngoài: " + ex.getMessage());
        }
    }

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