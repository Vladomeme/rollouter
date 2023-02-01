package net.rollouter.main;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryListener;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.rollouter.main.util.Brush;
import net.rollouter.main.util.QueuedBrush;
import net.rollouter.main.util.Roll;
import net.rollouter.main.util.UnrollTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class Rollouter implements ModInitializer, SuggestionProvider {

    public static final Logger LOGGER = LoggerFactory.getLogger("rollouter");
    Gson gson = new Gson();
    String fileName;
    ArrayList<String> fileNames = new ArrayList<>();
    static Stack<QueuedBrush> brushQueue = new Stack<>();
    static int delay = 2;

    @Override
    public void onInitialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {

            @Override
            public Identifier getFabricId() {
                return new Identifier("rollouter", "brush_rolls");
            }

            @Override
            public void reload(ResourceManager manager) {
                if (fileNames != null) {
                    fileNames.clear();
                }
                manager.findResources("brush_rolls", id -> id.getPath().endsWith(".json")).keySet().forEach(id -> {
                    try (InputStream stream = manager.getResource(id).get().getInputStream()) {
                        fileNames.add(Path.of(id.getPath()).getFileName().toString());
                    } catch (Exception e) {
                        LOGGER.error("Error occurred while loading resource json " + id.toString(), e);
                    }
                });
                addCommand();
            }
        });
        LOGGER.info("Rollouter loaded!");
    }

    public void addCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("rollout")
                            .then(argument("file_name", StringArgumentType.string())
                                    .suggests(this::getSuggestions).executes(context -> {
                                        fileName = StringArgumentType.getString(context, "file_name");
                                        moveToQueue(fileName);
                                        return 1;
                                    }).then(argument("delayTicks", StringArgumentType.string()).executes(context -> {
                                                        delay = Integer.parseInt(StringArgumentType.getString(context, "delayTicks"));
                                                        moveToQueue(fileName);
                                                        return 1;
                                                    }
                                            )
                                    )
                            )
            );
        });
    }

    public void moveToQueue(String fileName) {
        LOGGER.info("Unrolling " + fileName);
        Roll roll = null;
        try {
            roll = gson.fromJson(JsonParser.parseReader(new InputStreamReader(MinecraftClient.getInstance().getResourceManager().getResource(
                    new Identifier("rollouter", "brush_rolls/" + fileName)).get().getInputStream())).getAsJsonObject(), Roll.class);
            if (roll.getSlot9() != null) {
                brushQueue.push(new QueuedBrush(roll.getSlot9(), 8));
            }
            if (roll.getSlot8() != null) {
                brushQueue.push(new QueuedBrush(roll.getSlot8(), 7));
            }
            if (roll.getSlot7() != null) {
                brushQueue.push(new QueuedBrush(roll.getSlot7(), 6));
            }
            if (roll.getSlot6() != null) {
                brushQueue.push(new QueuedBrush(roll.getSlot6(), 5));
            }
            if (roll.getSlot5() != null) {
                brushQueue.push(new QueuedBrush(roll.getSlot5(), 4));
            }
            if (roll.getSlot4() != null) {
                brushQueue.push(new QueuedBrush(roll.getSlot4(), 3));
            }
            if (roll.getSlot3() != null) {
                brushQueue.push(new QueuedBrush(roll.getSlot3(), 2));
            }
            if (roll.getSlot2() != null) {
                brushQueue.push(new QueuedBrush(roll.getSlot2(), 1));
            }
            if (roll.getSlot1() != null) {
                brushQueue.push(new QueuedBrush(roll.getSlot1(), 0));
            }
            ((UnrollTimer) MinecraftClient.getInstance().player).unroll_setTimer(1);
        } catch (IOException e) {
            LOGGER.error("Error occurred while loading resource json " + fileName, e);
        }
    }

    public static void unrollBrush(Brush brush, int slot) {
        PlayerInventory inventory = MinecraftClient.getInstance().player.getInventory();
        inventory.selectedSlot = slot;
        MinecraftClient.getInstance().getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        ItemStack stack;
        if (brush.getName() != null) {
            stack = new ItemStack(Registry.ITEM.get(
                    new Identifier("minecraft", brush.getItem()))).setCustomName(Text.of(brush.getName()));
        } else {
            stack = new ItemStack(Registry.ITEM.get(
                    new Identifier("minecraft", brush.getItem())));
        }
        inventory.setStack(slot, stack);
        MinecraftClient.getInstance().getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(slot + 36, stack));
        if (brush.getCommand() != null) {
            MinecraftClient.getInstance().player.sendCommand(brush.getCommand());
        }
    }

    public static void unrollNextBrush() {
        QueuedBrush queuedBrush = brushQueue.pop();
        unrollBrush(queuedBrush.getBrush(), queuedBrush.getSlot());
        if (!brushQueue.empty()) {
            ((UnrollTimer) (Object) MinecraftClient.getInstance().player).unroll_setTimer(delay);
        } else {
            delay = 2;
        }
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext context, SuggestionsBuilder builder) {
        fileNames.forEach(filename -> {
            builder.suggest(filename);
        });
        return builder.buildFuture();
    }
}
