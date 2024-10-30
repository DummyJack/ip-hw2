import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public final class WebServer {
    private static final int DEFAULT_PORT = 6789;
    
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Web Server started, listening on port: " + port);
            
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    HttpRequest request = new HttpRequest(clientSocket);
                    Thread thread = new Thread(request);
                    thread.start();
                } catch (IOException e) {
                    System.err.println("Error handling client connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Server startup failed: " + e.getMessage());
        }
    }
}

// HTTP請求處理類，實現Runnable接口以支持多線程
final class HttpRequest implements Runnable {
    private static final String CRLF = "\r\n";
    private static final int BUFFER_SIZE = 8192;
    private final Socket socket;
    
    // 構造函數，接收客戶端socket連接
    public HttpRequest(Socket socket) {
        this.socket = socket;
    }
    
    // 實現Runnable接口的run方法
    @Override
    public void run() {
        try (socket;
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            
            processRequest(reader, output);
            
        } catch (Exception e) {
            System.err.println("Request processing error: " + e.getMessage());
        }
    }
    
    // 處理HTTP請求的主要方法
    private void processRequest(BufferedReader reader, DataOutputStream output) throws IOException {
        // 讀取並記錄請求信息
        String requestLine = reader.readLine();
        logRequest(requestLine);
        
        // 讀取所有請求頭
        while (reader.readLine().length() > 0) {
            // 跳過請求頭
        }
        
        // 解析請求的文件名
        String fileName = parseFileName(requestLine);
        
        // 處理文件請求
        handleFileRequest(fileName, output);
    }
    
    // 處理文件請求的方法
    private void handleFileRequest(String fileName, DataOutputStream output) throws IOException {
        try (FileInputStream fileInput = new FileInputStream(fileName)) {
            // 發送成功響應
            sendResponse(output, "200 OK", contentType(fileName), fileInput);
        } catch (FileNotFoundException e) {
            // 發送404響應
            String body = "<HTML><HEAD><TITLE>404</TITLE></HEAD>" +
                         "<BODY><H1>404</H1></BODY></HTML>";
            sendResponse(output, "404 Not Found", "text/html", body);
        }
    }
    
    // 發送HTTP響應的方法
    private void sendResponse(DataOutputStream output, String status, 
                            String contentType, FileInputStream fileInput) throws IOException {
        writeHeaders(output, status, contentType);
        sendFileContent(fileInput, output);
    }
    
    // 發送HTTP響應的方法
    private void sendResponse(DataOutputStream output, String status, 
                            String contentType, String body) throws IOException {
        writeHeaders(output, status, contentType);
        output.writeBytes(body);
    }
    
    // 發送HTTP響應頭的方法
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
        return "." + tokens.nextToken();
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
}
