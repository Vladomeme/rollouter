package net.rollouter.main;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Stack;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class Rollouter implements ModInitializer, SuggestionProvider {

    public static final Logger LOGGER = LoggerFactory.getLogger("rollouter");
    private static final MinecraftClient client = MinecraftClient.getInstance();

    final ArrayList<String> files = new ArrayList<>();
    static final Stack<Brush> queue = new Stack<>();
    static int delay = 2;
    int ticks;

    @Override
    public void onInitialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {


            @Override
            public Identifier getFabricId() {
                return new Identifier("rollouter", "brush_rolls");
            }

            @Override
            public void reload(ResourceManager manager) {
                files.clear();

                manager.findResources("brush_rolls", id -> id.getPath().endsWith(".json")).keySet().forEach(id -> {
                    if (manager.getResource(id).isPresent()) files.add(Path.of(id.getPath()).getFileName().toString());
                });

                if (!Files.exists(Path.of(FabricLoader.getInstance().getConfigDir() + "/rollouter"))) {
                    try {
                        Files.createDirectories(Path.of(FabricLoader.getInstance().getConfigDir() + "/rollouter"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                else {
                    File[] rolls = new File(FabricLoader.getInstance().getConfigDir() + "/rollouter").listFiles();
                    if (rolls != null)
                        for (File file : rolls) {
                            if (!file.getName().endsWith("json")) continue;
                            files.add(file.getName());
                        }
                }

                addCommand();

                ClientTickEvents.END_CLIENT_TICK.register(client -> {
                    if (--ticks == 0 && !queue.empty()) nextBrush();
                });
            }

        });
        LOGGER.info("Rollouter loaded!");
    }

    public void addCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("rollout")
                        .then(argument("file", StringArgumentType.string())
                                .suggests(this::getSuggestions).executes(context -> {
                                    unroll(StringArgumentType.getString(context, "file"));
                                    return 1;
                                })
                                .then(argument("delay", IntegerArgumentType.integer()).executes(context -> {
                                            delay = IntegerArgumentType.getInteger(context, "delay");
                                            unroll(StringArgumentType.getString(context, "file"));
                                            return 1;
                                        }
                                )))));
    }

    public void unroll(String file) {
        if (client.player == null) return;
        LOGGER.info("Unrolling " + file);

        try {
            Brush[] brushes;
            if (isFileInRP(file)) brushes = new Gson().fromJson(JsonParser.parseReader(new InputStreamReader(client.getResourceManager().getResource(
                    new Identifier("rollouter", "brush_rolls/" + file)).get().getInputStream())).getAsJsonObject(), Roll.class).brushes();
            else brushes = new Gson().fromJson(Files.readString(
                    Path.of(FabricLoader.getInstance().getConfigDir() + "/rollouter/" + file)), Roll.class).brushes();

            LOGGER.info("Length: " + brushes.length);
            for (int i = 0; i < Math.min(brushes.length, 9); i++) {
                queue.push(brushes[i].withSlot(i));
                LOGGER.info(brushes[i].command());
            }
            if (brushes.length > 9) client.inGameHud.getChatHud().addMessage(Text.of("Brush roll had more than 9 entries!"));

            ticks = 1;
        } catch (IOException e) {
            LOGGER.error("Error occurred while unrolling " + file, e);
        }
    }

    public void giveBrush(Brush brush) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        PlayerInventory inventory = client.player.getInventory();

        inventory.selectedSlot = brush.slot();
        ItemStack stack = new ItemStack(Registries.ITEM.get(
                new Identifier("minecraft", brush.item())));
        if (brush.name() != null) stack.setCustomName(Text.of(brush.name()));

        client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(brush.slot()));

        inventory.setStack(brush.slot(), stack);

        client.getNetworkHandler().sendPacket(new CreativeInventoryActionC2SPacket(brush.slot() + 36, stack));
        if (brush.command() != null) {
            if (brush.command().length() < 256) client.getNetworkHandler().sendCommand(brush.command());
            else client.inGameHud.getChatHud().addMessage(Text.of("Command for brush " + brush.slot() + "is too long!"));
        }
    }

    public void nextBrush() {
        if (client.player == null) return;

        giveBrush(queue.pop());

        if (queue.empty()) delay = 2;
        else ticks = delay;
    }

    private boolean isFileInRP(String file) {
        return client.getResourceManager().getResource(new Identifier("rollouter", "brush_rolls/" + file)).isPresent();
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext context, SuggestionsBuilder builder) {
        files.forEach(builder::suggest);
        return builder.buildFuture();
    }
}
