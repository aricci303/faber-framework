package faber.agent;

/**
 * Everything about HOW an agent talks to its language model, as one immutable value: which provider and model, how much it may write per call and how far that may be raised when a call returns no text,
 * whether its hidden thinking is on, how many times an unreadable reply is retried, and which system prompt it reads (the core, or the core with a named overlay).
 *
 * The agent and its architecture know a profile and nothing of any model: a different model, or a different prompt for it, is a different profile, and two agents of one run may have different ones.
 * Profiles come from the catalog (faber/agent/llm-profiles.json, see LlmProfiles); the default profile is today's behaviour exactly. What a run records about its agent is the profile's name, the model and the prompt.
 */
public final class LlmProfile {
    public final String name, provider, modelId, prompt;
    public final long maxTokens, maxTokensCap;
    public final boolean thinkingDisabled;
    /** How many times the architecture asks the model again when a reply cannot be read (with a note that says what was wrong). */
    public final int formatAttempts;

    public LlmProfile(String name, String provider, String modelId, long maxTokens, long maxTokensCap, boolean thinkingDisabled, int formatAttempts, String prompt) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("a profile needs a name");
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("profile '" + name + "': no provider");
        if (modelId == null || modelId.isBlank()) throw new IllegalArgumentException("profile '" + name + "': no model id");
        if (maxTokens < 1) throw new IllegalArgumentException("profile '" + name + "': maxTokens must be at least 1 (got " + maxTokens + ")");
        if (formatAttempts < 1 || formatAttempts > 20) throw new IllegalArgumentException("profile '" + name + "': formatAttempts must be between 1 and 20 (got " + formatAttempts + ")");
        this.name = name; this.provider = provider; this.modelId = modelId; this.maxTokens = maxTokens; this.maxTokensCap = maxTokensCap; this.thinkingDisabled = thinkingDisabled; this.formatAttempts = formatAttempts;
        this.prompt = prompt == null || prompt.isBlank() ? SystemPrompt.CORE : prompt.trim();
    }

    public LlmProfile withName(String n) { return new LlmProfile(n, provider, modelId, maxTokens, maxTokensCap, thinkingDisabled, formatAttempts, prompt); }
    public LlmProfile withModel(String id) { return new LlmProfile(name, provider, id, maxTokens, maxTokensCap, thinkingDisabled, formatAttempts, prompt); }
    public LlmProfile withPrompt(String overlay) { return new LlmProfile(name, provider, modelId, maxTokens, maxTokensCap, thinkingDisabled, formatAttempts, overlay); }
    public LlmProfile withLimits(long maxTokens, long cap, boolean thinkingDisabled) { return new LlmProfile(name, provider, modelId, maxTokens, cap, thinkingDisabled, formatAttempts, prompt); }

    /** The client of this profile's provider (LlmClients). */
    public LlmClient newClient() { return LlmClients.create(this); }

    /** The system prompt this profile's agent reads: the core, or the core with the overlay, then the generated manuals. */
    public String systemPrompt() { return SystemPrompt.compose(prompt); }

    /** The prompt's identity for a record: "core", or the overlay's id and its hash. */
    public String promptId() { return SystemPrompt.identity(prompt); }

    /** The call settings, in the words the run's header has always used. */
    public String describeSettings() {
        return "maxTokens=" + maxTokens + (maxTokensCap > maxTokens ? " (raised, up to " + maxTokensCap + ", for a call that returns no text)" : "") + ", thinking=" + (thinkingDisabled ? "disabled (explicit)" : "model default");
    }

    @Override public String toString() { return name + " [" + provider + " " + modelId + ", prompt " + promptId() + ", " + describeSettings() + ", " + formatAttempts + " attempts per unreadable reply]"; }
}
