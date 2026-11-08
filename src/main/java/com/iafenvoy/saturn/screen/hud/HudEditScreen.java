package com.iafenvoy.saturn.screen.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The screen the player arranges HUD elements on: pick one up with the left button, drop it anywhere, and the new
 * placement is written to the layout file as it moves. Elements keep drawing through the GUI layer while this screen
 * is open, so the real element is being dragged rather than a picture of one. The eight squares along the border are
 * the window anchors: dragging over one binds the element to it, and a line says which one the element in hand is
 * bound to. The checkbox in an element's top-right corner is whether it is drawn at all.
 */
public final class HudEditScreen extends Screen {
    private static final int IDLE_FILL = 0x33FFFFFF;
    private static final int IDLE_OUTLINE = 0x66FFFFFF;
    private static final int HIDDEN_FILL = 0x14FFFFFF;
    private static final int HIDDEN_OUTLINE = 0x30FFFFFF;
    private static final int SELECTED_FILL = 0x44FFD24A;
    private static final int SELECTED_OUTLINE = 0xFFFFD24A;
    private static final int LABEL_COLOR = 0xFFFFD24A;
    private static final int HIDDEN_LABEL_COLOR = 0x80FFD24A;
    private static final int ANCHOR_SIZE = 12;
    private static final int ANCHOR_FILL = 0x22FFFFFF;
    private static final int ANCHOR_OUTLINE = 0x99FFFFFF;
    private static final int ANCHOR_ACTIVE_FILL = 0x44FFD24A;
    private static final int ANCHOR_ACTIVE_OUTLINE = 0xFFFFD24A;
    private static final int LINE_COLOR = 0xCCFFD24A;
    private static final int CHECKBOX_FILL = 0xDD0B0E14;
    private static final int CHECKBOX_OUTLINE = 0xFFFFFFFF;
    private static final int CHECKBOX_ON = 0xFF74E07A;
    private static final int CHECKBOX_OFF = 0xFFE07A7A;
    private static final int CHECKBOX_MAX_SIDE = 9;
    private static final int CHECKBOX_MIN_SIDE = 5;
    private static final int CHECKBOX_SLACK = 2;

    private HudEntry selected;
    private int grabOffsetX;
    private int grabOffsetY;

    public HudEditScreen() {
        super(Component.translatable("screen.saturn.hud_layout"));
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        List<HudEntry> moveable = HudManager.moveableEntries();
        this.renderAnchors(graphics);
        // Drawn before the frames, so the line runs under the element it belongs to; and only for the element in
        // hand, because one line per bound element would read as a spider web.
        if (this.selected != null) this.renderAnchorLine(graphics, this.selected);
        for (HudEntry entry : moveable) this.renderFrame(graphics, entry);
        if (moveable.isEmpty()) {
            Component empty = Component.translatable("screen.saturn.hud_layout.empty");
            graphics.drawString(this.font, empty, this.width / 2 - this.font.width(empty) / 2, this.height / 2,
                    0xAAAAAA, true);
        } else {
            // The controls are not discoverable on their own - there is no cursor change to hint at them, because
            // this port has no corner to grab - so they are written down.
            Component hint = Component.translatable("screen.saturn.hud_layout.hint");
            graphics.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height - 44,
                    0xAAAAAA, true);
        }
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(this.width / 2 - 60, this.height - 30, 120, 20).build());
    }

    // A plain translucent fill keeps the HUD behind the editor crisp: the vanilla blurred background would blur the
    // very elements being placed.
    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean consumed = super.mouseClicked(mouseX, mouseY, button);
        if (consumed) return true;
        if (button != 0) return false;
        HudEntry toggled = this.checkboxAt(mouseX, mouseY);
        if (toggled != null) {
            toggled.setVisible(!toggled.visible());
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        HudEntry hit = HudManager.at(mouseX, mouseY);
        this.setSelected(hit);
        if (hit == null) return false;
        this.grabOffsetX = (int) mouseX - hit.x();
        this.grabOffsetY = (int) mouseY - hit.y();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.selected != null) {
            // Binding first and moving second: binding only changes what the position is measured from and leaves
            // the element where it is, so the move that follows is the one the player sees.
            this.bindUnder(mouseX, mouseY);
            this.selected.setPosition((int) mouseX - this.grabOffsetX, (int) mouseY - this.grabOffsetY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // The selection is kept: the line to the anchor and the arrow keys stay useful once the button is up.
        // Clicking empty space or pressing Escape is what drops it.
        if (this.selected != null) this.selected.setDragging(false);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Arrow keys are the keyboard's version of dragging: one pixel at a time, or ten with a modifier - the
        // only way to place an entry precisely, and the only way at all without holding a mouse button down.
        if (this.selected != null) {
            int step = hasShiftDown() ? 10 : 1;
            if (keyCode == InputConstants.KEY_LEFT) return this.nudge(-step, 0);
            if (keyCode == InputConstants.KEY_RIGHT) return this.nudge(step, 0);
            if (keyCode == InputConstants.KEY_UP) return this.nudge(0, -step);
            if (keyCode == InputConstants.KEY_DOWN) return this.nudge(0, step);
            if (keyCode == InputConstants.KEY_ESCAPE) {
                this.setSelected(null);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        this.setSelected(null);
        super.removed();
    }

    private void renderAnchors(GuiGraphics graphics) {
        HudAnchor bound = this.selected == null ? null : this.selected.anchor();
        for (HudAnchor anchor : HudAnchor.values()) {
            ScreenBounds marker = anchor.marker(this.width, this.height, ANCHOR_SIZE);
            boolean highlighted = anchor == bound;
            graphics.fill(marker.x(), marker.y(), marker.xEnd(), marker.yEnd(),
                    highlighted ? ANCHOR_ACTIVE_FILL : ANCHOR_FILL);
            graphics.renderOutline(marker.x(), marker.y(), marker.width(), marker.height(),
                    highlighted ? ANCHOR_ACTIVE_OUTLINE : ANCHOR_OUTLINE);
        }
    }

    private void renderFrame(GuiGraphics graphics, HudEntry entry) {
        ScreenBounds bounds = entry.bounds();
        boolean shown = entry.visible();
        boolean highlight = entry == this.selected;
        graphics.fill(bounds.x(), bounds.y(), bounds.xEnd(), bounds.yEnd(),
                highlight ? SELECTED_FILL : shown ? IDLE_FILL : HIDDEN_FILL);
        graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                highlight ? SELECTED_OUTLINE : shown ? IDLE_OUTLINE : HIDDEN_OUTLINE);
        // Named by what the entry calls itself rather than by a translation key: an entry whose name has not
        // been written yet is exactly the one worth spotting here.
        graphics.drawString(this.font, entry.displayName(), bounds.x(), bounds.y() - 10,
                shown ? LABEL_COLOR : HIDDEN_LABEL_COLOR, true);
        this.renderCheckbox(graphics, entry, shown);
    }

    @SuppressWarnings("DuplicateExpressions")
    private void renderCheckbox(GuiGraphics graphics, HudEntry entry, boolean shown) {
        ScreenBounds box = checkbox(entry);
        graphics.fill(box.x(), box.y(), box.xEnd(), box.yEnd(), CHECKBOX_FILL);
        graphics.renderOutline(box.x(), box.y(), box.width(), box.height(), CHECKBOX_OUTLINE);
        // The mark stays opaque for a hidden element too: the box is how it comes back.
        int color = shown ? CHECKBOX_ON : CHECKBOX_OFF;
        int left = box.x();
        int top = box.y();
        int right = box.xEnd();
        int bottom = box.yEnd();
        if (shown) {
            this.line(graphics, left + box.width() / 4, top + box.height() * 5 / 8, left + box.width() * 2 / 5, top + box.height() * 3 / 4, color);
            this.line(graphics, left + box.width() * 2 / 5, top + box.height() * 3 / 4, right - box.width() / 4, top + box.height() / 4, color);
        } else {
            this.line(graphics, left + box.width() / 4, top + box.height() / 4, right - box.width() / 4, bottom - box.height() / 4, color);
            this.line(graphics, left + box.width() / 4, bottom - box.height() / 4, right - box.width() / 4, top + box.height() / 4, color);
        }
    }

    private void renderAnchorLine(GuiGraphics graphics, HudEntry entry) {
        ScreenBounds marker = entry.anchor().marker(this.width, this.height, ANCHOR_SIZE);
        ScreenBounds bounds = entry.bounds();
        this.line(graphics, marker.x() + marker.width() / 2, marker.y() + marker.height() / 2,
                bounds.x() + entry.anchor().toLeft(bounds.width()),
                bounds.y() + entry.anchor().toTop(bounds.height()), LINE_COLOR);
    }

    // No line primitive exists and the line has to be a diagonal: the anchors sit along the border while the
    // element is wherever the player left it. One pixel per step, so the cost is the length of the line.
    private void line(GuiGraphics graphics, int fromX, int fromY, int toX, int toY, int color) {
        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
        if (steps <= 0) {
            graphics.fill(fromX, fromY, fromX + 1, fromY + 1, color);
            return;
        }
        for (int step = 0; step <= steps; step++) {
            int x = fromX + (toX - fromX) * step / steps;
            int y = fromY + (toY - fromY) * step / steps;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    // The box is inside the frame's top-right corner rather than outside it: an outside box would fall off the
    // window at the top edge and overlap whatever sits next to the element. A tiny element gets a tiny box.
    private static ScreenBounds checkbox(HudEntry entry) {
        ScreenBounds bounds = entry.bounds();
        int side = Mth.clamp(Math.min(bounds.width(), bounds.height()), CHECKBOX_MIN_SIDE, CHECKBOX_MAX_SIDE);
        return new ScreenBounds(bounds.xEnd() - side, bounds.y(), side, side);
    }

    // Topmost first, and with the slack a small box needs to be clickable at all.
    private HudEntry checkboxAt(double pointX, double pointY) {
        List<HudEntry> moveable = HudManager.moveableEntries();
        for (int index = moveable.size() - 1; index >= 0; index--) {
            ScreenBounds box = checkbox(moveable.get(index));
            if (pointX >= box.x() - CHECKBOX_SLACK && pointX < box.xEnd() + CHECKBOX_SLACK
                    && pointY >= box.y() - CHECKBOX_SLACK && pointY < box.yEnd() + CHECKBOX_SLACK)
                return moveable.get(index);
        }
        return null;
    }

    // The anchor the mouse is over, if any: an element dragged onto one is measured from it from then on, which is
    // what keeps its distance from that edge when the window changes size.
    private void bindUnder(double pointX, double pointY) {
        for (HudAnchor anchor : HudAnchor.values())
            if (anchor.marker(this.width, this.height, ANCHOR_SIZE).contains(pointX, pointY)) {
                this.selected.setAnchor(anchor);
                return;
            }
    }

    private boolean nudge(int deltaX, int deltaY) {
        this.selected.setPosition(this.selected.x() + deltaX, this.selected.y() + deltaY);
        return true;
    }

    // Moves the edit screen's idea of "what is in hand" and tells the outgoing element, which is what lets an
    // entry draw itself differently while it is held.
    private void setSelected(HudEntry entry) {
        if (this.selected == entry) return;
        if (this.selected != null) this.selected.setDragging(false);
        this.selected = entry;
        if (entry == null) return;
        entry.setDragging(true);
        // Picking something up is worth a sound: a misplaced click and a successful grab otherwise look the
        // same until the mouse moves, which reads as the drag having failed.
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
