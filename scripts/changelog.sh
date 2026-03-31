#!/bin/bash

# ClawChat Changelog Generator
# 自动生成变更日志

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CHANGELOG_FILE="$PROJECT_DIR/CHANGELOG.md"

# 帮助信息
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -f, --from TAG          From tag"
    echo "  -t, --to TAG            To tag (default: HEAD)"
    echo "  -o, --output FILE       Output file (default: CHANGELOG.md)"
    echo "  -a, --append            Append to existing changelog"
    echo "  -h, --help              Show this help"
}

# 参数解析
FROM_TAG=""
TO_TAG="HEAD"
OUTPUT_FILE=""
APPEND=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -f|--from)
            FROM_TAG="$2"
            shift 2
            ;;
        -t|--to)
            TO_TAG="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        -a|--append)
            APPEND=true
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

# 获取版本号
get_version() {
    ./scripts/version.sh --get
}

# 生成变更日志
generate_changelog() {
    local version=$(get_version)
    local date=$(date +"%Y-%m-%d")
    
    echo "## [$version] - $date"
    echo ""
    
    # 获取提交记录
    if [ -n "$FROM_TAG" ]; then
        local commits=$(git log --pretty=format:"- %s" "$FROM_TAG".."$TO_TAG")
    else
        local commits=$(git log -1 --pretty=format:"- %s")
    fi
    
    # 分类提交
    local features=""
    local fixes=""
    local docs=""
    local others=""
    
    while IFS= read -r commit; do
        if [[ "$commit" == *"feat"* ]] || [[ "$commit" == *"feature"* ]]; then
            features="$features\n$commit"
        elif [[ "$commit" == *"fix"* ]] || [[ "$commit" == *"bug"* ]]; then
            fixes="$fixes\n$commit"
        elif [[ "$commit" == *"doc"* ]]; then
            docs="$docs\n$commit"
        else
            others="$others\n$commit"
        fi
    done <<< "$commits"
    
    # 输出分类
    if [ -n "$features" ]; then
        echo "### Features"
        echo -e "$features"
        echo ""
    fi
    
    if [ -n "$fixes" ]; then
        echo "### Bug Fixes"
        echo -e "$fixes"
        echo ""
    fi
    
    if [ -n "$docs" ]; then
        echo "### Documentation"
        echo -e "$docs"
        echo ""
    fi
    
    if [ -n "$others" ]; then
        echo "### Other Changes"
        echo -e "$others"
        echo ""
    fi
}

# 主逻辑
if [ -n "$OUTPUT_FILE" ]; then
    CHANGELOG_FILE="$OUTPUT_FILE"
fi

if [ "$APPEND" = true ] && [ -f "$CHANGELOG_FILE" ]; then
    # 临时文件
    TEMP_FILE=$(mktemp)
    generate_changelog > "$TEMP_FILE"
    cat "$CHANGELOG_FILE" >> "$TEMP_FILE"
    mv "$TEMP_FILE" "$CHANGELOG_FILE"
else
    generate_changelog > "$CHANGELOG_FILE"
fi

echo "Changelog generated: $CHANGELOG_FILE"