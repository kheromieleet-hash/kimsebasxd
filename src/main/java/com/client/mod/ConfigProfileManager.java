package com.client.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Manages saving, loading, exporting, and importing Sestia Client configuration profiles
 * (active modules, slider/mode settings, keybinds, macros, and HUD layout).
 */
public final class ConfigProfileManager {
    public static final String SHARE_PREFIX = "SESTIA1:";
    public static final String PROFILE_FORMAT = "sestia-profile";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_SHARE_PAYLOAD_BYTES = 1024 * 1024;
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson COMPACT_GSON = new GsonBuilder().create();

    public static final class InventorySnapshot {
        public boolean active = true;
        public boolean armor = true;
        public boolean potions = true;
        public boolean mini = false;
        public boolean vert = false;
        public int bgAlpha = 60;

        public InventorySnapshot() {
        }

        public static InventorySnapshot fromBridge(ModernModuleSettings.InventoryBridge inv) {
            if (inv == null) {
                return null;
            }
            InventorySnapshot snap = new InventorySnapshot();
            snap.active = inv.isActive();
            snap.armor = inv.isArmor();
            snap.potions = inv.isPotions();
            snap.mini = inv.isMini();
            snap.vert = inv.isVert();
            snap.bgAlpha = Math.max(0, Math.min(100, inv.getBgAlpha()));
            return snap;
        }

        public void applyTo(ModernModuleSettings.InventoryBridge inv) {
            if (inv == null) {
                return;
            }
            inv.setActive(this.active);
            inv.setArmor(this.armor);
            inv.setPotions(this.potions);
            inv.setMini(this.mini);
            inv.setVert(this.vert);
            inv.setBgAlpha(Math.max(0, Math.min(100, this.bgAlpha)));
        }
    }

    public static final class ProfileEnvelope {
        public String format = PROFILE_FORMAT;
        public int schemaVersion = SCHEMA_VERSION;
        public String name = "Profile";
        public long savedAtEpochMs = 0L;
        public int activeCount = 0;
        public ModConfig config;
        public InventorySnapshot inventoryHud;
    }

    public record ProfileSummary(
            Path file,
            String fileName,
            String name,
            long savedAtEpochMs,
            int activeCount,
            int bindCount,
            int macroCount
    ) {}

    private ConfigProfileManager() {
    }

    public static Path getDefaultProfilesDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("sestia").resolve("profiles");
    }

    public static String normalizeProfileName(String rawName) {
        if (rawName == null) {
            return "Profile";
        }
        String trimmed = rawName.trim();
        return trimmed.isEmpty() ? "Profile" : trimmed;
    }

    public static String sanitizeFileSlug(String rawName) {
        String base = normalizeProfileName(rawName).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        boolean prevDash = false;
        for (int i = 0; i < base.length() && sb.length() < 40; i++) {
            char ch = base.charAt(i);
            boolean alnum = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
            if (alnum || ch == '_') {
                sb.append(ch);
                prevDash = false;
            } else if (!prevDash && sb.length() > 0) {
                sb.append('-');
                prevDash = true;
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-') {
            sb.setLength(sb.length() - 1);
        }
        return sb.length() == 0 ? "profile" : sb.toString();
    }

    public static ProfileEnvelope createEnvelope(
            String profileName,
            ModConfig source,
            ModernModuleSettings.InventoryBridge inv,
            int activeCount
    ) {
        ModConfig copy = source != null ? source.copy() : new ModConfig();
        copy.sanitize();
        ProfileEnvelope env = new ProfileEnvelope();
        env.format = PROFILE_FORMAT;
        env.schemaVersion = SCHEMA_VERSION;
        env.name = normalizeProfileName(profileName);
        env.savedAtEpochMs = System.currentTimeMillis();
        env.activeCount = Math.max(0, activeCount);
        env.config = copy;
        env.inventoryHud = InventorySnapshot.fromBridge(inv);
        return env;
    }

    public static String exportJson(
            String profileName,
            ModConfig source,
            ModernModuleSettings.InventoryBridge inv,
            int activeCount
    ) {
        return PRETTY_GSON.toJson(createEnvelope(profileName, source, inv, activeCount));
    }

    public static String exportShareCode(
            String profileName,
            ModConfig source,
            ModernModuleSettings.InventoryBridge inv,
            int activeCount
    ) {
        String json = COMPACT_GSON.toJson(createEnvelope(profileName, source, inv, activeCount));
        return encodeShareCode(json);
    }

    public static String exportShareCode(ProfileEnvelope envelope) {
        if (envelope == null || envelope.config == null) {
            throw new IllegalArgumentException("Profile has no config data");
        }
        return encodeShareCode(COMPACT_GSON.toJson(envelope));
    }

    private static String encodeShareCode(String compactJson) {
        byte[] utf8 = compactJson.getBytes(StandardCharsets.UTF_8);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(utf8);
            gzip.finish();
            return SHARE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode profile share code", e);
        }
    }

    /**
     * Parses a strict share-code payload ({@code SESTIA1:...}) into a sanitized profile envelope.
     */
    public static ProfileEnvelope parseShareCode(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            throw new IllegalArgumentException("Empty share code");
        }
        String trimmed = rawInput.trim();
        if (!trimmed.startsWith(SHARE_PREFIX)) {
            throw new IllegalArgumentException("Share code must start with " + SHARE_PREFIX);
        }
        return parseJsonEnvelope(decodeShareCode(trimmed));
    }

    public static ProfileEnvelope parseImportPayload(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            throw new IllegalArgumentException("Empty profile input");
        }
        String trimmed = rawInput.trim();
        String jsonText = trimmed.startsWith(SHARE_PREFIX) ? decodeShareCode(trimmed) : trimmed;
        return parseJsonEnvelope(jsonText);
    }

    private static ProfileEnvelope parseJsonEnvelope(String jsonText) {
        JsonElement rootEl;
        try {
            rootEl = JsonParser.parseString(jsonText);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON profile format", e);
        }
        if (rootEl == null || !rootEl.isJsonObject()) {
            throw new IllegalArgumentException("Profile payload must be a JSON object");
        }

        JsonObject obj = rootEl.getAsJsonObject();
        ProfileEnvelope envelope;
        if (obj.has("config") && obj.get("config").isJsonObject()) {
            envelope = COMPACT_GSON.fromJson(obj, ProfileEnvelope.class);
        } else {
            // Also allow direct ModConfig JSON (e.g. shared customclient.json)
            ModConfig bareConfig = COMPACT_GSON.fromJson(obj, ModConfig.class);
            if (bareConfig == null) {
                throw new IllegalArgumentException("Missing config fields in JSON");
            }
            envelope = new ProfileEnvelope();
            envelope.name = "Imported Config";
            envelope.savedAtEpochMs = System.currentTimeMillis();
            envelope.config = bareConfig;
        }

        if (envelope == null || envelope.config == null) {
            throw new IllegalArgumentException("Profile contains no configuration");
        }
        envelope.name = normalizeProfileName(envelope.name);
        envelope.config.sanitize();
        return envelope;
    }

    private static String decodeShareCode(String shareCode) {
        String encoded = shareCode.substring(SHARE_PREFIX.length()).trim();
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException("Empty share code");
        }
        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            // Accept standard Base64 as fallback if user pasted from an external tool
            try {
                compressed = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Corrupted profile share code");
            }
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[2048];
            int total = 0;
            int read;
            while ((read = gzip.read(buf)) != -1) {
                total += read;
                if (total > MAX_SHARE_PAYLOAD_BYTES) {
                    throw new IllegalArgumentException("Profile payload exceeds size limit");
                }
                out.write(buf, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Corrupted compressed profile code", e);
        }
    }

    public static void applyProfile(
            ProfileEnvelope envelope,
            ModConfig target,
            ModernModuleSettings.InventoryBridge inv,
            Runnable afterApply
    ) {
        if (envelope == null || envelope.config == null || target == null) {
            throw new IllegalArgumentException("Profile or target config is null");
        }
        String preservedChatUrl = target.clientChatUrl;
        target.copyFrom(envelope.config);
        if ((target.clientChatUrl == null || target.clientChatUrl.isEmpty()) && preservedChatUrl != null && !preservedChatUrl.isEmpty()) {
            target.clientChatUrl = preservedChatUrl;
        }
        target.sanitize();
        if (envelope.inventoryHud != null) {
            envelope.inventoryHud.applyTo(inv);
        }
        if (afterApply != null) {
            afterApply.run();
        }
    }

    public static Path saveProfile(
            Path dir,
            String profileName,
            ModConfig source,
            ModernModuleSettings.InventoryBridge inv,
            int activeCount
    ) throws IOException {
        ProfileEnvelope env = createEnvelope(profileName, source, inv, activeCount);
        return saveEnvelope(dir, env);
    }

    public static Path saveEnvelope(Path dir, ProfileEnvelope envelope) throws IOException {
        Files.createDirectories(dir);
        String slug = sanitizeFileSlug(envelope.name);
        Path target = dir.resolve(slug + ".json");
        Files.writeString(target, PRETTY_GSON.toJson(envelope), StandardCharsets.UTF_8);
        return target;
    }

    public static ProfileEnvelope loadProfileFile(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return parseImportPayload(content);
    }

    public static boolean deleteProfile(Path file) throws IOException {
        return file != null && Files.deleteIfExists(file);
    }

    public static List<ProfileSummary> listProfiles(Path dir) {
        List<ProfileSummary> result = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return result;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .forEach(p -> {
                        try {
                            ProfileEnvelope env = loadProfileFile(p);
                            long ts = env.savedAtEpochMs > 0 ? env.savedAtEpochMs : Files.getLastModifiedTime(p).toMillis();
                            int binds = env.config.moduleKeybinds != null ? env.config.moduleKeybinds.size() : 0;
                            int macros = env.config.macros != null ? env.config.macros.size() : 0;
                            result.add(new ProfileSummary(
                                    p,
                                    p.getFileName().toString(),
                                    env.name,
                                    ts,
                                    env.activeCount,
                                    binds,
                                    macros
                            ));
                        } catch (Exception ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
        result.sort(Comparator.comparingLong(ProfileSummary::savedAtEpochMs).reversed()
                .thenComparing(ProfileSummary::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public static boolean openProfilesFolder(Path dir) {
        try {
            Files.createDirectories(dir);
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String pathStr = dir.toAbsolutePath().toString();
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", pathStr).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", pathStr).start();
            } else {
                new ProcessBuilder("xdg-open", pathStr).start();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
