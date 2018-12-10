package com.rexcantor64.triton.language;

import com.rexcantor64.triton.MultiLanguagePlugin;
import com.rexcantor64.triton.SpigotMLP;
import com.rexcantor64.triton.components.api.ChatColor;
import com.rexcantor64.triton.components.api.chat.BaseComponent;
import com.rexcantor64.triton.components.api.chat.HoverEvent;
import com.rexcantor64.triton.components.api.chat.TextComponent;
import com.rexcantor64.triton.config.MainConfig.FeatureSyntax;
import com.rexcantor64.triton.player.LanguagePlayer;
import com.rexcantor64.triton.scoreboard.ScoreboardComponent;
import com.rexcantor64.triton.utils.ComponentUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LanguageParser {

    public String replaceLanguages(String input, LanguagePlayer p, FeatureSyntax syntax) {
        Integer[] i;
        while ((i = getPatternIndex(input, syntax.getLang())) != null) {
            StringBuilder builder = new StringBuilder();
            builder.append(input, 0, i[0]);
            String placeholder = input.substring(i[2], i[3]);
            Integer[] argsIndex = getPatternIndex(placeholder, syntax.getArgs());
            if (argsIndex == null) {
                builder.append(SpigotMLP.get().getLanguageManager().getText(p, placeholder));
                builder.append(input.substring(i[1]));
                input = builder.toString();
                continue;
            }
            String code = placeholder.substring(0, argsIndex[0]);
            String args = placeholder.substring(argsIndex[2], argsIndex[3]);
            List<Integer[]> argIndexList = getPatternIndexArray(args, syntax.getArg());
            Object[] argList = new Object[argIndexList.size()];
            for (int k = 0; k < argIndexList.size(); k++) {
                Integer[] argIndex = argIndexList.get(k);
                argList[k] = replaceLanguages(args.substring(argIndex[2], argIndex[3]), p, syntax);
            }
            builder.append(SpigotMLP.get().getLanguageManager().getText(p, code, argList));
            builder.append(input.substring(i[1]));
            input = builder.toString();
        }
        return input;
    }

    public boolean hasLanguages(String input, FeatureSyntax syntax) {
        return getPatternIndex(input, syntax.getLang()) != null;
    }

    private static Integer[] getPatternIndex(String input, String pattern) {
        int start = -1;
        int contentLength = 0;
        int openedAmount = 0;

        for (int i = 0; i < input.length(); i++) {
            char currentChar = input.charAt(i);
            if (currentChar == '[' && input.length() > i + pattern.length() + 1 && input.substring(i + 1, i + 2 + pattern.length()).equals(pattern + "]")) {
                if (start == -1) start = i;
                openedAmount++;
                i += 1 + pattern.length();
            } else if (currentChar == '[' && input.length() > i + pattern.length() + 2 && input.substring(i + 1, i + 3 + pattern.length()).equals("/" + pattern + "]")) {
                openedAmount--;
                if (openedAmount == 0) {
                    if (contentLength == 0) {
                        start = -1;
                        continue;
                    }
                    return new Integer[]{start, i + 3 + pattern.length(), start + pattern.length() + 2, i};
                }
            } else if (start != -1)
                contentLength++;
        }
        return null;
    }

    private static List<Integer[]> getPatternIndexArray(String input, String pattern) {
        List<Integer[]> result = new ArrayList<>();
        int start = -1;
        int contentLength = 0;
        int openedAmount = 0;

        for (int i = 0; i < input.length(); i++) {
            char currentChar = input.charAt(i);
            if (currentChar == '[' && input.length() > i + pattern.length() + 1 && input.substring(i + 1, i + 2 + pattern.length()).equals(pattern + "]")) {
                if (start == -1) start = i;
                openedAmount++;
                i += 1 + pattern.length();
            } else if (currentChar == '[' && input.length() > i + pattern.length() + 2 && input.substring(i + 1, i + 3 + pattern.length()).equals("/" + pattern + "]")) {
                openedAmount--;
                if (openedAmount == 0) {
                    if (contentLength == 0) {
                        start = -1;
                        continue;
                    }
                    result.add(new Integer[]{start, i + 3 + pattern.length(), start + pattern.length() + 2, i});
                    start = -1;
                    contentLength = 0;
                }
            } else if (start != -1)
                contentLength++;
        }
        return result;
    }


    private BaseComponent getLastColorComponent(String input) {
        BaseComponent comp = new TextComponent("");
        if (input.length() < 2)
            return comp;
        for (int i = input.length() - 2; i >= 0; i--)
            if (input.charAt(i) == com.rexcantor64.triton.components.api.ChatColor.COLOR_CHAR) {
                com.rexcantor64.triton.components.api.ChatColor color = com.rexcantor64.triton.components.api.ChatColor.getByChar(input.charAt(i + 1));
                switch (color) {
                    case AQUA:
                    case BLACK:
                    case BLUE:
                    case DARK_AQUA:
                    case DARK_BLUE:
                    case DARK_GRAY:
                    case DARK_GREEN:
                    case DARK_PURPLE:
                    case DARK_RED:
                    case GOLD:
                    case GRAY:
                    case GREEN:
                    case LIGHT_PURPLE:
                    case RED:
                    case WHITE:
                    case YELLOW:
                        comp.setColor(color);
                        return comp;
                    case BOLD:
                        comp.setBold(true);
                        break;
                    case ITALIC:
                        comp.setItalic(true);
                        break;
                    case MAGIC:
                        comp.setObfuscated(true);
                        break;
                    case STRIKETHROUGH:
                        comp.setStrikethrough(true);
                        break;
                    case UNDERLINE:
                        comp.setUnderlined(true);
                        break;
                    case RESET:
                        return comp;
                    default:
                        break;

                }
            }
        return comp;
    }

    public BaseComponent[] parseSimpleBaseComponent(LanguagePlayer p, BaseComponent[] text, FeatureSyntax syntax) {
        for (BaseComponent a : text)
            if (a instanceof TextComponent)
                ((TextComponent) a).setText(replaceLanguages(((TextComponent) a).getText(), p, syntax));
        return text;
    }

    public BaseComponent[] parseTitle(LanguagePlayer p, BaseComponent[] text, FeatureSyntax syntax) {
        return TextComponent.fromLegacyText(replaceLanguages(TextComponent.toLegacyText(text), p, syntax));
    }

    private BaseComponent[] translateHoverComponents(LanguagePlayer p, FeatureSyntax syntax, BaseComponent... text) {
        List<BaseComponent> result = new ArrayList<>();
        for (BaseComponent comp : text) {
            if (comp.getHoverEvent() != null && comp.getHoverEvent().getAction() == HoverEvent.Action.SHOW_TEXT)
                comp.setHoverEvent(new HoverEvent(comp.getHoverEvent().getAction(), parseChat(p, syntax, comp.getHoverEvent().getValue())));
            result.add(comp);
            if (comp.getExtra() != null)
                for (BaseComponent extra : comp.getExtra())
                    translateHoverComponents(p, syntax, extra);
        }
        return result.toArray(new BaseComponent[0]);
    }

    public BaseComponent[] parseChat(LanguagePlayer p, FeatureSyntax syntax, BaseComponent... text) {
        if (text == null) return null;
        List<LanguageMessage> messages = LanguageMessage.fromBaseComponentArray(text);
        int counter = 15;
        indexLoop:
        while (counter > 0) {
            counter--;
            Integer[] i = getPatternIndex(BaseComponent.toPlainText(text), syntax.getLang());
            if (i == null) break;
            int index = 0;
            boolean foundStart = false;
            boolean foundEnd = false;
            BaseComponent beforeCache = new TextComponent("");
            BaseComponent compCache = new TextComponent("");
            BaseComponent afterCache = new TextComponent("");
            for (LanguageMessage message : messages) {
                if (foundEnd) {
                    afterCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText())));
                    continue;
                }
                if (!foundStart) {
                    if (index + message.getText().length() <= i[0]) {
                        beforeCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText())));
                        index += message.getText().length();
                        continue;
                    }
                    foundStart = true;
                    if (index + message.getText().length() >= i[1]) {
                        compCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(i[0] - index, i[1] - index))));
                        beforeCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(0, i[0] - index))));
                        afterCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(i[1] - index))));
                        foundEnd = true;
                        continue;
                    }
                    compCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(i[0] - index))));
                    beforeCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(0, i[0] - index))));
                } else {
                    if (message.isTranslatableComponent()) continue indexLoop;
                    if (index + message.getText().length() < i[1]) {
                        compCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText())));
                        if (index + message.getText().length() + 1 == i[1]) foundEnd = true;
                    } else {
                        compCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(0, i[1] - index))));
                        afterCache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(message.getText().substring(i[1] - index))));
                        foundEnd = true;
                        continue;
                    }
                }
                index += message.getText().length();
            }
            BaseComponent result = new TextComponent("");
            result.addExtra(beforeCache);
            BaseComponent processed = processLanguageComponent(compCache, p, syntax);
            if (processed == null) return null;
            result.addExtra(processed);

            BaseComponent afterCacheWrapper = getLastColorComponent(BaseComponent.toLegacyText(processed));
            afterCacheWrapper.addExtra(afterCache);
            result.addExtra(afterCacheWrapper);
            text = new BaseComponent[]{result};
            messages = LanguageMessage.fromBaseComponentArray(text);
        }

        text = translateHoverComponents(p, syntax, text);

        return text;
    }

    private BaseComponent processLanguageComponent(BaseComponent component, LanguagePlayer p, FeatureSyntax syntax) {
        String plainText = BaseComponent.toPlainText(component);
        Integer[] argsIndex = getPatternIndex(plainText, syntax.getArgs());
        if (argsIndex == null) {
            if (!SpigotMLP.get().getConf().getDisabledLine().isEmpty() && plainText.substring(syntax.getPatternSize(), plainText.length() - syntax.getPatternSize() - 1).equals(SpigotMLP.get().getConf().getDisabledLine()))
                return null;
            BaseComponent comp = ComponentUtils.copyFormatting(component.getExtra().get(0), new TextComponent(""));
            comp.setExtra(Arrays.asList(TextComponent.fromLegacyText(replaceLanguages(plainText, p, syntax))));
            return comp;
        }
        String messageCode = plainText.substring(syntax.getPatternSize(), argsIndex[0]);
        if (!SpigotMLP.get().getConf().getDisabledLine().isEmpty() && messageCode.equals(SpigotMLP.get().getConf().getDisabledLine()))
            return null;
        List<BaseComponent> arguments = new ArrayList<>();
        for (Integer[] i : getPatternIndexArray(plainText, syntax.getArg())) {
            BaseComponent cache = new TextComponent("");
            i[0] = i[0] + syntax.getPatternArgSize();
            i[1] = i[1] - syntax.getPatternArgSize() - 1;
            int index = 0;
            boolean foundStart = false;
            List<LanguageMessage> messages = LanguageMessage.fromBaseComponentArray(component);
            for (LanguageMessage message : messages) {
                if (!foundStart) {
                    if (index + message.getText().length() <= i[0]) {
                        index += message.getText().length();
                        continue;
                    }
                    foundStart = true;
                    if (index + message.getText().length() >= i[1]) {
                        cache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message.getText().substring(i[0] - index, i[1] - index))))));
                        break;
                    }
                    cache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message.getText().substring(i[0] - index))))));
                } else {
                    if (index + message.getText().length() < i[1]) {
                        cache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message.getText())))));
                    } else {
                        cache.addExtra(ComponentUtils.copyFormatting(message.getComponent(), new TextComponent(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', message.getText().substring(0, i[1] - index))))));
                        break;
                    }
                }
                index += message.getText().length();
            }
            arguments.add(cache);
        }
        return replaceArguments(TextComponent.fromLegacyText(MultiLanguagePlugin.get().getLanguageManager().getText(p, messageCode)), arguments);
    }

    private BaseComponent replaceArguments(BaseComponent[] base, List<BaseComponent> args) {
        BaseComponent result = new TextComponent("");
        for (LanguageMessage message : LanguageMessage.fromBaseComponentArray(base)) {
            String msg = message.getText();
            TextComponent current = new TextComponent("");
            for (int i = 0; i < msg.length(); i++) {
                if (msg.charAt(i) == '%') {
                    i++;
                    if (Character.isDigit(msg.charAt(i)) && args.size() >= Character.getNumericValue(msg.charAt(i))) {
                        result.addExtra(ComponentUtils.copyFormatting(message.getComponent(), current));
                        current = new TextComponent("");
                        current.addExtra(args.get(Character.getNumericValue(msg.charAt(i) - 1)));
                        result.addExtra(ComponentUtils.copyFormatting(message.getComponent(), current));
                        current = new TextComponent("");
                        continue;
                    }
                    i--;
                }
                current.setText(current.getText() + msg.charAt(i));
            }
            result.addExtra(ComponentUtils.copyFormatting(message.getComponent(), current));
        }
        return result;
    }

    /* Scoreboard stuff */

    public List<ScoreboardComponent> toScoreboardComponents(String text) {
        List<ScoreboardComponent> components = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        ScoreboardComponent current = new ScoreboardComponent();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 != text.length()) {
                i++;
                ChatColor cc = ChatColor.getByChar(text.charAt(i));
                if (cc == null) continue;
                switch (cc) {
                    case BOLD:
                        current.setBold(true);
                        break;
                    case ITALIC:
                        current.setItalic(true);
                        break;
                    case MAGIC:
                        current.setMagic(true);
                        break;
                    case STRIKETHROUGH:
                        current.setStrikethrough(true);
                        break;
                    case UNDERLINE:
                        current.setUnderline(true);
                        break;
                    default:
                        current.setText(builder.toString());
                        builder = new StringBuilder();
                        components.add(current);
                        current = new ScoreboardComponent();
                        current.setColor(cc);
                }
            } else
                builder.append(c);
        }
        if (builder.length() > 0) {
            current.setText(builder.toString());
            components.add(current);
        }
        return components;
    }

    public List<ScoreboardComponent> removeDummyColors(List<ScoreboardComponent> scoreboardComponents) {
        List<ScoreboardComponent> result = new ArrayList<>();
        for (ScoreboardComponent comp : scoreboardComponents) {
            if (comp.getText().length() == 0) continue;
            if (result.size() > 0 && comp.equalsFormatting(result.get(result.size() - 1))) {
                result.get(result.size() - 1).appendText(comp.getText());
                continue;
            }
            result.add(comp);
        }
        return result;
    }

    public String scoreboardComponentToString(List<ScoreboardComponent> scoreboardComponents) {
        StringBuilder builder = new StringBuilder();
        for (ScoreboardComponent comp : scoreboardComponents)
            builder.append(comp.getFormatting()).append(comp.getText());
        return builder.toString();
    }

    public String[] toPacketFormatting(List<ScoreboardComponent> components) {
        String toString = scoreboardComponentToString(components);
        if (toString.length() <= 36) return new String[]{"", toString, ""};
        StringBuilder prefix = new StringBuilder();
        StringBuilder entry = new StringBuilder();
        StringBuilder suffix = new StringBuilder();
        int status = 0;
        compLoop:
        for (ScoreboardComponent comp : components) {
            String formatting = comp.getFormatting();
            String text = comp.getText();
            boolean first = true;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (status == 0) {
                    if (first) {
                        first = false;
                        if (prefix.length() == 0 && formatting.equals("§f"))
                            formatting = "";
                        if (prefix.length() + formatting.length() > 16) {
                            int size = 16 - prefix.length();
                            prefix.append(formatting, 0, size);
                            entry.append(formatting.substring(size));
                            status = 1;
                            i--;
                            continue;
                        }
                        prefix.append(formatting);
                    }
                    if (prefix.length() >= 16)
                        status = 1;
                    else
                        prefix.append(c);
                }
                formatting = comp.getFormatting();
                if (status == 1) {
                    if (first) {
                        first = false;
                        if (entry.length() + formatting.length() > 36) {
                            entry.append("1234");
                            status = 2;
                            i--;
                            continue;
                        }
                        entry.append(formatting);
                    }
                    if (entry.length() >= 36) {
                        entry.append("1234");
                        status = 2;
                    } else
                        entry.append(c);
                }
                if (status == 2) {
                    if (first || suffix.length() == 0) {
                        first = false;
                        if (suffix.length() + formatting.length() > 16)
                            break compLoop;
                        suffix.append(formatting);
                    }
                    if (suffix.length() >= 16)
                        break compLoop;
                    suffix.append(c);
                }
            }
        }
        return new String[]{prefix.toString(), entry.toString(), suffix.toString()};
    }


}
