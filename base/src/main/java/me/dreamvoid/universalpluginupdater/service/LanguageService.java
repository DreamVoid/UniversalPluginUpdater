package me.dreamvoid.universalpluginupdater.service;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import me.dreamvoid.universalpluginupdater.platform.IPlatformProvider;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/**
 * 主代码语言服务
 * 从 resources/lang 下加载 JSON 语言文件，按 JVM 参数 / 系统语言 / 兜底语言逐层回退
 */
public final class LanguageService {
    private static final String FALLBACK_LOCALE = "zh-Hans";
    private static final String LOCALE_PROPERTY = "upu.locale";
    private static final String LANG_RESOURCE_DIR = "lang";
    private static final LanguageService INSTANCE = new LanguageService();
    private static final Logger logger = Logger.getLogger(LanguageService.class.getName());
    private static final Gson gson = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, JsonElement>>() {}.getType();

    private final Map<String, Map<String, JsonElement>> cache = new ConcurrentHashMap<>();
    private final Map<String, String> localeResolveCache = new ConcurrentHashMap<>();
    private volatile IPlatformProvider platform;

    private LanguageService() {
    }

    public static LanguageService instance() {
        return INSTANCE;
    }

    public void setPlatform(IPlatformProvider platform) {
        this.platform = platform;
        localeResolveCache.clear();
    }

    /**
     * 参数使用 {@link MessageFormat#format(String, Object...)} 格式化
     * @param key 语言键
     * @param args 参数
     * @return 翻译后的文本
     */
    public String tr(String key, Object... args) {
        return formatMessage(getBundle(resolveLocaleName(getLocalTag(getLocale()))), key, args);
    }

    /**
     * @param locale 语言环境
     * @param key 语言键
     * @param args 参数
     * @return 翻译后的文本
     */
    public String tr(Locale locale, String key, Object... args) {
        return formatMessage(getBundle(resolveLocaleName(getLocalTag(locale))), key, args);
    }

    private String formatMessage(Map<String, JsonElement> bundle, String key, Object... args) {
        JsonElement template = bundle.get(key);
        if (template == null) {
            Map<String, JsonElement> fallback = getBundle(FALLBACK_LOCALE);
            template = fallback.get(key);
            if (template == null) {
                return key;
            }
        }

        if (template.isJsonArray()) {
            // 数组文本多行处理
            StringBuilder builder = new StringBuilder();
            template.getAsJsonArray().forEach(element -> {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                String line = element.getAsString();
                builder.append(args == null || args.length == 0 ? line : MessageFormat.format(line, args));
            });
            return builder.toString();
        } else {
            // 单文本单行处理
            return MessageFormat.format(template.getAsString(), args);
        }
    }

    private Map<String, JsonElement> getBundle(String localeName) {
        String effectiveLocale = localeName == null || localeName.isBlank() ? FALLBACK_LOCALE : localeName;
        return cache.computeIfAbsent(effectiveLocale, this::loadBundleSafely);
    }

    private Map<String, JsonElement> loadBundleSafely(String locale) {
        Map<String, JsonElement> bundle = loadBundle(locale);
        if (bundle != null) {
            return bundle;
        }

        if (!FALLBACK_LOCALE.equals(locale)) {
            logger.warning("未找到语言文件 " + locale + ", 回退到 " + FALLBACK_LOCALE);
        }

        Map<String, JsonElement> fallback = loadBundle(FALLBACK_LOCALE);
        return fallback != null ? fallback : Collections.emptyMap();
    }

    private String loadLink(String locale) {
        String externalPath = readExternalLangFile(locale + ".link");
        if (externalPath != null) {
            return externalPath;
        }

        String resourcePath = "/lang/" + locale + ".link";
        try (InputStream inputStream = LanguageService.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return null;
            }

            byte[] bytes = inputStream.readAllBytes();
            String target = new String(bytes, StandardCharsets.UTF_8).trim();
            return target.isEmpty() ? null : target;
        } catch (Exception e) {
            logger.warning("加载语言链接失败 " + resourcePath + ": " + e);
            return null;
        }
    }

    private Map<String, JsonElement> loadBundle(String locale) {
        Map<String, JsonElement> externalBundle = loadExternalBundle(locale);
        if (externalBundle != null) {
            return externalBundle;
        }

        String resourcePath = "/lang/" + locale + ".json";
        try (InputStream inputStream = LanguageService.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return null;
            }

            try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                Map<String, JsonElement> bundle = gson.fromJson(reader, MAP_TYPE);
                return bundle != null ? new LinkedHashMap<>(bundle) : Collections.emptyMap();
            }
        } catch (Exception e) {
            logger.warning("加载语言文件失败 " + resourcePath + ": " + e);
            return null;
        }
    }

    /**
     * 获取当前平台的 Locale<br>
     * 优先级：JVM 参数 upu.locale > 配置文件 > 运行环境 > zh-Hans
     * @return 当前平台的 Locale
     */
    public Locale getLocale() {
        String requested = System.getProperty(LOCALE_PROPERTY);
        if (requested != null && !requested.isBlank()) {
            return parseLocale(requested);
        }

        // TODO: 实现配置文件指定语言

        Locale systemLocale = Locale.getDefault();
        return systemLocale == null ? Locale.forLanguageTag(FALLBACK_LOCALE) : systemLocale;
    }

    private Locale parseLocale(String locale) {
        String value = normalizeLocaleTag(locale);
        if (value.isEmpty()) {
            return Locale.forLanguageTag(FALLBACK_LOCALE);
        }

        Locale parsed = Locale.forLanguageTag(value);
        if (parsed.getLanguage().isEmpty()) {
            return Locale.forLanguageTag(FALLBACK_LOCALE);
        }

        return parsed;
    }


    private String resolveLocaleName(String candidate) {
        String normalized = normalizeLocaleTag(candidate);
        if (normalized.isBlank()) {
            return FALLBACK_LOCALE;
        }

        String cached = localeResolveCache.get(normalized);
        if (cached != null) {
            return cached;
        }

        String resolved = resolveLocaleName(normalized, new HashSet<>());
        localeResolveCache.put(normalized, resolved);
        return resolved;
    }

    private String resolveLocaleName(String candidate, Set<String> visited) {
        String normalized = normalizeLocaleTag(candidate);
        if (normalized.isBlank()) {
            return FALLBACK_LOCALE;
        }

        String visitedKey = normalized.toLowerCase(Locale.ROOT);
        if (!visited.add(visitedKey)) {
            logger.warning("语言映射出现循环，已回退到 " + FALLBACK_LOCALE + ": " + candidate);
            return FALLBACK_LOCALE;
        }

        // 1) 精确匹配 JSON
        if (hasJsonLocale(normalized)) {
            return normalized;
        }

        // 2) 精确匹配 LINK
        if (hasLinkLocale(normalized)) {
            String linkedLocale = loadLink(normalized);
            if (linkedLocale != null && !linkedLocale.isBlank()) {
                return resolveLocaleName(linkedLocale, visited);
            }
        }

        // 3) startsWith 匹配（按名称排序后取第一个）
        String prefixMatch = findStartsWithLocale(normalized);
        if (prefixMatch != null) {
            return resolveLocaleName(prefixMatch, visited);
        }

        // 4) 用分隔符提取语言主段再匹配（en-SG -> en）
        String base = extractBaseSegment(normalized);
        if (!base.equalsIgnoreCase(normalized)) {
            if (hasJsonLocale(base)) {
                return base;
            }
            if (hasLinkLocale(base)) {
                String linkedBase = loadLink(base);
                if (linkedBase != null && !linkedBase.isBlank()) {
                    return resolveLocaleName(linkedBase, visited);
                }
            }
            String basePrefixMatch = findStartsWithLocale(base);
            if (basePrefixMatch != null) {
                return resolveLocaleName(basePrefixMatch, visited);
            }
        }

        // 5) 回退默认语言
        if (!FALLBACK_LOCALE.equalsIgnoreCase(normalized)) {
            return resolveLocaleName(FALLBACK_LOCALE, visited);
        }

        return FALLBACK_LOCALE;
    }

    private String findStartsWithLocale(String localePrefix) {
        String prefix = localePrefix.toLowerCase(Locale.ROOT);
        List<String> candidates = new ArrayList<>(collectAvailableLocales());
        candidates.sort(Comparator.comparing(String::toLowerCase));

        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                return candidate;
            }
        }

        return null;
    }

    private String extractBaseSegment(String locale) {
        int dashIndex = locale.indexOf('-');
        int underscoreIndex = locale.indexOf('_');

        int splitIndex;
        if (dashIndex < 0) {
            splitIndex = underscoreIndex;
        } else if (underscoreIndex < 0) {
            splitIndex = dashIndex;
        } else {
            splitIndex = Math.min(dashIndex, underscoreIndex);
        }

        if (splitIndex <= 0) {
            return locale;
        }

        return locale.substring(0, splitIndex);
    }

    private boolean hasJsonLocale(String locale) {
        return loadBundle(locale) != null;
    }

    private boolean hasLinkLocale(String locale) {
        return loadLink(locale) != null;
    }

    private Set<String> collectAvailableLocales() {
        Set<String> locales = new HashSet<>();
        collectLocalesFromExternal(locales);
        collectLocalesFromClasspath(locales);
        locales.add(FALLBACK_LOCALE);
        return locales;
    }

    private void collectLocalesFromExternal(Set<String> locales) {
        if (platform == null) {
            return;
        }

        try {
            Path dir = platform.getDataPath().resolve("lang");
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                return;
            }

            Files.list(dir).forEach(new Consumer<Path>() {
                @Override
                public void accept(Path path) {
                    String fileName = path.getFileName().toString();
                    LanguageService.this.addLocaleFromFileName(locales, fileName);
                }
            });
        } catch (Exception e) {
            logger.warning("扫描外部语言目录失败: " + e);
        }
    }

    private void collectLocalesFromClasspath(Set<String> locales) {
        try {
            var resources = LanguageService.class.getClassLoader().getResources(LANG_RESOURCE_DIR);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();
                if ("file".equalsIgnoreCase(protocol)) {
                    Path dir = Path.of(url.toURI());
                    if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                        continue;
                    }

                    Files.list(dir).forEach(path -> {
                        String fileName = path.getFileName().toString();
                        addLocaleFromFileName(locales, fileName);
                    });
                } else if ("jar".equalsIgnoreCase(protocol)) {
                    JarURLConnection connection = (JarURLConnection) url.openConnection();
                    try (JarFile jarFile = connection.getJarFile()) {
                        var entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (!name.startsWith(LANG_RESOURCE_DIR + "/") || name.endsWith("/")) {
                                continue;
                            }

                            String fileName = name.substring((LANG_RESOURCE_DIR + "/").length());
                            if (fileName.contains("/")) {
                                continue;
                            }

                            addLocaleFromFileName(locales, fileName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("扫描类路径语言目录失败: " + e);
        }
    }

    private void addLocaleFromFileName(Set<String> locales, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        if (fileName.endsWith(".json")) {
            String locale = fileName.substring(0, fileName.length() - 5);
            locales.add(normalizeLocaleTag(locale));
            return;
        }

        if (fileName.endsWith(".link")) {
            String locale = fileName.substring(0, fileName.length() - 5);
            locales.add(normalizeLocaleTag(locale));
        }
    }

    private String readExternalLangFile(String fileName) {
        if (platform == null) {
            return null;
        }

        try {
            Path path = platform.getDataPath().resolve("lang").resolve(fileName);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return null;
            }

            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            logger.warning("读取外部语言文件失败 " + fileName + ": " + e);
            return null;
        }
    }

    private Map<String, JsonElement> loadExternalBundle(String locale) {
        if (platform == null) {
            return null;
        }

        try {
            Path path = platform.getDataPath().resolve("lang").resolve(locale + ".json");
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return null;
            }

            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Map<String, JsonElement> bundle = gson.fromJson(reader, MAP_TYPE);
                return bundle != null ? new LinkedHashMap<>(bundle) : Collections.emptyMap();
            }
        } catch (Exception e) {
            logger.warning("加载外部语言文件失败 " + locale + ".json: " + e);
            return null;
        }
    }

    private String getLocalTag(Locale locale) {
        if (locale == null) {
            return FALLBACK_LOCALE;
        }

        String tag = normalizeLocaleTag(locale.toLanguageTag());
        if (tag.isBlank() || tag.equalsIgnoreCase("und")) {
            return FALLBACK_LOCALE;
        }

        if (locale.getLanguage().equalsIgnoreCase("zh") && tag.equalsIgnoreCase("zh")) {
            return FALLBACK_LOCALE;
        }

        return tag;
    }

    private String normalizeLocaleTag(String locale) {
        if (locale == null) {
            return "";
        }

        String value = locale.trim().replace('_', '-');
        if (value.isEmpty()) {
            return "";
        }

        String tag = Locale.forLanguageTag(value).toLanguageTag();
        if (tag.isBlank() || tag.equalsIgnoreCase("und")) {
            return FALLBACK_LOCALE;
        }

        return tag;
    }
}