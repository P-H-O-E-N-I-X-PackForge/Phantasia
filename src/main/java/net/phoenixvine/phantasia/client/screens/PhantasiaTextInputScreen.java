package net.phoenixvine.phantasia.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * PhantasiaTextInputScreen
 *
 * A focused modal subscreen for text/number entry that needs more room than
 * the inline strip boxes in the editor provide (annotation labels, global
 * mistake notes, captions, etc.).
 *
 * Opens on top of the editor, returns the entered value via {@code onConfirm}
 * and restores the editor on cancel or confirm.
 */
@OnlyIn(Dist.CLIENT)
public class PhantasiaTextInputScreen extends Screen {

    private static final int C_BG = 0xBB000000;
    private static final int C_PANEL = 0xFF0C0C1A;
    private static final int C_ACCENT = 0xFF4FC3F7;
    private static final int C_DIM = 0xFF667788;
    private static final int C_TEXT = 0xFFDDDDDD;
    private static final int C_BTN = 0xBB151528;
    private static final int C_BTN_H = 0xBB1A2840;
    private static final int C_GREEN = 0xFF66BB6A;

    private final Screen parent;
    private final String title;
    private final String hint;
    private final String initial;
    private final int maxLength;
    private final Consumer<String> onConfirm;

    private EditBox inputBox;

    /**
     * @param parent    screen to return to after confirm/cancel
     * @param title     prompt shown above the box (e.g. "Annotation Label")
     * @param hint      placeholder text
     * @param initial   pre-filled value (current value of the field)
     * @param maxLength max characters
     * @param onConfirm called with the final string on confirm; not called on cancel
     */
    public PhantasiaTextInputScreen(Screen parent, String title, String hint,
                                    String initial, int maxLength,
                                    Consumer<String> onConfirm) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.hint = hint;
        this.initial = initial != null ? initial : "";
        this.maxLength = maxLength;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        super.init();

        int pw = Math.min(400, this.width - 40);
        int px = (this.width - pw) / 2;
        int py = (this.height - 80) / 2;

        inputBox = addRenderableWidget(new EditBox(font, px + 8, py + 28, pw - 16, 16,
                Component.empty()));
        inputBox.setMaxLength(maxLength);
        inputBox.setHint(Component.literal(hint));
        inputBox.setValue(initial);
        inputBox.setBordered(true);
        setInitialFocus(inputBox);
        // Select all so the user can immediately replace
        inputBox.setHighlightPos(0);
        inputBox.moveCursorToEnd();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        g.fill(0, 0, this.width, this.height, C_BG);

        int pw = Math.min(400, this.width - 40);
        int ph = 80;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        g.fill(px, py, px + pw, py + ph, C_PANEL);
        g.fill(px, py, px + pw, py + 1, C_ACCENT);

        g.drawCenteredString(font, title, px + pw / 2, py + 6, C_ACCENT);

        super.render(g, mx, my, partial);

        int btnY = py + ph - 20;
        int half = pw / 2 - 4;

        drawBtn(g, mx, my, px + 8, btnY, half, 14, "✓ Confirm", C_GREEN);
        drawBtn(g, mx, my, px + pw / 2 + 4, btnY, half, 14, "✕ Cancel", C_BTN);
    }

    private void drawBtn(GuiGraphics g, int mx, int my, int x, int y, int w, int h,
                         String label, int col) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hov ? C_BTN_H : col);
        if (hov) {
            g.fill(x, y, x + w, y + 1, C_ACCENT);
            g.fill(x, y + h - 1, x + w, y + h, C_ACCENT);
        }
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, hov ? C_ACCENT : C_TEXT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (super.mouseClicked(mx, my, btn)) return true;

        int pw = Math.min(400, this.width - 40);
        int ph = 80;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        int btnY = py + ph - 20;
        int half = pw / 2 - 4;

        if (mx >= px + 8 && mx < px + 8 + half && my >= btnY && my < btnY + 14) {
            confirm();
            return true;
        }
        if (mx >= px + pw / 2 + 4 && mx < px + pw && my >= btnY && my < btnY + 14) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (kc == GLFW.GLFW_KEY_ENTER || kc == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        if (kc == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    private void confirm() {
        String val = inputBox != null ? inputBox.getValue() : initial;
        onConfirm.accept(val);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
