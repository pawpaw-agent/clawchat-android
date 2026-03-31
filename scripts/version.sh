#!/bin/bash

# ClawChat Version Management Script
# 用于管理版本号

set -e

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# 帮助信息
usage() {
    echo "Usage: $0 [OPTIONS] [VERSION]"
    echo ""
    echo "Options:"
    echo "  -g, --get               Get current version"
    echo "  -s, --set VERSION       Set version"
    echo "  -i, --increment TYPE    Increment version (major|minor|patch)"
    echo "  -h, --help              Show this help"
    echo ""
    echo "Examples:"
    echo "  $0 --get"
    echo "  $0 --set 1.2.0"
    echo "  $0 --increment patch"
}

# 获取当前版本
get_version() {
    local version_name=$(grep "versionName" "$PROJECT_DIR/app/build.gradle.kts" | head -1 | sed 's/.*"\([^"]*\)".*/\1/')
    echo "$version_name"
}

# 设置版本
set_version() {
    local new_version="$1"
    
    # 验证版本号格式
    if ! [[ "$new_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        echo "Error: Invalid version format. Use semantic versioning (e.g., 1.2.0)"
        exit 1
    fi
    
    # 解析版本号
    IFS='.' read -r major minor patch <<< "$new_version"
    
    # 计算 versionCode (major * 10000 + minor * 100 + patch)
    local version_code=$((major * 10000 + minor * 100 + patch))
    
    # 更新 build.gradle.kts
    sed -i "s/versionCode = .*/versionCode = $version_code/" "$PROJECT_DIR/app/build.gradle.kts"
    sed -i "s/versionName = .*/versionName = \"$new_version\"/" "$PROJECT_DIR/app/build.gradle.kts"
    
    echo "Version updated to $new_version (versionCode: $version_code)"
}

# 递增版本
increment_version() {
    local type="$1"
    local current_version=$(get_version)
    
    IFS='.' read -r major minor patch <<< "$current_version"
    
    case "$type" in
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        patch)
            patch=$((patch + 1))
            ;;
        *)
            echo "Error: Invalid increment type. Use major, minor, or patch"
            exit 1
            ;;
    esac
    
    local new_version="$major.$minor.$patch"
    set_version "$new_version"
}

# 参数解析
ACTION=""
VERSION=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -g|--get)
            ACTION="get"
            shift
            ;;
        -s|--set)
            ACTION="set"
            VERSION="$2"
            shift 2
            ;;
        -i|--increment)
            ACTION="increment"
            VERSION="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            VERSION="$1"
            shift
            ;;
    esac
done

cd "$PROJECT_DIR"

case "$ACTION" in
    get)
        get_version
        ;;
    set)
        if [ -z "$VERSION" ]; then
            echo "Error: Version number required"
            usage
            exit 1
        fi
        set_version "$VERSION"
        ;;
    increment)
        if [ -z "$VERSION" ]; then
            echo "Error: Increment type required (major|minor|patch)"
            usage
            exit 1
        fi
        increment_version "$VERSION"
        ;;
    *)
        # 默认获取版本
        get_version
        ;;
esac