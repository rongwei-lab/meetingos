#!/bin/bash

# 座位表导出功能依赖安装脚本
# 用途：安装 html2canvas 和 jspdf 依赖包

set -e

echo "=========================================="
echo "座位表导出功能 - 依赖安装"
echo "=========================================="
echo ""

cd "$(dirname "$0")/../meetingos-admin"

echo "📦 当前目录: $(pwd)"
echo ""

# 检查网络连接
echo "🔍 检查网络连接..."
if ping -c 1 registry.npmjs.org &> /dev/null; then
    echo "✅ npm 官方源可访问"
    REGISTRY=""
elif ping -c 1 registry.npmmirror.com &> /dev/null; then
    echo "⚠️  npm 官方源不可访问，使用淘宝镜像"
    REGISTRY="--registry=https://registry.npmmirror.com"
else
    echo "❌ 网络连接异常，无法访问 npm 源"
    exit 1
fi

echo ""
echo "📥 安装依赖包..."
echo "   - html2canvas@^1.4.1"
echo "   - jspdf@^4.2.1"
echo ""

npm install html2canvas@^1.4.1 jspdf@^4.2.1 $REGISTRY

echo ""
echo "✅ 依赖安装完成！"
echo ""
echo "🎉 座位表导出功能已就绪，可以在座位管理页面使用"
echo ""
