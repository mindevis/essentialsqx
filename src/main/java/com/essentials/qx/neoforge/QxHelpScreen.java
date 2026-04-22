package com.essentials.qx.neoforge;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Type;
import java.util.*;

public class QxHelpScreen extends Screen {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, List<?>>>(){}.getType();

    private final Map<String, List<?>> groupCommands;
    private final List<String> groupOrder = new ArrayList<>();
    private final Set<String> collapsedGroups = new HashSet<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean scrolling = false;
    private static final int LINE_HEIGHT = 12;
    private static final int PADDING = 10;
    private static final int HEADER_HEIGHT = 18;
    private static final int CONTENT_BOTTOM_MARGIN = 40;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLLBAR_MARGIN = 4;

    public QxHelpScreen(String json) {
        super(Component.literal("QX Help"));
        Map<String, List<?>> parsed = new LinkedHashMap<>();
        try {
            parsed = GSON.fromJson(json, MAP_TYPE);
            if (parsed != null) {
                groupOrder.addAll(parsed.keySet());
            }
        } catch (Exception ignored) {
            parsed = Map.of();
        }
        this.groupCommands = parsed != null ? parsed : Map.of();
    }

    @Override
    protected void init() {
        super.init();
        collapsedGroups.clear();
        collapsedGroups.addAll(groupOrder);
        int buttonW = 100;
        int buttonH = 20;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(width / 2 - buttonW / 2, height - 28, buttonW, buttonH)
            .build());
    }

    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, width, height, 0xE0101010);
    }

    private int getVisibleContentHeight() {
        int h = 0;
        for (String groupName : groupOrder) {
            h += HEADER_HEIGHT;
            if (!collapsedGroups.contains(groupName)) {
                List<?> cmds = groupCommands.get(groupName);
                if (cmds != null) {
                    for (Object entryObj : cmds) {
                        if (entryObj instanceof Map<?, ?> entry) {
                            h += LINE_HEIGHT;
                            Object desc = entry.get("description");
                            if (desc != null && !String.valueOf(desc).isEmpty()) h += LINE_HEIGHT;
                        }
                    }
                }
            }
        }
        return h;
    }

    private void renderScrollbar(GuiGraphics gui, int contentTop, int contentBottom, int mouseX, int mouseY) {
        int trackHeight = contentBottom - contentTop;
        if (maxScroll <= 0 || trackHeight <= 0) return;

        int scrollbarX = width - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
        gui.fill(scrollbarX, contentTop, scrollbarX + SCROLLBAR_WIDTH, contentBottom, 0x80000000);
        int thumbHeight = Math.max(20, trackHeight * trackHeight / (trackHeight + maxScroll));
        int thumbY = contentTop + (int) ((long) scrollOffset * (trackHeight - thumbHeight) / maxScroll);
        int thumbColor = (mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH
                && mouseY >= thumbY && mouseY <= thumbY + thumbHeight) || scrolling
                ? 0xFF808080 : 0xFF606060;
        gui.fill(scrollbarX + 1, thumbY, scrollbarX + SCROLLBAR_WIDTH - 1, thumbY + thumbHeight, thumbColor);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);

        int contentTop = PADDING;
        int contentBottom = height - CONTENT_BOTTOM_MARGIN;
        int contentHeight = contentBottom - contentTop;
        int contentRight = width - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN * 2;

        int totalContentHeight = getVisibleContentHeight();
        maxScroll = Math.max(0, totalContentHeight - contentHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        gui.enableScissor(PADDING, contentTop, contentRight, contentBottom);

        int y = contentTop - scrollOffset;
        int left = PADDING + 5;

        for (String groupName : groupOrder) {
            boolean collapsed = collapsedGroups.contains(groupName);
            String arrow = collapsed ? "▶ " : "▼ ";
            if (y + HEADER_HEIGHT >= contentTop && y <= contentBottom) {
                gui.drawString(font, "§6§l" + arrow + groupName, left, y + 2, 0xFFFFFF, false);
            }
            y += HEADER_HEIGHT;

            if (!collapsed) {
                List<?> cmds = groupCommands.get(groupName);
                if (cmds != null) {
                    for (Object entryObj : cmds) {
                        if (entryObj instanceof Map<?, ?> entry) {
                            String cmd = entry.get("command") != null ? String.valueOf(entry.get("command")) : "";
                            String desc = entry.get("description") != null ? String.valueOf(entry.get("description")) : "";
                            if (y + LINE_HEIGHT < contentTop || y > contentBottom) {
                                y += LINE_HEIGHT;
                                if (!desc.isEmpty()) y += LINE_HEIGHT;
                                continue;
                            }
                            if (!cmd.isEmpty() && !cmd.equals("null")) {
                                gui.drawString(font, "§a" + cmd, left, y, 0xFFFFFF, false);
                                y += LINE_HEIGHT;
                            }
                            if (!desc.isEmpty() && !desc.equals("null")) {
                                gui.drawString(font, "§7  " + desc, left, y, 0xAAAAAA, false);
                                y += LINE_HEIGHT;
                            }
                        }
                    }
                }
            }
        }

        gui.disableScissor();
        renderScrollbar(gui, contentTop, contentBottom, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int contentTop = PADDING;
            int contentBottom = height - CONTENT_BOTTOM_MARGIN;
            int scrollbarX = width - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
            int trackHeight = contentBottom - contentTop;

            if (scrollbarX >= 0 && mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH
                    && mouseY >= contentTop && mouseY <= contentBottom && maxScroll > 0) {
                int thumbHeight = Math.max(20, trackHeight * trackHeight / (trackHeight + maxScroll));
                int thumbY = contentTop + (int) ((long) scrollOffset * (trackHeight - thumbHeight) / maxScroll);
                int contentHeight = contentBottom - contentTop;
                if (mouseY < thumbY) {
                    scrollOffset = Math.max(0, scrollOffset - contentHeight);
                } else if (mouseY > thumbY + thumbHeight) {
                    scrollOffset = Math.min(maxScroll, scrollOffset + contentHeight);
                } else {
                    scrolling = true;
                }
                return true;
            }

            int y = contentTop - scrollOffset;
            int left = PADDING + 5;
            int contentRight = width - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN * 2;

            for (String groupName : groupOrder) {
                if (mouseX >= left && mouseX < contentRight && mouseY >= y && mouseY < y + HEADER_HEIGHT) {
                    if (collapsedGroups.contains(groupName)) {
                        collapsedGroups.remove(groupName);
                    } else {
                        collapsedGroups.add(groupName);
                    }
                    return true;
                }
                y += HEADER_HEIGHT;
                if (!collapsedGroups.contains(groupName)) {
                    List<?> cmds = groupCommands.get(groupName);
                    if (cmds != null) {
                        for (Object entryObj : cmds) {
                            if (entryObj instanceof Map<?, ?> entry) {
                                y += LINE_HEIGHT;
                                Object desc = entry.get("description");
                                if (desc != null && !String.valueOf(desc).isEmpty()) y += LINE_HEIGHT;
                            }
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrolling && button == 0 && maxScroll > 0) {
            int contentTop = PADDING;
            int contentBottom = height - CONTENT_BOTTOM_MARGIN;
            int trackHeight = contentBottom - contentTop;
            int thumbHeight = Math.max(20, trackHeight * trackHeight / (trackHeight + maxScroll));
            int scrollbarX = width - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;
            double relY = mouseY - contentTop - thumbHeight / 2.0;
            double ratio = relY / (trackHeight - thumbHeight);
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, (int) (ratio * maxScroll)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) scrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 30));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
