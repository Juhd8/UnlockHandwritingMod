# 手写模拟器 - LSPosed 解锁模块

## 功能
+ 解锁 VIP 会员 - 所有 VIP 特权可用（去水印、去广告、自定义字体、多页保存等）
+ 去除水印 - 保存图片不再有"手写模拟器"水印
+ 永不过期 - VIP 设为 2099 年到期

## 目标应用
+ 包名: game.miplus.handwriting
+ 应用名: 手写模拟器 (Handwriting Simulator)
+ 测试版本: v3.4.8

## 前提条件
+ 已安装 LSPosed / LSPatch 框架
+ Android 8.0+ (API 26+)

## 构建方法

### 方式一：Android Studio 构建
1. 用 Android Studio 打开本模块目录
2. 等待 Gradle 同步完成
3. 点击 Build - Build Bundle(s)/APK(s) - Build APK
4. 生成的 APK 在 app/build/outputs/apk/debug/

### 方式二：命令行构建
需要安装 JDK 17+ 和 Android SDK
cd UnlockHandwritingMod
./gradlew assembleRelease

## 安装方法

### 使用 LSPosed（需 Root）
1. 安装编译好的 APK
2. 打开 LSPosed 管理器
3. 在模块中启用本模块
4. 勾选目标应用「手写模拟器」
5. 重启手写模拟器应用（或强行停止后重新打开）

### 使用 LSPatch（免 Root）
1. 用 LSPatch 对原版手写模拟器 APK 进行修补
2. 在本模块中启用勾选目标
3. 安装修补后的 APK

## Hook 原理

### 1. JavaScript 注入（主要途径）
当 UniApp 的 WebView 加载页面时，自动注入 JavaScript 代码：
+ 拦截 miplus.getUserInfo() - 强制返回 vip: true
+ 拦截 miplus.getGuestInfo() - 强制返回 vip: true
+ 拦截 uni.getStorageSync() - 读取时注入 VIP 字段
+ 拦截 uni.setStorageSync() - 写入时强制 VIP
+ 拦截 JSON.parse() - 解析包含 vip 的 JSON 时强制设为 true
+ 拦截 plus.storage.getItem() - 原生存储层面注入

### 2. SharedPreferences 拦截（备选保障）
+ Hook SharedPreferences.getString() - 在存储层修改用户数据
+ Hook SharedPreferences.Editor.putString() - 在写入时注入 VIP

### 3. JSON 解析拦截（最终保障）
+ Hook JSONObject.optBoolean/getBoolean - 任何 JSON 中读取 vip 字段都返回 true

## 注意
+ 本模块仅供学习交流使用
+ 请勿用于商业用途
+ VIP 解锁是客户端修改，不影响服务端数据
+ 部分服务端校验的功能可能无法解锁（如云同步等）

## 文件结构
UnlockHandwritingMod/
  build.gradle.kts          根构建配置
  settings.gradle.kts       项目设置
  gradle.properties         Gradle 属性
  app/
    build.gradle.kts        模块构建（含 LSPosed API 依赖）
    proguard-rules.pro      混淆规则
    src/main/
      AndroidManifest.xml   模块清单
      res/xml/xposed_init   Xposed 入口指向
      java/game/miplus/handwriting/hook/
        MainHook.java       核心 Hook 代码

## 更新日志
+ v1.0.0 (2026-06-10): 初始版本，支持 v3.4.8
  VIP 解锁 + 去除水印 + SharedPreferences/JSON 多重保障
