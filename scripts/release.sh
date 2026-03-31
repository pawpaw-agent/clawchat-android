#!/bin/bash

# ClawChat Release Script
# 用于准备和发布新版本

set -e

# 配置
APP_NAME="ClawChat"
PACKAGE_NAME="com.openclaw.clawchat"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 帮助信息
usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -v, --version VERSION   Specify version (e.g., 1.2.0)"
    echo "  -b, --build TYPE        Build type (debug|release)"
    echo "  -t, --tag               Create git tag"
    echo "  -p, --push              Push to remote"
    echo "  -h, --help              Show this help"
    echo ""
    echo "Examples:"
    echo "  $0 -v 1.2.0 -b release -t -p"
}

# 参数解析
VERSION=""
BUILD_TYPE="release"
CREATE_TAG=false
PUSH=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--version)
            VERSION="$2"
            shift 2
            ;;
        -b|--build)
            BUILD_TYPE="$2"
            shift 2
            ;;
        -t|--tag)
            CREATE_TAG=true
            shift
            ;;
        -p|--push)
            PUSH=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            usage
            exit 1
            ;;
    esac
done

# 验证版本号
if [ -z "$VERSION" ]; then
    echo -e "${RED}Error: Version number is required${NC}"
    usage
    exit 1
fi

# 验证版本号格式
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo -e "${RED}Error: Invalid version format. Use semantic versioning (e.g., 1.2.0)${NC}"
    exit 1
fi

cd "$PROJECT_DIR"

echo -e "${GREEN}=== ClawChat Release Script ===${NC}"
echo "Version: $VERSION"
echo "Build: $BUILD_TYPE"
echo "Create Tag: $CREATE_TAG"
echo "Push: $PUSH"
echo ""

# 1. 检查工作目录
echo -e "${YELLOW}Checking working directory...${NC}"
if [[ -n $(git status -s) ]]; then
    echo -e "${RED}Error: Working directory is not clean${NC}"
    echo "Please commit or stash changes first"
    exit 1
fi

# 2. 更新版本号
echo -e "${YELLOW}Updating version to $VERSION...${NC}"
./scripts/version.sh "$VERSION"

# 3. 运行测试
echo -e "${YELLOW}Running tests...${NC}"
./gradlew test --no-daemon

if [ $? -ne 0 ]; then
    echo -e "${RED}Tests failed!${NC}"
    exit 1
fi

# 4. 构建 APK
echo -e "${YELLOW}Building $BUILD_TYPE APK...${NC}"
if [ "$BUILD_TYPE" = "release" ]; then
    ./gradlew assembleRelease --no-daemon
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
else
    ./gradlew assembleDebug --no-daemon
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}Build failed! APK not found${NC}"
    exit 1
fi

# 5. 提交更改
echo -e "${YELLOW}Committing changes...${NC}"
git add -A
git commit -m "release: v$VERSION"

# 6. 创建标签
if [ "$CREATE_TAG" = true ]; then
    echo -e "${YELLOW}Creating git tag v$VERSION...${NC}"
    git tag -a "v$VERSION" -m "Release v$VERSION"
fi

# 7. 推送
if [ "$PUSH" = true ]; then
    echo -e "${YELLOW}Pushing to remote...${NC}"
    git push origin master
    
    if [ "$CREATE_TAG" = true ]; then
        git push origin "v$VERSION"
    fi
fi

echo ""
echo -e "${GREEN}=== Release v$VERSION completed! ===${NC}"
echo "APK: $APK_PATH"
echo ""

# 显示 APK 信息
APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
echo "APK Size: $APK_SIZE"

# 完成
echo ""
echo "Next steps:"
echo "1. Test the APK on multiple devices"
echo "2. Update CHANGELOG.md"
echo "3. Create GitHub Release"
echo "4. Update documentation"