package me.dreamvoid.universalpluginupdater.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * 平台提供者接口，由各个服务端的插件实现
 * 提供平台特定的数据访问功能
 */
public interface IPlatformProvider {
    /**
     * 获取插件数据目录路径
     * @return 数据目录的Path对象
     */
    Path getDataPath();

    /**
     * 获取所有已安装插件的标识符列表
     * 标识符应该为小写形式
     * @return 插件标识符列表
     */
    List<String> getPlugins();

    /**
     * 获取平台支持的加载器类型列表
     * 用于Modrinth等API的版本筛选
     * @return 加载器类型列表（如 "paper", "bukkit", "bungeecord"）
     */
    List<String> getLoaders();

    /**
     * 获取平台支持的游戏版本列表
     * 用于Modrinth等API的版本筛选
     * @return 游戏版本列表（如 "1.20.1", "1.21"），返回null或空列表表示不限制版本
     */
    List<String> getGameVersions();

    Logger getPlatformLogger();
}
