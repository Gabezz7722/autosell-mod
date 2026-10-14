package com.example.autosell;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class AutoSellMod implements ClientModInitializer {
    private static final String BUTTON_TEXT = "Auto Sell";

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GenericContainerScreen) {
                addButton(screen);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen instanceof GenericContainerScreen) {
                addButton(client.currentScreen);
            }
        });
    }

    private void addButton(Screen screen) {
        if (!(screen instanceof GenericContainerScreen chestScreen)) return;

        for (var child : screen.children()) {
            if (child instanceof ButtonWidget btn && btn.getMessage() != null && btn.getMessage().getString().equals(BUTTON_TEXT)) {
                return;
            }
        }

        int x = screen.width - 90;
        int y = 8;

        ButtonWidget btn = ButtonWidget.builder(
                Text.literal(BUTTON_TEXT),
                button -> {
                    if (AutoSellConfig.INSTANCE.enabled) {
                        AutoSellConfig.INSTANCE.enabled = false;
                        button.setMessage(Text.literal(BUTTON_TEXT));
                        return;
                    }
                    AutoSellConfig.INSTANCE.enabled = true;
                    button.setMessage(Text.literal("Selling..."));
                    openConfigOrRun(chestScreen);
                }
        ).dimensions(x, y, 80, 20).build();

        screen.addDrawableChild(btn);
    }

    private void openConfigOrRun(GenericContainerScreen chestScreen) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || client.world == null) return;

        // This is intentionally simple: press button once to start selling.
        // If you want config menu instead, call AutoSellScreen.open() here.
        sellFromChest(chestScreen);
    }

    private void sellFromChest(GenericContainerScreen chestScreen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        if (!AutoSellConfig.INSTANCE.enabled) return;

        // We sell every non-empty stack in the chest inventory except the player's inventory
        List<Slot> sellableSlots = new ArrayList<>();
        for (Slot slot : chestScreen.getScreenHandler().slots) {
            if (slot == null || !slot.hasStack()) continue;
            if (slot.inventory == client.player.getInventory()) continue;
            sellableSlots.add(slot);
        }

        if (sellableSlots.isEmpty()) {
            AutoSellConfig.INSTANCE.enabled = false;
            return;
        }

        new Thread(() -> {
            try {
                for (Slot slot : sellableSlots) {
                    if (!AutoSellConfig.INSTANCE.enabled) break;

                    ItemStack stack = slot.getStack();
                    if (stack == null || stack.isEmpty()) continue;

                    // Hold the item in cursor/hand by moving the slot into the player's hand
                    // This is highly server-dependent. We approximate a click action.
                    client.execute(() -> {
                        if (client.player == null) return;

                        // Quick approach: if the slot is valid, attempt to swap it into the active hand
                        // This is the part that may need tuning depending on your server and client.
                        // We assume the slot index is valid and the item is picked up.
                        client.interactionManager.clickSlot(
                                chestScreen.getScreenHandler().syncId,
                                slot.id,
                                0,
                                net.minecraft.screen.slot.SlotActionType.PICKUP,
                                client.player
                        );
                    });

                    Thread.sleep(Math.max(120, AutoSellConfig.INSTANCE.delayMs));

                    int count = stack.getCount();
                    String formatted = formatCount(count);

                    // Send the exact chat syntax: /ah sell <count>
                    client.execute(() -> {
                        if (client.player != null && client.player.networkHandler != null) {
                            String command = "/ah sell " + formatted;
                            client.player.networkHandler.sendChatMessage(command);
                        }
                    });

                    Thread.sleep(Math.max(180, AutoSellConfig.INSTANCE.delayMs));

                    // Try to clear the held item/cursor
                    client.execute(() -> {
                        if (client.player != null && client.player.networkHandler != null) {
                            // no-op: server handles sell and clears the item if it was applied correctly
                        }
                    });

                    Thread.sleep(Math.max(120, AutoSellConfig.INSTANCE.delayMs));
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                AutoSellConfig.INSTANCE.enabled = false;
            }
        }).start();
    }

    private static String formatCount(int count) {
        if (count < 1000) return String.valueOf(count);

        double k = count / 1000.0;
        if (count % 1000 == 0) return (int)k + "k";
        if (k < 10.0) return String.format("%.1f", k).replace(".0", "") + "k";
        return String.valueOf(Math.round(k)) + "k";
    }
}
