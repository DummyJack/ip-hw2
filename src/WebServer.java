import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/**
 * 多執行緒 Web 伺服器主類
 * 負責監聽指定端口並處理傳入的 HTTP 請求
 */
public final class WebServer {
    private static final int DEFAULT_PORT = 6789;
    
    public static void main(String[] args) {
        int port = parsePort(args);
        startServer(port);
    }
    
    /**
     * 從命令列參數解析端口號
     */
    private static int parsePort(String[] args) {
        return args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
    }
    
    /**
     * 啟動 Web 伺服器
     */
    private static void startServer(int port) {
        try (ServerSocket serverSocket = createServerSocket(port)) {
            System.out.println("Web Server started, listening on port: " + port);
            handleConnections(serverSocket);
        } catch (IOException e) {
            System.err.println("Server startup failed: " + e.getMessage());
        }
    }
    
    /**
     * 創建伺服器 Socket
     */
    private static ServerSocket createServerSocket(int port) throws IOException {
        return new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"));
    }
    
    /**
     * 處理客戶端連接
     */
    private static void handleConnections(ServerSocket serverSocket) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Socket clientSocket = serverSocket.accept();
                startRequestHandler(clientSocket);
            } catch (IOException e) {
                System.err.println("Error handling client connection: " + e.getMessage());
            }
        }
    }
    
    /**
     * 啟動請求處理執行緒
     */
    private static void startRequestHandler(Socket clientSocket) {
        HttpRequest request = new HttpRequest(clientSocket);
        Thread thread = new Thread(request);
        thread.start();
    }
}

/**
 * HTTP 請求處理類
 * 實現 Runnable 接口以支援多執行緒處理請求
 */
final class HttpRequest implements Runnable {
    private static final String CRLF = "\r\n";
    private final Socket socket;
    
    // 請求處理相關的常量
    private static final String HTTP_OK = "200 OK";
    private static final String HTTP_NOT_FOUND = "404 Not Found";
    
    public HttpRequest(Socket socket) {
        this.socket = socket;
    }
    
    @Override
    public void run() {
        // 使用 try-with-resources 自動關閉資源
        try (
            socket;  // 客戶端連接的 Socket
            BufferedReader reader = createReader();
            DataOutputStream output = createOutputStream()
        ) {
            // 處理客戶端的 HTTP 請求
            processRequest(reader, output);
        } catch (Exception e) {
            // 如果處理過程中發生異常，記錄錯誤信息
            logError("Request processing error", e);
        }
    }
    
    /**
     * 創建輸入流讀取器
     */
    private BufferedReader createReader() throws IOException {
        return new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );
    }
    
    /**
     * 創建輸出流
     */
    private DataOutputStream createOutputStream() throws IOException {
        return new DataOutputStream(socket.getOutputStream());
    }
    
    /**
     * 處理 HTTP 請求
     * 持續處理來自同一個連接的請求，直到連接關閉或發生錯誤
     */
    private void processRequest(BufferedReader reader, DataOutputStream output) throws IOException {
        while (!socket.isClosed()) {  // 當 socket 連接仍然開啟時持續處理請求
            try {
                // 讀取 HTTP 請求的第一行（包含 HTTP 方法、URL 和協議版本）
                String requestLine = reader.readLine();
                if (requestLine == null || requestLine.isEmpty()) {
                    break;  // 如果請求行為空，表示客戶端可能已斷開連接
                }

                logRequest(requestLine);  // 記錄請求信息到日誌
                
                // 解析所有 HTTP 請求標頭
                Map<String, String> headers = parseHeaders(reader);
                // 檢查是否為 Keep-Alive 連接
                boolean keepAlive = isKeepAliveRequest(headers);

                // 驗證請求中的認證信息（用戶名和密碼）
                if (validateCredentials(requestLine)) {
                    String fileName = parseFileName(requestLine);  // 從請求 URL 解析要訪問的文件名
                    handleFileRequest(fileName, output, keepAlive);  // 處理文件請求
                } else {
                    sendAuthenticationError(output, keepAlive);  // 發送認證錯誤回應
                }

                output.flush();  // 確保所有資料都已發送到客戶端
                
                if (!keepAlive) {
                    break;
                }
            } catch (SocketException e) {
                break;  // 客戶端關閉連接時跳出循環
            } catch (Exception e) {
                logError("Request processing error", e);
                break;  // 發生錯誤時中斷處理
            }
        }
    }

    /**
     * 驗證 HTTP 請求中的用戶認證資訊
     */
    private boolean validateCredentials(String requestLine) {
        // 檢查請求行是否為空
        if (requestLine == null) return false;
        
        try {
            // 將請求行按空格分割成數組：[GET, /path?params, HTTP/1.1]
            String[] parts = requestLine.split("\\s+");
            if (parts.length < 2) return false;
            
            String urlPart = parts[1];
            // 確認 URL 中 ? 符號
            if (!urlPart.contains("?")) return false;
            
            // 分離並獲取查詢參數字串
            String queryString = urlPart.split("\\?")[1];
            // 解析查詢參數
            Map<String, String> params = parseQueryString(queryString);
            
            // 從參數中獲取用戶名和密碼
            String username = params.get("username");
            String password = params.get("password");
            
            // 驗證用戶名和密碼是否匹配
            return "admin".equals(username) && "123456".equals(password);
        } catch (Exception e) {
            // 如果解析過程中發生任何異常，返回認證失敗
            return false;
        }
    }
        
    /**
     * 解析 URL 查詢字串中的參數
     */
    private Map<String, String> parseQueryString(String queryString) {
        // 創建 HashMap 來存儲解析後的參數
        Map<String, String> params = new HashMap<>();
        
        // 依據 & 符號分割查詢字串，得到各個參數對
        // 例如：["username=admin", "password=123456"]
        String[] pairs = queryString.split("&");
        
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            
            // 確保參數對包含鍵和值兩個部分
            if (keyValue.length == 2) {
                // 將鍵值對放入 Map 中
                params.put(keyValue[0], keyValue[1]);
            }
        }
        return params;
    }

    /**
     * 解析 HTTP 請求標頭
     */
    private Map<String, String> parseHeaders(BufferedReader reader) throws IOException {
        // 創建 Map 來存儲解析後的標頭
        Map<String, String> headers = new HashMap<>();
        String line;
        
        // 持續讀取直到遇到空行（HTTP 標頭結束標誌）
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // 以冒號分割標頭行，限制分割次數為 2
            // 例如 "Host: localhost:8080" 會分割成 ["Host", "localhost:8080"]
            String[] parts = line.split(":", 2);
            
            // 確保標頭行包含名稱和值兩部分
            if (parts.length == 2) {
                // 儲存標頭，將名稱轉換為小寫並去除首尾空白
                headers.put(parts[0].trim().toLowerCase(), parts[1].trim());
            }
        }
        return headers;
    }

    /**
     * 檢查 HTTP 請求是否要求保持連接(Keep-Alive)
     */
    private boolean isKeepAliveRequest(Map<String, String> headers) {
        // 檢查 HTTP 版本和 Connection 標頭
        String connection = headers.get("connection");
        
        if (connection == null) {
            return true;  // HTTP/1.1 的預設行為
        }
        
        return !connection.equalsIgnoreCase("close");
    }

    // 處理檔案請求的方法
    private void handleFileRequest(String fileName, DataOutputStream output, 
                                   boolean keepAlive) throws IOException {
        File file = new File(fileName);
        if (!file.exists() || !file.isFile()) {
            send404Response(output, fileName, keepAlive);
            return;
        }

        // 取得檔案
        String fileType = contentType(fileName);
        // 讀取並發送檔案內容
        try (FileInputStream fileInput = new FileInputStream(file)) {
            byte[] fileContent = fileInput.readAllBytes();
            sendResponse(output, HTTP_OK, fileType, fileContent, keepAlive);
        }
    }
    
    // 發送 HTTP 回應的方法
    private void sendResponse(DataOutputStream output, String status, 
                            String contentType, byte[] body, 
                            boolean keepAlive) throws IOException {
        // 寫入標頭
        output.writeBytes("HTTP/1.1 " + status + CRLF);
        output.writeBytes("Content-Type: " + contentType + CRLF);
        output.writeBytes("Content-Length: " + body.length + CRLF);
        
        // 使用 Connection 標頭來控制 keep-alive
        if (keepAlive) {
            output.writeBytes("Connection: keep-alive" + CRLF);
            output.writeBytes("Keep-Alive: timeout=5, max=100" + CRLF);
        } else {
            output.writeBytes("Connection: close" + CRLF);
        }
        
        output.writeBytes(CRLF);
        output.write(body);
    }
    
    // 解析請求的文件名的方法
    private String parseFileName(String requestLine) {
        StringTokenizer tokens = new StringTokenizer(requestLine);
        tokens.nextToken(); // 跳過 GET
        String fileName = tokens.nextToken();
        
        // 移除查詢參數部分
        if (fileName.contains("?")) {
            fileName = fileName.split("\\?")[0];
        }
        
        // 如果請求路徑是 "/"，返回 index.html
        if (fileName.equals("/")) {
            return "./tests/index.html";
        }
        
        // 其他請求加上 tests 目錄前綴
        return "./tests" + fileName;
    }
    
    // 記錄請求信息的方法
    private void logRequest(String requestLine) {
        System.out.printf("[%s] %s%n", 
            new java.util.Date(), 
            requestLine != null ? requestLine : "無效請求"
        );
    }
    
    // 根據文件擴展名判斷Content-Type的輔助方法
    private static String contentType(String fileName) {
        if (fileName.endsWith(".htm") || fileName.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        
        // 返回標準的二進位流類型
        return "application/octet-stream";
    }
    
    /**
     * 記錄錯誤信息
     */
    private void logError(String message, Exception e) {
        System.err.printf("[%s] %s: %s%n", 
            new Date(), 
            message, 
            e.getMessage()
        );
    }
    
    // 發送 401 回應
    private void sendAuthenticationError(DataOutputStream output, 
                                       boolean keepAlive) throws IOException {
        String body = "<!DOCTYPE html>" +
                     "<html lang=\"zh-TW\">" +
                     "<head>" +
                     "<meta charset=\"UTF-8\">" +
                     "<title>401 未授權訪問</title>" +
                     "</head>" +
                     "<body>" +
                     "<h1>401 Unauthorized</h1>" +
                     "<p>帳號或密碼錯誤，請重新輸入。</p>" +
                     "</body></html>";
        sendResponse(output, "401 Unauthorized", "text/html; charset=UTF-8", body.getBytes(StandardCharsets.UTF_8), keepAlive);
    }

    // 發送 404 回應
    private void send404Response(DataOutputStream output, 
                               String fileName,
                               boolean keepAlive) throws IOException {
        String body = String.format(
            "<!DOCTYPE html>" +
            "<html lang=\"zh-TW\">" +
            "<head>" +
            "<meta charset=\"UTF-8\">" +
            "<title>找不到檔案</title>" +
            "</head>" +
            "<body>" +
            "<p>找不到檔案：%s</p>" +
            "<p>請確認檔案路徑是否正確。</p>" +
            "</body></html>",
            fileName
        );
        sendResponse(output, HTTP_NOT_FOUND, "text/html; charset=UTF-8", body.getBytes(StandardCharsets.UTF_8), keepAlive);
    }
}
