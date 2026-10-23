package faber.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONObject;

/**
 * The catalog of profiles (resource faber/agent/llm-profiles.json) and how a launch picks one.
 *
 *   {"default": "sonnet-5", "profiles": {"sonnet-5": {"provider": "anthropic", "model": "claude-sonnet-5", "maxTokens": 8192, "maxTokensCap": 32000, "thinking": "default", "formatAttempts": 3, "prompt": "core"}, ...}}
 *
 * A profile is chosen by name (a tool's --llm-profile, or -Dfaber.llm.profile; otherwise the catalog's default), then adjusted, in this order of precedence: what the launch names explicitly (a tool's --llm-model,
 * --prompt-overlay), then the process-wide properties (-Dfaber.llm.model, .maxTokens, .maxTokensCap, .thinking, and -Dfaber.prompt.overlay), then what the catalog says. The default profile with no property set
 * is the behaviour the framework has always had: Sonnet 5, 8192 output tokens raised up to 32000, the model's own thinking, three attempts, the core prompt.
 */
public final class LlmProfiles {
    private LlmProfiles() {}
    static final String RESOURCE = "llm-profiles.json";

    private static Map<String, LlmProfile> catalog; private static String defaultName;

    private static synchronized void load() {
        if (catalog != null) return;
        try (InputStream in = LlmProfiles.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("profile catalog not found: faber/agent/" + RESOURCE + " (is src/main/resources on the classpath?)");
            JSONObject j = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            Map<String, LlmProfile> m = new LinkedHashMap<>(); JSONObject ps = j.getJSONObject("profiles");
            for (String name : ps.keySet()) {
                JSONObject p = ps.getJSONObject(name);
                m.put(name, new LlmProfile(name, p.optString("provider", "anthropic"), p.getString("model"), p.optLong("maxTokens", 8192L), p.optLong("maxTokensCap", 32000L),
                        "disabled".equalsIgnoreCase(p.optString("thinking", "default")), p.optInt("formatAttempts", 3), p.optString("prompt", SystemPrompt.CORE)));
            }
            String d = j.getString("default");
            if (!m.containsKey(d)) throw new IllegalStateException("profile catalog: the default '" + d + "' is not one of its profiles " + m.keySet());
            catalog = m; defaultName = d;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    public static synchronized java.util.Set<String> names() { load(); return new java.util.TreeSet<>(catalog.keySet()); }
    public static synchronized String defaultName() { load(); return defaultName; }

    /** A profile of the catalog by name, as the catalog gives it (no overrides). */
    public static synchronized LlmProfile named(String name) {
        load(); LlmProfile p = catalog.get(name);
        if (p == null) throw new IllegalArgumentException("no profile '" + name + "' in the catalog (faber/agent/" + RESOURCE + "); the profiles are " + new java.util.TreeSet<>(catalog.keySet()));
        return p;
    }

    private static String nonBlank(String... xs) { for (String x : xs) if (x != null && !x.isBlank()) return x.trim(); return null; }

    /**
     * The profile of a launch: the named one (or -Dfaber.llm.profile, or the catalog's default), with the model, the prompt overlay and the call settings adjusted as described above.
     * A model named on its own (no profile) makes an ad hoc profile, named for it, with everything else from the default; its name says so.
     */
    public static LlmProfile resolve(String profileName, String modelId, String promptOverlay) {
        String chosen = nonBlank(profileName, System.getProperty("faber.llm.profile"));
        LlmProfile p = named(chosen != null ? chosen : defaultName());
        String model = nonBlank(modelId, System.getProperty("faber.llm.model"));
        if (model != null && !model.equals(p.modelId)) p = p.withModel(model).withName(chosen != null ? p.name + "+model=" + model : "ad hoc (" + model + ")");
        String overlay = nonBlank(promptOverlay, System.getProperty("faber.prompt.overlay"));
        if (overlay != null && !overlay.equals(p.prompt)) p = p.withPrompt(overlay).withName(p.name + "+prompt=" + overlay);
        long mt = Long.getLong("faber.llm.maxTokens", p.maxTokens), cap = Long.getLong("faber.llm.maxTokensCap", p.maxTokensCap);
        boolean noThink = System.getProperty("faber.llm.thinking") != null ? "disabled".equalsIgnoreCase(System.getProperty("faber.llm.thinking")) : p.thinkingDisabled;
        if (mt != p.maxTokens || cap != p.maxTokensCap || noThink != p.thinkingDisabled) p = p.withLimits(mt, cap, noThink);
        return p;
    }

    /** What a program gets when it names nothing: the catalog's default, adjusted by the process-wide properties. */
    public static LlmProfile defaultProfile() { return resolve(null, null, null); }
}
