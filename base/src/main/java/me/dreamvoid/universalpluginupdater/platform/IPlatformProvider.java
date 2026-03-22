package me.dreamvoid.universalpluginupdater.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * 平台提供者接口，由各个服务端的插件实现
 * 提供平台特定的数据访问功能
 */
public interface IPlatformProvider {
    // 平台自身相关

    /**
     * 获取当前平台名称
     * @return 平台名称（如 "Paper", "Bukkit", "BungeeCord"）
     */
    String getPlatformName();

    /**
     * 获取平台支持的游戏版本列表
     * 用于Modrinth等API的版本筛选
     * @return 游戏版本列表（如 "1.20.1", "1.21"），返回null或空列表表示不限制版本
     */
    List<String> getGameVersions();

    /**
     * 获取平台支持的加载器类型列表
     * 用于Modrinth等API的版本筛选
     * @return 加载器类型列表（如 "paper", "bukkit", "bungeecord"）
     */
    List<String> getLoaders();

    /**
     * 获取插件数据目录路径
     * @return 数据目录的Path对象
     */
    Path getDataPath();

    // 平台其他插件相关

    /**
     * 获取所有已安装插件的标识符列表
     * 标识符应该为小写形式
     * @return 插件标识符列表
     */
    List<String> getPlugins();

    /**
     * 获取本插件的版本名
     * @return 插件版本名
     */
    @NotNull
    String getPluginVersion();

    /**
     * 获取指定插件的版本名
     * @param pluginName 插件标识符
     * @return 插件版本名
     */
    @Nullable
    String getPluginVersion(String pluginName);

    // 平台实用相关

    /**
     * 获取平台的Logger实例
     * @return 平台的Logger实例，供插件使用
     */
    Logger getPlatformLogger();

    /**
     * 异步执行Runnable代码
     * 不同平台对异步调度的方式不同，由各平台的实现来提供
     * @param runnable 要执行的任务
     */
    void runTaskAsync(Runnable runnable);

    /**
     * 获取指定命令发送者的Locale
     * @param sender 命令发送者
     * @return {@link Locale} 对象
     */
    Locale getLocale(ICommandSender sender);

    /**
     * 获取指定插件的文件路径
     * @param pluginId 插件标识符
     * @return 插件文件路径，如果找不到返回null
     */
    @Nullable
    Path getPluginFile(String pluginId);


    /**
     * 卸载指定插件
     * @param pluginId 插件标识符
     * @return 卸载是否成功
     */
    boolean unloadPlugin(String pluginId);
}

