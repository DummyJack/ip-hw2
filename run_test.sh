#!/bin/bash

# 檢查 Java 是否安裝
echo -e "${BLUE}檢查 Java 環境...${NC}"
if ! command -v java &> /dev/null; then
    echo "錯誤: 需要安裝 Java"
    exit 1
fi

# 編譯 Java 文件
echo -e "編譯 Java 文件..."
javac -d bin src/WebServer.java

# 檢查編譯是否成功
if [ $? -ne 0 ]; then
    echo "編譯失敗"
    exit 1
fi

# 啟動服務器
echo -e "訪問 http://localhost:6789"
echo -e "按 Ctrl+C 停止服務器"

# 運行服務器
java -cp bin WebServer
