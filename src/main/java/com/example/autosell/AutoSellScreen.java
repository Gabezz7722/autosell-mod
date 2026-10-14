package com.example.autosell;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class AutoSellScreen extends Screen {
    private TextFieldWidget delayField;

    public AutoSellScreen() {
        super(Text.literal("Auto Sell Settings"));
    }

    @Override
    protected void init() {
        super.init();

        delayField = new TextFieldWidget(this.textRenderer, width / 2 - 80, height / 2 - 20, 160, 20, Text.literal("Delay"));
        delayField.setText(String.valueOf(AutoSellConfig.INSTANCE.delayMs));
        addDrawableChild(delayField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> {
            try {
                AutoSellConfig.INSTANCE.delayMs = Integer.parseInt(delayField.getText());
            } catch (NumberFormatException ignored) {
                AutoSellConfig.INSTANCE.delayMs = 500;
            }
            close();
        }).dimensions(width / 2 - 60, height / 2 + 20, 120, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Auto Sell Settings"), width / 2, 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Delay (ms)"), width / 2, height / 2 - 40, 0xFFFFFF);
    }
}
