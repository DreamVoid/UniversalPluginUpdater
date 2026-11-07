package me.dreamvoid.universalpluginupdater.service;

import me.dreamvoid.universalpluginupdater.Config;
import org.junit.*;

import java.util.Locale;

import static org.junit.Assert.*;

/**
 * 验证日语资源加载与语言选择优先级
 */
public class LanguageManagerTest {
    private String property;
    private String language;
    private Locale systemLocale;

    /**
     * 保存进程级语言配置
     */
    @Before
    public void saveSettings() {
        property = System.getProperty("upu.locale");
        language = Config.Language;
        systemLocale = Locale.getDefault();
        System.clearProperty("upu.locale");
    }

    /**
     * 恢复进程级语言配置
     */
    @After
    public void restoreSettings() {
        if (property == null) System.clearProperty("upu.locale");
        else System.setProperty("upu.locale", property);
        Config.Language = language;
        Locale.setDefault(systemLocale);
    }

    /**
     * 未指定 JVM 覆盖时使用配置语言
     */
    @Test
    public void configuredJapaneseIsRespected() {
        Config.Language = "ja-JP";
        Locale.setDefault(Locale.US);
        assertEquals(Locale.JAPAN, LanguageManager.getLocale());
        assertEquals("すべてのプラグインが最新です。", LanguageManager.tr("message.command.update.none"));
    }

    /**
     * JVM 参数仍优先于配置
     */
    @Test
    public void explicitJvmOverrideHasPriority() {
        Config.Language = "ja-JP";
        System.setProperty("upu.locale", "en-US");
        assertEquals(Locale.US, LanguageManager.getLocale());
        assertEquals("All plugins are already up to date.", LanguageManager.tr("message.command.update.none"));
    }

    /**
     * system 配置与 ja 别名正确加载日语
     */
    @Test
    public void systemJapaneseAndLanguageAliasResolve() {
        Config.Language = "system";
        Locale.setDefault(Locale.JAPANESE);
        assertEquals(Locale.JAPANESE, LanguageManager.getLocale());
        assertEquals("すべてのプラグインが最新です。", LanguageManager.tr("message.command.update.none"));
        Config.Language = "ja";
        assertEquals("すべてのプラグインが最新です。", LanguageManager.tr("message.command.update.none"));
    }

    /**
     * 日语中的占位符、数字与多行帮助正确格式化
     */
    @Test
    public void japaneseParametersAndHelpAreRendered() {
        Config.Language = "ja-JP";
        String failure = LanguageManager.tr("message.strategy.backup.failed", "Example", "/plugins/.disabled/id", "disk");
        assertTrue(failure.contains("Example"));
        assertTrue(failure.contains("/plugins/.disabled/id"));
        assertTrue(failure.contains("disk"));
        assertFalse(failure.contains("{0}"));
        assertEquals("3件のプラグインを更新しました。2件は更新に失敗しました。",
                LanguageManager.tr("message.lifecycle.unload.result", 3, 2));
        String help = LanguageManager.tr("message.command.help", "1.2", "test");
        assertTrue(help.startsWith("UniversalPluginUpdater 1.2 (test)\n"));
        assertTrue(help.contains("使い方:"));
    }
}
