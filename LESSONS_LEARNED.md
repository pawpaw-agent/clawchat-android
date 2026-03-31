# ClawChat 开发经验总结

## 📚 成功经验

### 1. 架构设计
**Clean Architecture + MVVM**
- 清晰的层次分离 (data/domain/ui)
- ViewModel 管理状态，Repository 管理数据
- UseCase 封装业务逻辑（可选）

**最佳实践**:
- ✅ 单向数据流 (StateFlow)
- ✅ 不可变 UI State
- ✅ 依赖注入 (Hilt)
- ✅ 模块化设计

### 2. Compose 最佳实践
**声明式 UI**
- 使用 `remember` 缓存计算结果
- 使用 `derivedStateOf` 减少重组
- 使用 `key` 优化列表性能
- 使用 `LaunchedEffect` 处理副作用

**性能优化**:
- ✅ 最小化重组范围
- ✅ 使用稳定的 key
- ✅ 避免过度使用 StateFlow

### 3. 测试策略
**金字塔测试**
- 单元测试: 134+ 用例
- 集成测试: 关键流程
- UI 测试: 核心界面

**测试工具**:
- ✅ TestCoroutinesRule
- ✅ TestDataFactory
- ✅ MemoryProfiler
- ✅ BenchmarkHelper

### 4. 日志管理
**统一日志系统**
- 所有日志使用 AppLog
- Debug 日志仅在 Debug 构建输出
- 分类日志级别 (v/d/i/w/e)
- 上下文信息完整

**迁移经验**:
- 从 Log.* 迁移到 AppLog.*
- 175 个日志调用统一
- 生产环境性能提升

### 5. 国际化
**多语言支持**
- 6 种语言 (zh, en, ja, ko, fr, de)
- 100+ 字符串翻译
- 占位符格式化 (%s, %d)

**最佳实践**:
- ✅ 使用 strings.xml
- ✅ 提供上下文注释
- ✅ 测试不同语言布局

---

## ⚠️ 改进机会

### 1. 错误消息国际化
**问题**: 部分错误消息硬编码在 ViewModel

**改进方案**:
```kotlin
// 不好的做法
_state.update { it.copy(error = "发送失败") }

// 好的做法
val errorMessage = context.getString(R.string.error_send_failed)
_state.update { it.copy(error = errorMessage) )
```

**行动计划**:
- v1.3.0 移除所有硬编码字符串
- 添加 Context 到 ViewModel
- 使用 ResourceResolver 工具类

### 2. TODO 管理
**问题**: 2 个 TODO 未处理

**改进方案**:
- 使用 TODO tracking 工具
- 定期审查 TODO 列表
- 在 sprint 中分配时间

### 3. 测试覆盖
**问题**: 部分 Util 类未完全覆盖

**改进方案**:
- 增加边界测试
- 增加异常路径测试
- 使用 mutation testing

### 4. 代码审查
**问题**: 部分代码缺少审查

**改进方案**:
- 强制 PR 审查
- 使用审查清单
- 自动化代码质量检查

---

## 💡 最佳实践

### 1. 命名规范
**文件命名**:
- ViewModel: `*ViewModel.kt`
- Repository: `*Repository.kt`
- UseCase: `*UseCase.kt`
- Util: `*Utils.kt`

**代码命名**:
- 类: PascalCase
- 函数: camelCase
- 常量: UPPER_SNAKE_CASE
- 变量: camelCase, 有意义

### 2. 文件组织
**标准结构**:
```
com.openclaw.clawchat/
├── data/          # 数据层
├── domain/        # 业务层
├── ui/            # UI 层
│   ├── components/
│   ├── screens/
│   └── state/
├── network/       # 网络
├── security/      # 安全
└── util/          # 工具
```

### 3. 依赖管理
**版本管理**:
- 使用 libs.versions.toml
- 定期更新依赖
- 安全漏洞检查

**依赖原则**:
- 最小依赖原则
- 避免重复依赖
- 使用稳定版本

### 4. Git 提交
**提交规范**:
```
type: subject (Task X-Y)

Body text

Footer
```

**类型**:
- feat: 新功能
- fix: 修复
- refactor: 重构
- docs: 文档
- test: 测试
- style: 格式

### 5. 文档编写
**必须文档**:
- README.md: 项目介绍
- CHANGELOG.md: 变更记录
- ARCHITECTURE.md: 架构设计
- CONTRIBUTING.md: 贡献指南

**代码文档**:
- 类文档 (KDoc)
- 公共 API 文档
- 复杂逻辑注释

---

## 📈 性能优化经验

### 1. Compose 性能
**减少重组**:
- 使用 `remember` 缓存
- 使用 `derivedStateOf`
- 使用 `key` 优化列表
- 避免不必要的 lambda 创建

### 2. 内存管理
**避免泄漏**:
- viewModelScope 自动取消
- onCleared() 清理资源
- DisposableEffect 清理副作用

### 3. 网络优化
**减少延迟**:
- WebSocket 长连接
- 心跳保活
- 断线重连

### 4. 启动优化
**快速启动**:
- SplashScreen API
- 延迟初始化
- 异步加载

---

## 🎓 团队协作

### 1. 代码审查清单
- [ ] 代码符合规范
- [ ] 有单元测试
- [ ] 文档已更新
- [ ] 无编译警告
- [ ] CI 通过

### 2. PR 模板
```markdown
## 变更说明
描述本次变更的内容

## 测试计划
描述如何测试

## 截图
UI 变更截图

## Checklist
- [ ] 单元测试通过
- [ ] 代码审查完成
- [ ] 文档已更新
```

### 3. Issue 模板
```markdown
## 描述
问题描述

## 复现步骤
1. 步骤 1
2. 步骤 2

## 期望结果
期望的行为

## 实际结果
实际的行为

## 环境
- 设备: 
- 系统: 
- 版本: 
```

---

## 🚀 发布经验

### 发布前检查
- [ ] 版本号更新
- [ ] CHANGELOG 更新
- [ ] 签名配置
- [ ] ProGuard 规则
- [ ] CI 绿色
- [ ] 测试通过

### 发布后监控
- [ ] 崩溃率监控
- [ ] 用户反馈
- [ ] 性能指标
- [ ] 下载量

---

**ClawChat Team**  
2026-04-01