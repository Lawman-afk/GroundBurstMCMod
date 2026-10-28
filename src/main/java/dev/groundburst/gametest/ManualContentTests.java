package dev.groundburst.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.groundburst.content.manual.GBManual;
import dev.groundburst.content.multiblock.GBMultiblocks;
import dev.groundburst.content.recipe.ProcessingKind;
import dev.groundburst.content.recipe.ProcessingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * The Field Manual's content (assets/groundburst/manual, written by tools/manual/content.py) against the game: every
 * item exists, every crafting, furnace and machine page has a recipe to show, every structure and image exists.
 */
final class ManualContentTests {
    private ManualContentTests() {}

    static void register() {
        GBGameTests.test("manual_content_matches_the_game", GBGameTests.FLOOR_16, 20, ManualContentTests::content);
        GBGameTests.test("manual_item_exists", GBGameTests.FLOOR_16, 20, helper -> {
            helper.assertTrue(GBManual.FIELD_MANUAL.get().getDefaultMaxStackSize() == 1, "the manual does not stack");
            helper.succeed();
        });
    }

    private static JsonObject read(String path) {
        InputStream in = ManualContentTests.class.getResourceAsStream("/assets/groundburst/manual/" + path);
        if (in == null) {
            throw new IllegalStateException("missing manual file " + path);
        }
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void content(GameTestHelper helper) {
        var level = helper.getLevel();
        var context = SlotDisplayContext.fromLevel(level);
        var recipes = level.getServer().getRecipeManager().getRecipes();
        List<String> problems = new ArrayList<>();
        JsonObject index = read("index.json");
        for (JsonElement c : index.getAsJsonArray("chapters")) {
            JsonObject chapter = read("chapters/" + c.getAsString() + ".json");
            item(chapter.get("icon").getAsString(), "chapter " + c.getAsString(), problems);
        }
        int pages = 0;
        for (JsonElement e : index.getAsJsonArray("entries")) {
            String id = e.getAsString();
            JsonObject entry = read("entries/" + id + ".json");
            item(entry.get("icon").getAsString(), id, problems);
            for (JsonElement i : entry.getAsJsonArray("items")) {
                item(i.getAsString(), id, problems);
            }
            for (JsonElement p : entry.getAsJsonArray("pages")) {
                pages++;
                JsonObject page = p.getAsJsonObject();
                String type = page.get("type").getAsString();
                switch (type) {
                    case "spotlight" -> item(page.get("item").getAsString(), id, problems);
                    case "crafting", "smelting" -> {
                        Item item = item(page.get("item").getAsString(), id, problems);
                        boolean smelting = type.equals("smelting");
                        boolean found = item != null && recipes.stream().anyMatch(r -> (smelting ? r.value() instanceof AbstractCookingRecipe
                                : r.value() instanceof CraftingRecipe) && r.value().display().stream()
                                .anyMatch(d -> d.result().resolveForFirstStack(context).is(item)));
                        if (!found) {
                            problems.add(id + ": no " + type + " recipe makes " + page.get("item").getAsString());
                        }
                    }
                    case "machine" -> {
                        String kind = page.get("kind").getAsString();
                        ProcessingKind k = null;
                        for (ProcessingKind pk : ProcessingKind.values()) {
                            if (pk.id().equals(kind)) {
                                k = pk;
                            }
                        }
                        if (k == null) {
                            problems.add(id + ": unknown machine " + kind);
                            break;
                        }
                        Item item = page.has("item") ? item(page.get("item").getAsString(), id, problems) : null;
                        Identifier fluid = page.has("fluid") ? Identifier.parse(page.get("fluid").getAsString()) : null;
                        ProcessingKind kk = k;
                        boolean found = recipes.stream().filter(r -> r.value() instanceof ProcessingRecipe pr && pr.kind() == kk).anyMatch(r -> {
                            ProcessingRecipe pr = (ProcessingRecipe) r.value();
                            boolean itemOk = item == null || pr.outputs().stream().anyMatch(o -> o.item().item().value() == item);
                            boolean fluidOk = fluid == null || pr.fluidOutputs().stream()
                                    .anyMatch(f -> BuiltInRegistries.FLUID.getKey(f.fluid().value()).equals(fluid));
                            return itemOk && fluidOk;
                        });
                        if (!found) {
                            problems.add(id + ": no " + kind + " recipe for " + page);
                        }
                    }
                    case "structure" -> {
                        if (GBMultiblocks.byId(Identifier.parse(page.get("structure").getAsString())) == null) {
                            problems.add(id + ": unknown structure " + page.get("structure").getAsString());
                        }
                    }
                    case "image" -> {
                        Identifier image = Identifier.parse(page.get("image").getAsString());
                        String path = "/assets/" + image.getNamespace() + "/" + image.getPath();
                        if (ManualContentTests.class.getResource(path) == null) {
                            problems.add(id + ": missing image " + image);
                        }
                    }
                    case "text", "table", "links" -> {
                    }
                    default -> problems.add(id + ": unknown page type " + type);
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "manual problems: " + problems);
        helper.assertTrue(pages > 50, "the manual has its pages, " + pages);
        helper.succeed();
    }

    private static Item item(String id, String where, List<String> problems) {
        var item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id));
        if (item.isEmpty()) {
            problems.add(where + ": unknown item " + id);
            return null;
        }
        return item.get();
    }
}
