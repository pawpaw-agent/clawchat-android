// ClawChat State Management Module
// 导出所有状态管理相关类和接口

package ui.state

// UI 状态数据模型
// - MainUiState: 主界面状态（连接状态、会话列表、当前会话）
// - SessionUiState: 会话界面状态（消息列表、输入状态）
// - SettingsUiState: 设置界面状态（网关配置）
// - PairingUiState: 配对界面状态

// 领域模型
// - ConnectionStatus: 连接状态密封类
// - SessionUi: 会话数据模型
// - SessionStatus: 会话状态枚举
// - GatewayConfigUi: 网关配置数据模型
// - PairingStatus: 配对状态枚举

// ViewModel
// - MainViewModel: 主界面 ViewModel，管理连接和会话列表
// - SessionViewModel: 会话界面 ViewModel，管理消息和输入

// UI 事件
// - UiEvent: 主界面事件（导航、错误、成功）
// - SessionUiEvent: 会话界面事件（滚动、错误）
