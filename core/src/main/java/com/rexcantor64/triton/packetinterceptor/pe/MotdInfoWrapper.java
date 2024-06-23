package com.rexcantor64.triton.packetinterceptor.pe;

import com.google.common.annotations.Beta;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Beta
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("unused")
public final class MotdInfoWrapper {

    private @NotNull VersionInfo version = new VersionInfo();
    private @Nullable PlayerInfo players = null;
    private @NotNull JsonElement description = new JsonObject();
    private @Nullable String favicon = null;
    private @Nullable String modinfo = null;

    @Data
    public static final class VersionInfo {
        private String name;
        private int protocol;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static final class PlayerInfo {
        private int max, online;
        private List<Sample> sample = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static final class Sample {
        private @NotNull UUID uuid = new UUID(0, 0);
        private @NotNull String name = "";
    }

    // FML
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static final class ModInfo {
        private @NotNull String type = "BUKKIT";
        private @NotNull List<ModItem> modList = new ArrayList<>();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static final class ModItem {
        private @NotNull String modid = "", version = "";
    }

}
