#!/bin/bash
# 校园导航系统 - macOS / Linux 启动脚本
cd "$(dirname "$0")"

mkdir -p out/production/java

echo "正在编译..."
javac -encoding UTF-8 -d out/production/java java/src/CampusNavSystem.java
if [ $? -ne 0 ]; then
    echo ""
    echo "❌ 编译失败！请确认已安装 JDK（需要 JDK 8 或以上版本）"
    echo "   macOS 安装: brew install openjdk"
    echo "   下载地址: https://www.oracle.com/java/technologies/downloads/"
    read -p "按回车键退出..."
    exit 1
fi

echo "✅ 编译成功，启动服务器..."
java -cp out/production/java CampusNavSystem
