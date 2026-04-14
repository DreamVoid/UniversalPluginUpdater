package me.dreamvoid.universalpluginupdater.update;

public abstract class AbstractUpdate {
    /**
     * 更新渠道类型
     */
    protected UpdateType updateType = UpdateType.Plugin;

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
     * 此方法应当在用户执行 /upu upgrade 时调用，执行升级逻辑<br>
     * 如果继承此类，建议重写 {@link #upgrade(boolean)} 方法而不是此方法
     * @return 升级是否成功
     */
    public boolean upgrade(){
        return upgrade(false);
    }

    /**
     * 升级现有插件，可指定是否立刻执行<br>
     * 此方法应当在用户执行 /upu upgrade 时调用，执行升级逻辑
     * @param now true 立刻执行，false 延迟到插件卸载阶段执行
     * @return 升级是否成功
     */
    public abstract boolean upgrade(boolean now);

    /**
     * 获取当前更新实例对应的插件 ID
     * @return 插件 ID
     */
    public abstract String getPluginId();

    /**
     * 获取更新的版本名
     * @return 新版本
     */
    public abstract String getVersion();

    /**
     * 获取更新渠道类型
     * @return {@link UpdateType}
     */
    public final UpdateType getUpdateType() {
        return updateType;
    }
}
