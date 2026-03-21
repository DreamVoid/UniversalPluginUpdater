package me.dreamvoid.universalpluginupdater.update;

public abstract class AbstractUpdate {
    public UpdateType updateType;

    /**
     * 执行更新检查，联网获取最新版本信息<br>
     * 此方法应当在用户执行 /upu update 时调用
     * @return 检查更新是否成功
     */
    public abstract boolean update();

    /**
     * 下载更新文件<br>
     * 此方法应当在用户执行 /upu download 时调用
     * @return 下载文件是否成功
     */
    public abstract boolean download();

    /**
     * 升级现有插件<br>
     * 此方法应当在用户执行 /upu upgrade 时调用，执行升级逻辑
     * @return 升级是否成功
     */
    public boolean upgrade(){
        return upgrade(false);
    };

    /**
     * 升级现有插件，可指定是否立刻执行
     * @param now true 立刻执行，false 延迟到插件卸载阶段执行
     * @return 升级是否成功
     */
    public abstract boolean upgrade(boolean now);

    /**
     * 获取缓存的版本号
     * 此方法应在 update() 执行成功后调用，返回缓存的版本信息
     * @return 版本号字符串，若无缓存则返回null
     */
    public abstract String getCachedVersion();

    /**
     * 获取该更新渠道对应的插件 ID
     * 用于升级时获取插件文件
     * @return 插件 ID
     */
    public abstract String getPluginId();
}
