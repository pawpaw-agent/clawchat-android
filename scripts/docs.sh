#!/bin/bash

# ClawChat Documentation Generator
# 生成 API 文档和代码文档

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DOCS_DIR="$PROJECT_DIR/docs"

# 帮助信息
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -o, --output DIR        Output directory (default: docs/)"
    echo "  -f, --format FORMAT     Output format (html|markdown)"
    echo "  -c, --clean             Clean output directory first"
    echo "  -h, --help              Show this help"
}

# 参数解析
OUTPUT_DIR=""
FORMAT="markdown"
CLEAN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -o|--output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        -f|--format)
            FORMAT="$2"
            shift 2
            ;;
        -c|--clean)
            CLEAN=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

cd "$PROJECT_DIR"

# 设置输出目录
if [ -z "$OUTPUT_DIR" ]; then
    OUTPUT_DIR="$DOCS_DIR"
fi

# 清理
if [ "$CLEAN" = true ]; then
    echo "Cleaning $OUTPUT_DIR..."
    rm -rf "$OUTPUT_DIR"
fi

# 创建输出目录
mkdir -p "$OUTPUT_DIR"

echo "Generating documentation..."
echo "Output: $OUTPUT_DIR"
echo "Format: $FORMAT"

# 生成 KDoc 文档
# Note: 实际项目需要配置 Dokka 插件
# 这里是简化版本

# 1. 收集所有 Kotlin 文件
echo "Collecting Kotlin files..."
KOTLIN_FILES=$(find app/src/main/java -name "*.kt" -type f)

# 2. 生成文档索引
cat > "$OUTPUT_DIR/INDEX.md" << 'EOF'
# ClawChat Documentation

## API Reference

- [API Reference](../API_REFERENCE.md)

## Guides

- [Architecture](../ARCHITECTURE.md)
- [Contributing](../CONTRIBUTING.md)
- [Changelog](../CHANGELOG.md)

## Troubleshooting

- [Troubleshooting Guide](../TROUBLESHOOTING.md)

## Roadmap

- [v1.3.0 Roadmap](../v1.3.0-roadmap.md)

## Lessons Learned

- [Lessons Learned](../LESSONS_LEARNED.md)
EOF

# 3. 统计文档
TOTAL_FILES=$(echo "$KOTLIN_FILES" | wc -l)
TOTAL_DOCS=$(find "$OUTPUT_DIR" -name "*.md" -type f | wc -l)

echo ""
echo "Documentation generated successfully!"
echo "Kotlin files: $TOTAL_FILES"
echo "Documentation files: $TOTAL_DOCS"