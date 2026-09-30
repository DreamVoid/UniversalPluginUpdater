package me.dreamvoid.universalpluginupdater.update;

import me.dreamvoid.universalpluginupdater.platform.Platform;

/**
 * 插件专属更新渠道标记接口<br>
 * 实现此接口的更新渠道仅服务于 {@link #getPluginId()} 指定的插件，不作为通用渠道注册（不可被其他插件通过配置使用）<br>
 * 此类渠道的渠道标识固定为 "plugin"
 */
@UpdateChannel("plugin")
public abstract class AbstractPluginUpdate extends AbstractUpdate {
    protected AbstractPluginUpdate(String pluginId, Platform platform) {
        super(pluginId, platform);
    }
}
