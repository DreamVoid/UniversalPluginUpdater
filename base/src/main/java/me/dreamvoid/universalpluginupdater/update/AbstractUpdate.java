package me.dreamvoid.universalpluginupdater.update;

import java.net.URL;

public abstract class AbstractUpdate {
    public UpdateType updateType;

    /**
     * 获取更新信息中的版本号
     * @return 版本号字符串
     */
    public abstract String getVersion();

    /**
     * 获取更新文件的下载链接
     * @return 下载链接URL
     */
    public abstract URL getDownloadUrl();

    /**
     * 下载更新文件<br>
     * 此方法应当在用户执行 /upu download 时调用
     * @return 下载是否成功
     */
    public abstract boolean download();
}
