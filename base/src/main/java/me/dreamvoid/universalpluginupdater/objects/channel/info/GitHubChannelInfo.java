package me.dreamvoid.universalpluginupdater.objects.channel.info;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * GitHub渠道的配置信息
 */
public record GitHubChannelInfo(
        @SerializedName("repository") String repository,
        @SerializedName("auth") String auth,
        @SerializedName("accept") JsonElement accept,
        @SerializedName("filter") String filter,
        @SerializedName("version-key") String versionKey,
        @SerializedName("version-regex") String versionRegex
) {
        public List<String> acceptValues() {
                if (accept == null || accept.isJsonNull()) {
                        return List.of();
                }

                if (accept.isJsonArray()) {
                        List<String> values = new ArrayList<>();
                        accept.getAsJsonArray().forEach(element -> {
                                if (element == null || element.isJsonNull()) {
                                        return;
                                }

                                String value = element.getAsString();
                                if (value != null && !value.isBlank()) {
                                        values.add(value);
                                }
                        });
                        return values;
                }

                if (accept.isJsonPrimitive()) {
                        String value = accept.getAsString();
                        return (value != null && !value.isBlank()) ? List.of(value) : List.of();
                }

                return List.of();
        }
}
