package game.miplus.handwriting.hook;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 手写模拟器 LSPosed Hook 模块
 * 功能：
 * 1. 解锁VIP会员（user.vip = true, guest.vip = true）
 * 2. 去除保存图片时的水印
 * 3. 解锁所有VIP特权
 *
 * 目标版本：v3.4.8
 * 包名：game.miplus.handwriting
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "HandwritingUnlock";
    private static final String TARGET_PACKAGE = "game.miplus.handwriting";

    // ============================================================
    // 注入的 JavaScript: 直接修改 UniApp 运行时的 VIP 检查与水印
    // ============================================================
    private static final String INJECT_JS =
        "(function(){" +
        "  if (window.__hooked) return; window.__hooked = true;" +
        "" +
        "  var TAG = '[HOOK]';" +
        "" +
        "  // ===== 1. 拦截 miplus 插件的方法 =====" +
        "  var _miplus = null;" +
        "  var checkMiplus = setInterval(function() {" +
        "    if (typeof miplus !== 'undefined' && miplus) {" +
        "      _miplus = miplus;" +
        "      clearInterval(checkMiplus);" +
        "" +
        "      // 1a. Hook getUserInfo — 返回 VIP 用户" +
        "      var origGetUser = miplus.getUserInfo;" +
        "      miplus.getUserInfo = function() {" +
        "        var user = origGetUser ? origGetUser() : null;" +
        "        try {" +
        "          if (!user || typeof user !== 'object') {" +
        "            user = { id: 'unlocked', nickname: 'VIP用户', vip: true, expiredTime: 4099680000 };" +
        "          } else {" +
        "            user.vip = true;" +
        "            user.svip = true;" +
        "            user.vipLevel = 999;" +
        "            user.expiredTime = 4099680000;" +
        "          }" +
        "        } catch(e) {}" +
        "        return user;" +
        "      };" +
        "" +
        "      // 1b. Hook getUserInfoObject (if exists)" +
        "      if (miplus.getUserInfoObject) {" +
        "        var origGetUserObj = miplus.getUserInfoObject;" +
        "        miplus.getUserInfoObject = function() {" +
        "          var user = origGetUserObj ? origGetUserObj() : null;" +
        "          if (user) { user.vip = true; user.expiredTime = 4099680000; }" +
        "          return user;" +
        "        };" +
        "      }" +
        "" +
        "      // 1c. Hook getGuestInfo — 返回 VIP 游客" +
        "      var origGetGuest = miplus.getGuestInfo;" +
        "      miplus.getGuestInfo = function() {" +
        "        var guest = origGetGuest ? origGetGuest() : null;" +
        "        try {" +
        "          if (!guest || typeof guest !== 'object') {" +
        "            guest = { vip: true };" +
        "          } else {" +
        "            guest.vip = true;" +
        "          }" +
        "        } catch(e) {}" +
        "        return guest;" +
        "      };" +
        "" +
        "      // 1d. Hook isGuestVip — 始终返回 true" +
        "      if (miplus.isGuestVip) {" +
        "        miplus.isGuestVip = function() { return true; };" +
        "      }" +
        "" +
        "      // 1e. Hook saveUserInfo — 拦截保存，强制 VIP" +
        "      var origSaveUser = miplus.saveUserInfo;" +
        "      miplus.saveUserInfo = function(data, cb) {" +
        "        try {" +
        "          if (typeof data === 'string') {" +
        "            var obj = JSON.parse(data);" +
        "            obj.vip = true;" +
        "            obj.expiredTime = 4099680000;" +
        "            data = JSON.stringify(obj);" +
        "          } else if (data && typeof data === 'object') {" +
        "            data.vip = true;" +
        "            data.expiredTime = 4099680000;" +
        "          }" +
        "        } catch(e) {}" +
        "        if (origSaveUser) origSaveUser(data, cb);" +
        "      };" +
        "    }" +
        "  }, 100);" +
        "" +
        "  // ===== 2. 拦截 uni 存储系统 =====" +
        "  if (typeof uni !== 'undefined') {" +
        "    // 2a. Hook getStorageSync" +
        "    var origGet = uni.getStorageSync;" +
        "    uni.getStorageSync = function(key) {" +
        "      var val = origGet ? origGet(key) : null;" +
        "      try {" +
        "        if (key === 'user_info' && val) {" +
        "          var obj = JSON.parse(val);" +
        "          obj.vip = true;" +
        "          obj.svip = true;" +
        "          obj.expiredTime = 4099680000;" +
        "          val = JSON.stringify(obj);" +
        "        }" +
        "        if (key === 'guest_info' && val) {" +
        "          var obj = JSON.parse(val);" +
        "          obj.vip = true;" +
        "          val = JSON.stringify(obj);" +
        "        }" +
        "      } catch(e) {}" +
        "      return val;" +
        "    };" +
        "" +
        "    // 2b. Hook getStorage (async)" +
        "    var origGetAsync = uni.getStorage;" +
        "    uni.getStorage = function(opts) {" +
        "      if (opts && opts.success) {" +
        "        var origSuccess = opts.success;" +
        "        opts.success = function(res) {" +
        "          try {" +
        "            if (opts.key === 'user_info' && res && res.data) {" +
        "              if (typeof res.data === 'string') {" +
        "                var obj = JSON.parse(res.data);" +
        "                obj.vip = true;" +
        "                obj.expiredTime = 4099680000;" +
        "                res.data = JSON.stringify(obj);" +
        "              } else if (typeof res.data === 'object') {" +
        "                res.data.vip = true;" +
        "                res.data.expiredTime = 4099680000;" +
        "              }" +
        "            }" +
        "            if (opts.key === 'guest_info' && res && res.data) {" +
        "              if (typeof res.data === 'object') res.data.vip = true;" +
        "            }" +
        "          } catch(e) {}" +
        "          origSuccess(res);" +
        "        };" +
        "      }" +
        "      if (origGetAsync) origGetAsync(opts);" +
        "    };" +
        "" +
        "    // 2c. Hook uni.setStorageSync — 强制 VIP" +
        "    var origSet = uni.setStorageSync;" +
        "    uni.setStorageSync = function(key, data) {" +
        "      try {" +
        "        if (key === 'user_info' && data) {" +
        "          if (typeof data === 'string') {" +
        "            var obj = JSON.parse(data);" +
        "            obj.vip = true;" +
        "            obj.expiredTime = 4099680000;" +
        "            data = JSON.stringify(obj);" +
        "          } else if (typeof data === 'object') {" +
        "            data.vip = true;" +
        "            data.expiredTime = 4099680000;" +
        "          }" +
        "        }" +
        "      } catch(e) {}" +
        "      if (origSet) origSet(key, data);" +
        "    };" +
        "  }" +
        "" +
        "  // ===== 3. 拦截所有 JSON 解析中的 vip 字段 =====（如果可用）" +
        "  var origJSONParse = JSON.parse;" +
        "  JSON.parse = function(text, reviver) {" +
        "    var result = origJSONParse(text, reviver);" +
        "    try {" +
        "      if (result && typeof result === 'object' && !Array.isArray(result)) {" +
        "        if (result.hasOwnProperty('vip') || result.hasOwnProperty('expiredTime')) {" +
        "          result.vip = true;" +
        "          result.expiredTime = 4099680000;" +
        "        }" +
        "      }" +
        "    } catch(e) {}" +
        "    return result;" +
        "  };" +
        "" +
        "  // ===== 4. 拦截 plus 存储（UniApp 原生存储）=====" +
        "  if (typeof plus !== 'undefined' && plus.storage) {" +
        "    var origGetItem = plus.storage.getItem;" +
        "    plus.storage.getItem = function(key) {" +
        "      var val = origGetItem ? origGetItem(key) : null;" +
        "      try {" +
        "        if ((key === 'user_info' || key === 'guest_info') && val) {" +
        "          var obj = JSON.parse(val);" +
        "          obj.vip = true;" +
        "          obj.expiredTime = 4099680000;" +
        "          val = JSON.stringify(obj);" +
        "        }" +
        "      } catch(e) {}" +
        "      return val;" +
        "    };" +
        "  }" +
        "" +
        "  // ===== 5. 定时修复（有些页面会重新初始化）=====" +
        "  setInterval(function() {" +
        "    if (typeof miplus !== 'undefined' && miplus) {" +
        "      if (miplus.getUserInfo) {" +
        "        var u = miplus.getUserInfo();" +
        "        if (u) { u.vip = true; u.expiredTime = 4099680000; }" +
        "      }" +
        "      if (miplus.getGuestInfo) {" +
        "        var g = miplus.getGuestInfo();" +
        "        if (g) { g.vip = true; }" +
        "      }" +
        "    }" +
        "  }, 2000);" +
        "" +
        "  console.log(TAG, 'Hook injected successfully!');" +
        "})();";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }

        XposedBridge.log(TAG + ": Loading hook for " + TARGET_PACKAGE);

        // ===== Hook 1: WebView JavaScript 注入 =====
        hookWebViewJsInjection(lpparam);

        // ===== Hook 2: 拦截 SharedPreferences 中的用户数据 =====
        hookSharedPreferences(lpparam);

        // ===== Hook 3: 拦截 JSON 解析 =====
        hookJsonParsing(lpparam);

        XposedBridge.log(TAG + ": All hooks installed successfully!");
    }

    /**
     * Hook WebView 的页面加载完成事件，注入 JavaScript 代码
     * 这样当 UniApp 的 WebView 加载页面时，我们的 JS 代码就会执行
     */
    private void hookWebViewJsInjection(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook WebViewClient.onPageFinished 用于注入 JS
        try {
            XposedHelpers.findAndHookMethod(
                WebViewClient.class,
                "onPageFinished",
                WebView.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        WebView webView = (WebView) param.args[0];
                        String url = (String) param.args[1];
                        if (url != null && isAppUrl(url)) {
                            injectJsToWebView(webView);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed hooking WebViewClient: " + e.getMessage());
        }

        // Hook WebView.loadUrl — 捕获所有加载事件
        try {
            XposedHelpers.findAndHookMethod(
                WebView.class,
                "loadUrl",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        WebView webView = (WebView) param.thisObject;
                        String url = (String) param.args[0];
                        if (url != null && isAppUrl(url)) {
                            // 延迟执行以确保页面已渲染
                            webView.postDelayed(() -> injectJsToWebView(webView), 500);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed hooking loadUrl: " + e.getMessage());
        }

        // Hook Activity.onCreate 来查找 WebView
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class,
                "onCreate",
                android.os.Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        activity.getWindow().getDecorView().postDelayed(() -> {
                            injectJsToAllWebViews(activity);
                        }, 1000);
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed hooking Activity.onCreate: " + e.getMessage());
        }
    }

    /**
     * Hook SharedPreferences 拦截用户数据读取
     * 这样即便 JS 注入未能成功，也能在存储层确保 VIP=true
     */
    private void hookSharedPreferences(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook SharedPreferences.getString()
            XposedHelpers.findAndHookMethod(
                SharedPreferences.class,
                "getString",
                String.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[0];
                        String result = (String) param.getResult();
                        if (result != null && (containsIgnoreCase(key, "user_info")
                                || containsIgnoreCase(key, "guest_info")
                                || containsIgnoreCase(key, "user")
                                || containsIgnoreCase(key, "member"))) {
                            try {
                                // 尝试解析为 JSON 并注入 VIP
                                String modified = injectVipIntoJson(result);
                                if (!modified.equals(result)) {
                                    param.setResult(modified);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed hooking SharedPreferences: " + e.getMessage());
        }

        // Hook SharedPreferences.Editor.putString() 来在写入时强制 VIP
        try {
            XposedHelpers.findAndHookMethod(
                SharedPreferences.Editor.class,
                "putString",
                String.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[0];
                        String value = (String) param.args[1];
                        if (value != null && (containsIgnoreCase(key, "user_info")
                                || containsIgnoreCase(key, "guest_info"))) {
                            try {
                                param.args[1] = injectVipIntoJson(value);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed hooking SharedPreferences.Editor: " + e.getMessage());
        }
    }

    /**
     * Hook JSON 解析 — 当应用解析任何包含 vip 字段的 JSON 时强制设为 true
     */
    private void hookJsonParsing(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                JSONObject.class,
                "optBoolean",
                String.class,
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[0];
                        if (containsIgnoreCase(key, "vip")) {
                            param.setResult(true);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed hooking JSONObject.optBoolean: " + e.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(
                JSONObject.class,
                "getBoolean",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String key = (String) param.args[0];
                        if (containsIgnoreCase(key, "vip")) {
                            param.setResult(true);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed hooking JSONObject.getBoolean: " + e.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(
                JSONObject.class,
                "has",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[0];
                        if (containsIgnoreCase(key, "vip")) {
                            param.setResult(true);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed hooking JSONObject.has: " + e.getMessage());
        }

        // 针对 org.json 更早的版本，也可能是 optInt / getInt
        try {
            XposedHelpers.findAndHookMethod(
                JSONObject.class,
                "optInt",
                String.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[0];
                        if (containsIgnoreCase(key, "vip")) {
                            param.setResult(1);
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Failed hooking JSONObject.optInt: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 判断 URL 是否为 UniApp 内部页面
     */
    private boolean isAppUrl(String url) {
        return url != null && (
            url.contains("file://") ||
            url.contains("uni-app") ||
            url.contains("__UNI__") ||
            url.contains("localhost") ||
            url.contains("127.0.0.1") ||
            url.contains("miplus") ||
            !url.startsWith("http")  // 包括 data:、file: 等内部页面
        );
    }

    /**
     * 向指定的 WebView 注入 JavaScript 代码
     */
    private void injectJsToWebView(WebView webView) {
        if (webView == null) return;
        try {
            webView.post(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        webView.evaluateJavascript(INJECT_JS, null);
                        XposedBridge.log(TAG + ": JS injected via evaluateJavascript");
                    }
                } catch (Exception e) {
                    XposedBridge.log(TAG + ": evaluateJavascript failed: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            XposedBridge.log(TAG + ": injectJs failed: " + e.getMessage());
        }
    }

    /**
     * 遍历 Activity 查找所有 WebView 并注入 JS
     */
    private void injectJsToAllWebViews(Activity activity) {
        if (activity == null) return;
        try {
            // 递归查找 Activity 中的 WebView
            findAndInjectWebViews(activity.getWindow().getDecorView());
        } catch (Exception e) {
            XposedBridge.log(TAG + ": injectAllWebViews failed: " + e.getMessage());
        }
    }

    /**
     * 递归查找 View 树中的 WebView
     */
    private void findAndInjectWebViews(android.view.View view) {
        if (view == null) return;
        if (view instanceof WebView) {
            injectJsToWebView((WebView) view);
            return;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                findAndInjectWebViews(group.getChildAt(i));
            }
        }
    }

    /**
     * 在 JSON 字符串中注入 vip=true
     */
    private String injectVipIntoJson(String json) {
        if (TextUtils.isEmpty(json)) return json;
        try {
            // 尝试用 JSONObject 解析
            JSONObject obj = new JSONObject(json);
            boolean modified = false;

            // 强制设置所有 VIP 相关字段
            if (obj.has("vip") || obj.has("expiredTime")) {
                obj.put("vip", true);
                obj.put("svip", true);
                obj.put("expiredTime", 4099680000L); // 2099-12-31
                obj.put("vipLevel", 999);
                obj.put("isVip", true);
                obj.put("isMember", true);
                obj.put("memberExpire", 4099680000L);
                modified = true;
            }

            if (modified) {
                String modifiedJson = obj.toString();
                XposedBridge.log(TAG + ": Injected VIP into JSON: " + keyNameFromJson(json));
                return modifiedJson;
            }
        } catch (Exception ignored) {}
        return json;
    }

    /**
     * 从 JSON 中提取可能的 key 名称（用于日志）
     */
    private String keyNameFromJson(String json) {
        if (json == null) return "null";
        try {
            JSONObject obj = new JSONObject(json);
            if (obj.has("id")) return "user:" + obj.optString("id", "?");
            if (obj.has("nickname")) return "user:" + obj.optString("nickname", "?");
        } catch (Exception ignored) {}
        return json.length() > 50 ? json.substring(0, 50) + "..." : json;
    }

    /**
     * 忽略大小写的 contains 检查
     */
    private boolean containsIgnoreCase(String str, String search) {
        return str != null && search != null &&
               str.toLowerCase().contains(search.toLowerCase());
    }
}
