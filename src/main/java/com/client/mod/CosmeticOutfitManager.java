package com.client.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Saves, loads, deletes, exports, and imports standalone Sestia Cosmetic Outfits ({@code SC1:...})
 * completely decoupled from gameplay module configs and private credentials.
 */
public final class CosmeticOutfitManager {

    public static final String SHARE_PREFIX = "SC1:";
    public static final String OUTFIT_FORMAT = "sestia-outfit";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_PAYLOAD_BYTES = 128 * 1024;
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new GsonBuilder().create();

    public record OutfitEnvelope(
            String format,
            int schemaVersion,
            String name,
            long savedAtEpochMs,
            CosmeticsLogic.CosmeticLoadout loadout
    ) {
    }

    public record OutfitSummary(
            Path file,
            String fileName,
            String name,
            long savedAtEpochMs,
            int activeSlots,
            CosmeticsLogic.CosmeticLoadout loadout
    ) {
    }

    private CosmeticOutfitManager() {
    }

    public static Path getDefaultOutfitsDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("sestia").resolve("outfits");
    }

    public static String normalizeOutfitName(String rawName) {
        if (rawName == null) {
            return "Outfit";
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            return "Outfit";
        }
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    public static OutfitEnvelope createEnvelope(String name, CosmeticsLogic.CosmeticLoadout loadout) {
        CosmeticsLogic.CosmeticLoadout clean = loadout != null ? loadout.sanitized() : CosmeticsLogic.defaultLoadout();
        return new OutfitEnvelope(OUTFIT_FORMAT, SCHEMA_VERSION, normalizeOutfitName(name), System.currentTimeMillis(), clean);
    }

    public static String exportShareCode(String name, CosmeticsLogic.CosmeticLoadout loadout) {
        OutfitEnvelope env = createEnvelope(name, loadout);
        JsonObject root = new JsonObject();
        root.addProperty("n", env.name());
        root.add("c", SestiaChatProtocol.serializeCosmeticsV2(env.loadout()));
        byte[] utf8 = COMPACT_GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        return SHARE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(deflateRaw(utf8));
    }

    public static OutfitEnvelope parseShareCode(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            throw new IllegalArgumentException("Empty outfit share code");
        }
        String trimmed = rawInput.trim();
        if (!trimmed.startsWith(SHARE_PREFIX)) {
            throw new IllegalArgumentException("Outfit share code must start with " + SHARE_PREFIX);
        }
        String jsonText = inflateRaw(decodeBase64(trimmed.substring(SHARE_PREFIX.length())));
        JsonElement el;
        try {
            el = JsonParser.parseString(jsonText);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid outfit JSON payload", e);
        }
        if (el == null || !el.isJsonObject()) {
            throw new IllegalArgumentException("Outfit payload must be a JSON object");
        }
        JsonObject obj = el.getAsJsonObject();
        String name = obj.has("n") && obj.get("n").isJsonPrimitive()
                ? normalizeOutfitName(obj.get("n").getAsString())
                : "Shared Outfit";
        if (!obj.has("c") || !obj.get("c").isJsonObject()) {
            throw new IllegalArgumentException("Outfit code missing cosmetic data");
        }
        CosmeticsLogic.CosmeticLoadout loadout = SestiaChatProtocol.parseCosmeticsV2(obj.getAsJsonObject("c"));
        if (loadout == null) {
            throw new IllegalArgumentException("Failed to decode cosmetic loadout");
        }
        return createEnvelope(name, loadout);
    }

    public static Path saveOutfit(Path dir, String name, CosmeticsLogic.CosmeticLoadout loadout) throws IOException {
        Files.createDirectories(dir);
        OutfitEnvelope env = createEnvelope(name, loadout);
        JsonObject root = new JsonObject();
        root.addProperty("format", env.format());
        root.addProperty("schemaVersion", env.schemaVersion());
        root.addProperty("name", env.name());
        root.addProperty("savedAtEpochMs", env.savedAtEpochMs());
        root.add("cosmetics", SestiaChatProtocol.serializeCosmeticsV2(env.loadout()));
        String slug = ConfigProfileManager.sanitizeFileSlug(env.name());
        Path file = dir.resolve(slug + ".json");
        Files.writeString(file, PRETTY_GSON.toJson(root), StandardCharsets.UTF_8);
        return file;
    }

    public static OutfitEnvelope loadOutfitFile(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
        String name = obj.has("name") && obj.get("name").isJsonPrimitive()
                ? normalizeOutfitName(obj.get("name").getAsString())
                : "Outfit";
        long savedAt = obj.has("savedAtEpochMs") && obj.get("savedAtEpochMs").isJsonPrimitive()
                ? obj.get("savedAtEpochMs").getAsLong()
                : Files.getLastModifiedTime(file).toMillis();
        JsonObject cObj = obj.has("cosmetics") && obj.get("cosmetics").isJsonObject()
                ? obj.getAsJsonObject("cosmetics")
                : (obj.has("c") && obj.get("c").isJsonObject() ? obj.getAsJsonObject("c") : null);
        CosmeticsLogic.CosmeticLoadout loadout = SestiaChatProtocol.parseCosmeticsV2(cObj);
        if (loadout == null) {
            throw new IOException("Invalid outfit file: " + file.getFileName());
        }
        return new OutfitEnvelope(OUTFIT_FORMAT, SCHEMA_VERSION, name, savedAt, loadout.sanitized());
    }

    public static boolean deleteOutfit(Path file) throws IOException {
        return file != null && Files.deleteIfExists(file);
    }

    public static List<OutfitSummary> listOutfits(Path dir) {
        List<OutfitSummary> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(p -> {
                        try {
                            OutfitEnvelope env = loadOutfitFile(p);
                            out.add(new OutfitSummary(
                                    p,
                                    p.getFileName().toString(),
                                    env.name(),
                                    env.savedAtEpochMs(),
                                    env.loadout().activeSlotCount(),
                                    env.loadout()
                            ));
                        } catch (Exception ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
        out.sort(Comparator.comparingLong(OutfitSummary::savedAtEpochMs).reversed()
                .thenComparing(OutfitSummary::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static byte[] deflateRaw(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(32, input.length / 2))) {
            deflater.setInput(input);
            deflater.finish();
            byte[] buf = new byte[512];
            while (!deflater.finished()) {
                int count = deflater.deflate(buf);
                baos.write(buf, 0, count);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to compress outfit share code", e);
        } finally {
            deflater.end();
        }
    }

    private static byte[] decodeBase64(String encodedPart) {
        String encoded = encodedPart != null ? encodedPart.trim() : "";
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException("Empty outfit share code");
        }
        try {
            return Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            try {
                return Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Corrupted outfit share code");
            }
        }
    }

    private static String inflateRaw(byte[] compressed) {
        Inflater inflater = new Inflater(true);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            inflater.setInput(compressed);
            byte[] buf = new byte[1024];
            int total = 0;
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw new IllegalArgumentException("Corrupted compressed outfit code");
                    }
                    break;
                }
                total += count;
                if (total > MAX_PAYLOAD_BYTES) {
                    throw new IllegalArgumentException("Outfit share code exceeds size limit");
                }
                out.write(buf, 0, count);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException | DataFormatException e) {
            throw new IllegalArgumentException("Corrupted compressed outfit code", e);
        } finally {
            inflater.end();
        }
    }
}
