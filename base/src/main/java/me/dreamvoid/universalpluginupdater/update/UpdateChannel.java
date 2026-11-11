package me.dreamvoid.universalpluginupdater.update;

import java.lang.annotation.*;

/**
 * 通用更新渠道的标识注解<br>
 * 标注在 {@link AbstractUpdate} 实现类上，声明该渠道的标识（id）<br>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface UpdateChannel {
    /**
     * 渠道标识，如 "github"、"modrinth"<br>
     * 与配置文件 {@code channels[].type} 字段对应，不区分大小写
     */
    String id();
}
