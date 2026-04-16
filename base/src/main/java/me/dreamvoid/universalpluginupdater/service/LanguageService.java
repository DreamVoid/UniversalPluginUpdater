package me.dreamvoid.universalpluginupdater.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import me.dreamvoid.universalpluginupdater.Config;
import me.dreamvoid.universalpluginupdater.Utils;
import me.dreamvoid.universalpluginupdater.platform.Platform;

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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 主代码语言服务
 * 从 resources/lang 下加载 JSON 语言文件，按 JVM 参数 / 系统语言 / 兜底语言逐层回退
 */
public final class LanguageService {
    private static final String FALLBACK_LOCALE = "zh-Hans";

    private static final LanguageService INSTANCE = new LanguageService();
    private static Logger logger = Logger.getLogger(LanguageService.class.getName());

    private static final Type MAP_TYPE = new TypeToken<Map<String, JsonElement>>() {}.getType();

    private final Map<String, Map<String, JsonElement>> cache = new ConcurrentHashMap<>();
    private final Map<String, String> localeResolveCache = new ConcurrentHashMap<>();
    private volatile Platform platform;

    private LanguageService() {}

    public static void setPlatform(Platform platform) {
        INSTANCE.platform = platform;
        logger = platform.getPlatformLogger();
        INSTANCE.localeResolveCache.clear();
    }

    /**
     * 获取语言键对应的文本<br>
     * 参数使用 {@link MessageFormat#format(String, Object...)} 格式化
     * @param key 语言键
     * @param args 参数
     * @return 翻译后的文本
     */
    public static String tr(String key, Object... args) {
        return INSTANCE.formatMessage(getLocale(), key, args);
    }

    /**
     * 获取语言键对应的文本<br>
     * 参数使用 {@link MessageFormat#format(String, Object...)} 格式化
     * @param locale 语言环境
     * @param key 语言键
     * @param args 参数
     * @return 翻译后的文本
     */
    public static String tr(Locale locale, String key, Object... args) {
        return INSTANCE.formatMessage(locale, key, args);
    }

    /**
     * 获取当前平台的 Locale<br>
     * 优先级：JVM 参数 upu.locale > 配置文件 > 运行环境 > zh-Hans
     * @return 当前平台的 Locale
     */
    public static Locale getLocale() {
        String requested = System.getProperty("upu.locale", FALLBACK_LOCALE);
        if (requested != null && !requested.isBlank()) {
            return INSTANCE.parseLocale(requested);
        }

        String configuredLanguage = Config.Language;
        if (configuredLanguage == null || configuredLanguage.isBlank()) {
            configuredLanguage = "system";
        }

        if (!configuredLanguage.equalsIgnoreCase("system")) {
            return INSTANCE.parseLocale(configuredLanguage);
        }

        Locale systemLocale = Locale.getDefault();
        return systemLocale == null ? Locale.forLanguageTag(FALLBACK_LOCALE) : systemLocale;
    }

    private String formatMessage(Locale requestedLocale, String key, Object... args) {
        String safeKey = sanitizeKey(key);
        if (safeKey.isEmpty()) {
            return "";
        }

        String requestedResolved = resolveLocaleName(getLocalTag(requestedLocale));
        String defaultResolved = resolveLocaleName(getLocalTag(getLocale()));

        JsonElement template = getBundle(requestedResolved).get(safeKey);

        if (template == null && isLocaleDifferent(requestedResolved, defaultResolved)) {
            template = getBundle(defaultResolved).get(safeKey);
        }

        if (template == null && isLocaleDifferent(requestedResolved, FALLBACK_LOCALE) && isLocaleDifferent(defaultResolved, FALLBACK_LOCALE)) {
            template = getBundle(FALLBACK_LOCALE).get(safeKey);
        }

        if (template == null) {
            return safeKey;
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

    private boolean isLocaleDifferent(String left, String right) {
        if (left == null || right == null) {
            return true;
        }
        return !normalizeLocaleTag(left).equalsIgnoreCase(normalizeLocaleTag(right));
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
            String target = sanitizeRawText(new String(bytes, StandardCharsets.UTF_8)).trim();
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
                Map<String, JsonElement> bundle = Utils.getGson().fromJson(reader, MAP_TYPE);
                return bundle != null ? sanitizeBundle(bundle) : Collections.emptyMap();
            }
        } catch (Exception e) {
            logger.warning("加载语言文件失败 " + resourcePath + ": " + e);
            return null;
        }
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
        loadLocalesExternal(locales);
        loadLocales(locales);
        locales.add(FALLBACK_LOCALE);
        return locales;
    }
    private void loadLocalesExternal(Set<String> locales) {
        if (platform == null) {
            return;
        }

        Path dir = platform.getDataPath().resolve("lang");
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(path -> {
                String fileName = path.getFileName().toString();
                LanguageService.this.addLocaleFromFileName(locales, fileName);
            });
        } catch (Exception e) {
            logger.warning("扫描外部语言目录失败: " + e);
        }
    }
    private void loadLocales(Set<String> locales) {
        try {
            var resources = LanguageService.class.getClassLoader().getResources("lang");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();
                if ("file".equalsIgnoreCase(protocol)) {
                    Path dir = Path.of(url.toURI());
                    if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                        continue;
                    }

                    try (Stream<Path> stream = Files.list(dir)) {
                        stream.forEach(path -> {
                            String fileName = path.getFileName().toString();
                            addLocaleFromFileName(locales, fileName);
                        });
                    }
                } else if ("jar".equalsIgnoreCase(protocol)) {
                    JarURLConnection connection = (JarURLConnection) url.openConnection();
                    try (JarFile jarFile = connection.getJarFile()) {
                        var entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (!name.startsWith("lang" + "/") || name.endsWith("/")) {
                                continue;
                            }

                            String fileName = name.substring(("lang" + "/").length());
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
        String sanitizedFileName = sanitizeRawText(fileName).trim();
        if (sanitizedFileName.isBlank()) {
            return;
        }

        String locale = sanitizedFileName.substring(0, sanitizedFileName.length() - 5);
        if (sanitizedFileName.endsWith(".json") || sanitizedFileName.endsWith(".link")) {
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

            return sanitizeRawText(Files.readString(path, StandardCharsets.UTF_8)).trim();
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
                Map<String, JsonElement> bundle = Utils.getGson().fromJson(reader, MAP_TYPE);
                return bundle != null ? sanitizeBundle(bundle) : Collections.emptyMap();
            }
        } catch (Exception e) {
            logger.warning("加载外部语言文件失败 " + locale + ".json: " + e);
            return null;
        }
    }
    private static Map<String, JsonElement> sanitizeBundle(Map<String, JsonElement> source) {
        LinkedHashMap<String, JsonElement> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            String sanitizedKey = sanitizeKey(entry.getKey());
            if (sanitizedKey.isEmpty()) {
                continue;
            }

            JsonElement value = entry.getValue();
            if (value == null || value.isJsonNull()) {
                continue;
            }

            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String sanitizedText = sanitizeRawText(value.getAsString());
                value = new JsonPrimitive(sanitizedText);
            }

            sanitized.put(sanitizedKey, value);
        }
        return sanitized;
    }
    private static String sanitizeKey(String key) {
        if (key == null) {
            return "";
        }
        return sanitizeRawText(key).trim();
    }
    private static String sanitizeRawText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }
    private static String getLocalTag(Locale locale) {
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
    private static String normalizeLocaleTag(String locale) {
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