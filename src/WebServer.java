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
    private static final int BUFFER_SIZE = 8192;
    private final Socket socket;
    
    // 請求處理相關的常量
    private static final String HTTP_OK = "200 OK";
    private static final String HTTP_NOT_FOUND = "404 Not Found";
    
    public HttpRequest(Socket socket) {
        this.socket = socket;
    }
    
    @Override
    public void run() {
        try (socket;
             BufferedReader reader = createReader();
             DataOutputStream output = createOutputStream()) {
            processRequest(reader, output);
        } catch (Exception e) {
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
     */
    private void processRequest(BufferedReader reader, DataOutputStream output) throws IOException {
        String requestLine = reader.readLine();
        logRequest(requestLine);
        skipHeaders(reader);
        
        String fileName = parseFileName(requestLine);
        handleFileRequest(fileName, output);
    }
    
    /**
     * 跳過請求標頭
     */
    private void skipHeaders(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            // 跳過所有請求標頭
        }
    }
    
    // 處理文件請求的方法
    private void handleFileRequest(String fileName, DataOutputStream output) throws IOException {
        File file = new File(fileName);
        if (!file.exists() || !file.isFile()) {
            // 發送404回應
            String body = "<HTML><HEAD><TITLE>404 Not Found</TITLE></HEAD>" +
                         "<BODY><H1>404 Not Found</H1>" +
                         "<p>The requested file " + fileName + " was not found on this server.</p>" +
                         "</BODY></HTML>";
            sendResponse(output, HTTP_NOT_FOUND, "text/html", body);
            return;
        }
        
        try (FileInputStream fileInput = new FileInputStream(file)) {
            // 發送成功回應
            sendResponse(output, HTTP_OK, contentType(fileName), fileInput);
        }
    }
    
    // 發送 HTTP 回應的方法
    private void sendResponse(DataOutputStream output, String status, 
                            String contentType, FileInputStream fileInput) throws IOException {
        writeHeaders(output, status, contentType);
        sendFileContent(fileInput, output);
    }
    
    // 發送 HTTP 回應的方法
    private void sendResponse(DataOutputStream output, String status, 
                            String contentType, String body) throws IOException {
        writeHeaders(output, status, contentType);
        output.writeBytes(body);
    }
    
    // 發送 HTTP 回應頭的方法
    private void writeHeaders(DataOutputStream output, String status, 
                            String contentType) throws IOException {
        output.writeBytes("HTTP/1.1 " + status + CRLF);
        output.writeBytes("Content-Type: " + contentType + CRLF);
        output.writeBytes("Connection: close" + CRLF);
        output.writeBytes(CRLF);
    }
    
    // 發送文件內容的方法
    private void sendFileContent(FileInputStream fis, DataOutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytes;
        while ((bytes = fis.read(buffer)) != -1) {
            output.write(buffer, 0, bytes);
        }
    }
    
    // 解析請求的文件名的方法
    private String parseFileName(String requestLine) {
        StringTokenizer tokens = new StringTokenizer(requestLine);
        tokens.nextToken(); // 跳過 GET
        String fileName = tokens.nextToken();
        
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
}
