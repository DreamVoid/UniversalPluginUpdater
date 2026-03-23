package me.dreamvoid.universalpluginupdater;

/**
 * 主代码配置缓存
 *
 * 命名约定：层级键使用大驼峰并以下划线连接，例如 foo.bar -> Foo_Bar
 */
public abstract class Config {
    public static boolean Verbose = false;
    public static String Language = "system";
    public static String Updater_Strategy = "native";
    public static boolean Updater_AllowUpgradeNow = true;
    public static String Updater_Proxy_Uri = null;
    public static String Updater_Proxy_Username = null;
    public static String Updater_Proxy_Password = null;

    /**
     * 加载/重载配置项
     */
    public void reloadConfig(){
        Utils.getLogger().info("正在加载配置文件...");
        loadConfig();

        Verbose = getBoolean("verbose", Verbose);
        Language = getString("language", Language);
        Updater_Strategy = getString("updater.strategy", Updater_Strategy);
        Updater_AllowUpgradeNow = getBoolean("updater.allow-upgrade-now", Updater_AllowUpgradeNow);
        Updater_Proxy_Uri = getString("updater.proxy.uri", null);
        Updater_Proxy_Username = getString("updater.proxy.username", null);
        Updater_Proxy_Password = getString("updater.proxy.password", null);
    }

    /**
     * 加载配置文件（平台）<br>
     * 在 Bukkit 上，类似 saveDefaultConfig() + reloadConfig()
     */
    public abstract void loadConfig();

    public abstract String getString(String path, String def);

    public abstract int getInt(String path, int def);

    public abstract long getLong(String path, long def);

    public abstract boolean getBoolean(String path, boolean def);

    public String getString(String path) {
        return getString(path, null);
    }

    public int getInt(String path) {
        return getInt(path, 0);
    }

    public long getLong(String path) {
        return getLong(path, 0L);
    }

    public boolean getBoolean(String path) {
        return getBoolean(path, false);
    }
}
