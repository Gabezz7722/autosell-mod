package com.example.autosell;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.PlayerScreenHandler;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                    button.setMessage(Text.literal("Preparing..."));
                    startSellProcess(chestScreen);
                }
        ).dimensions(x, y, 80, 20).build();

        screen.addDrawableChild(btn);
    }

    private void startSellProcess(GenericContainerScreen chestScreen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        // Collect chest slot info (item id and total counts) for slots that are in the chest (not player inventory)
        Map<Identifier, Integer> toMove = new HashMap<>();
        List<Slot> chestSlots = new ArrayList<>();
        for (Slot slot : chestScreen.getScreenHandler().slots) {
            if (slot == null || !slot.hasStack()) continue;
            if (slot.inventory == client.player.getInventory()) continue; // skip player inventory
            chestSlots.add(slot);
            ItemStack stack = slot.getStack();
            Identifier id = Registries.ITEM.getId(stack.getItem());
            toMove.put(id, toMove.getOrDefault(id, 0) + stack.getCount());
        }

        if (chestSlots.isEmpty()) {
            AutoSellConfig.INSTANCE.enabled = false;
            return;
        }

        // Run moving and selling in a background thread to avoid blocking client thread
        new Thread(() -> {
            try {
                // Step 1: while chest is open, QUICK_MOVE each chest slot into player inventory
                for (Slot slot : chestSlots) {
                    if (!AutoSellConfig.INSTANCE.enabled) break;
                    client.execute(() -> {
                        if (client.player == null) return;
                        client.interactionManager.clickSlot(
                                chestScreen.getScreenHandler().syncId,
                                slot.id,
                                0,
                                SlotActionType.QUICK_MOVE,
                                client.player
                        );
                    });
                    Thread.sleep(Math.max(150, AutoSellConfig.INSTANCE.delayMs));
                }

                // Step 2: close the chest screen before sending /ah sell commands
                client.execute(() -> client.setScreen(null));
                Thread.sleep(300); // give the client/server a moment to sync inventories

                // Step 3: for each item id we moved, find matching stacks in player's inventory and sell them
                if (client.player == null) return;
                var playerHandler = client.player.playerScreenHandler; // player's own handler

                for (Map.Entry<Identifier, Integer> entry : toMove.entrySet()) {
                    if (!AutoSellConfig.INSTANCE.enabled) break;
                    Identifier itemId = entry.getKey();

                    // Iterate player handler slots and sell stacks that match itemId
                    for (Slot pSlot : playerHandler.slots) {
                        if (!AutoSellConfig.INSTANCE.enabled) break;
                        if (pSlot == null || !pSlot.hasStack()) continue;
                        if (pSlot.inventory != client.player.getInventory()) continue;

                        ItemStack pStack = pSlot.getStack();
                        Identifier pid = Registries.ITEM.getId(pStack.getItem());
                        if (!pid.equals(itemId)) continue;

                        int count = pStack.getCount();
                        String formatted = formatCount(count);

                        // Pick up the stack into cursor
                        client.execute(() -> {
                            if (client.player == null) return;
                            client.interactionManager.clickSlot(
                                    playerHandler.syncId,
                                    pSlot.id,
                                    0,
                                    SlotActionType.PICKUP,
                                    client.player
                            );
                        });

                        Thread.sleep(Math.max(120, AutoSellConfig.INSTANCE.delayMs));

                        // Send the /ah sell <count> command while the chest is closed
                        client.execute(() -> {
                            if (client.player != null && client.player.networkHandler != null) {
                                String command = "/ah sell " + formatted;
                                client.player.networkHandler.sendChatMessage(command);
                            }
                        });

                        Thread.sleep(Math.max(180, AutoSellConfig.INSTANCE.delayMs));

                        // Try to clear cursor by clicking the same slot (if server didn't consume it)
                        client.execute(() -> {
                            if (client.player == null) return;
                            client.interactionManager.clickSlot(
                                    playerHandler.syncId,
                                    pSlot.id,
                                    0,
                                    SlotActionType.PICKUP,
                                    client.player
                            );
                        });

                        Thread.sleep(Math.max(120, AutoSellConfig.INSTANCE.delayMs));
                    }
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
