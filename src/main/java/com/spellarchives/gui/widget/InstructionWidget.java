package com.spellarchives.gui.widget;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import com.spellarchives.util.TextUtils;

public class InstructionWidget extends BaseWidget {
    private final FontRenderer fontRenderer;
    private final List<String> instructionBlocks = new ArrayList<>();
    private boolean alignBottom = false;
    private int color = 0xFFFFFF;
    private int lineHeight;

    public InstructionWidget(Minecraft mc, FontRenderer fontRenderer) {
        super(mc);

        this.fontRenderer = fontRenderer;
        this.lineHeight = fontRenderer.FONT_HEIGHT;
    }

    public void clear() {
        instructionBlocks.clear();
    }

    public void addInstruction(String instruction) {
        if (instruction != null && !instruction.isEmpty()) instructionBlocks.add(instruction);
    }

    public void setAlignBottom(boolean alignBottom) {
        this.alignBottom = alignBottom;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setLineHeight(int height) {
        this.lineHeight = height;
    }

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        if (!visible || instructionBlocks.isEmpty() || width <= 0 || height <= 0) return;

        List<String> allWrappedLines = new ArrayList<>();
        for (String block : instructionBlocks) {
            allWrappedLines.addAll(TextUtils.wrapTextToWidth(fontRenderer, block, width));
        }

        if (allWrappedLines.isEmpty()) return;

        int linesCapacity = height / lineHeight;
        if (allWrappedLines.size() > linesCapacity) {
            if (alignBottom) {
                allWrappedLines = allWrappedLines.subList(allWrappedLines.size() - linesCapacity, allWrappedLines.size());
            } else {
                allWrappedLines = allWrappedLines.subList(0, linesCapacity);
            }
        }

        int drawnHeight = allWrappedLines.size() * lineHeight;
        int startY = alignBottom ? (y + height - drawnHeight) : y;

        for (String line : allWrappedLines) {
            fontRenderer.drawString(line, x, startY, color);
            startY += lineHeight;
        }
    }
}
