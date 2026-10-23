package faber.agent;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * The providers an LlmProfile can name, each a way to make the client of a profile. "anthropic" is registered here; another provider (an OpenAI-compatible endpoint, a local model) is one more registration,
 * and nothing in the agent or its architecture changes. A registry and not a switch, so that a test (or an experiment) can register a provider of its own.
 */
public final class LlmClients {
    private LlmClients() {}
    private static final Map<String, Function<LlmProfile, LlmClient>> providers = new TreeMap<>();
    static { providers.put("anthropic", LlmClient.AnthropicLlmClient::new); }

    public static synchronized void register(String provider, Function<LlmProfile, LlmClient> factory) { providers.put(provider, factory); }

    public static synchronized LlmClient create(LlmProfile profile) {
        Function<LlmProfile, LlmClient> f = providers.get(profile.provider);
        if (f == null) throw new IllegalArgumentException("profile '" + profile.name + "': no client for the provider '" + profile.provider + "' (known: " + providers.keySet() + ")");
        return f.apply(profile);
    }

    public static synchronized java.util.Set<String> providers() { return new java.util.TreeSet<>(providers.keySet()); }
}
