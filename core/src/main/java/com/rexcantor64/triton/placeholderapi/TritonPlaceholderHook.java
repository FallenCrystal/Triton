package com.rexcantor64.triton.placeholderapi;

import com.rexcantor64.triton.SpigotMLP;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TritonPlaceholderHook extends PlaceholderExpansion implements Relational {

    private final SpigotMLP triton;

    public TritonPlaceholderHook(SpigotMLP triton) {
        this.triton = triton;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "triton";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Rexcantor64";
    }

    @Override
    public @NotNull String getVersion() {
        return triton.getLoader().getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player p, @NotNull String params) {
        final String[] split = params.split("\\{}");
        final String key = split[0];
        String[] args = new String[split.length - 1];
        if (split.length > 1) {
            for (int i = 1; i < split.length; i++) {
                args[i - 1] = PlaceholderAPI.setPlaceholders(p, split[i].replace("$", "%"));
            }
        }
        return p == null
                ? triton.getLanguageManager().getTextFromMain(key, (Object[]) args)
                : triton.getLanguageManager().getText(triton.getPlayerManager().get(p.getUniqueId()), key, (Object[]) args);
    }

    @Override
    public String onPlaceholderRequest(Player ignore, Player viewer, String params) {
        return onPlaceholderRequest(viewer, params);
    }
}
