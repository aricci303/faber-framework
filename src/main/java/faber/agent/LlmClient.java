package faber.agent;

import java.io.IOException;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlockParam;

public interface LlmClient {
	
	/**
	 * numInputTokens is only the non-cached portion of a call. Prompt
	 * caching (see systemOfTextBlockParams below) is meant to serve the
	 * bulk of a large, mostly-static system prompt from cache after the
	 * first call — but an earlier version attached cacheControl to the
	 * top-level MessageCreateParams.Builder rather than to the system
	 * prompt's own TextBlockParam, which does not actually mark that
	 * block as the cached prefix. Real symptom this produced: every
	 * single cycle showed a fresh cache creation and zero cache reads,
	 * confirmed across an 11-cycle run — the cache was being rewritten
	 * every time rather than ever actually being reused. Fixed by
	 * attaching cache_control precisely to the system prompt's block.
	 * cacheCreationInputTokens and cacheReadInputTokens are still
	 * included here regardless, since accurate reporting matters
	 * independent of whether caching itself is working correctly.
	 */
	public static record LlmCallResult(String output, long numInputTokens, long numOutputTokens,
			long cacheCreationInputTokens, long cacheReadInputTokens, String stopReason) {
		/** Convenience for callers that have no stop reason (scripted clients, older code). */
		public LlmCallResult(String output, long numInputTokens, long numOutputTokens,
				long cacheCreationInputTokens, long cacheReadInputTokens) {
			this(output, numInputTokens, numOutputTokens, cacheCreationInputTokens, cacheReadInputTokens, null);
		}
		public long totalInputTokens() {
			return numInputTokens + cacheCreationInputTokens + cacheReadInputTokens;
		}
		/** True when the model was cut off at the output length limit (the output is then typically unterminated). */
		public boolean truncated() { return "max_tokens".equals(stopReason); }
	}

	/**
	 * What the retry and diagnostic logic needs to know about one API response, independent of the SDK's types
	 * (so that logic can be tested without the SDK): the text blocks, how many blocks of any kind there were,
	 * why the model stopped, and the token usage.
	 */
	public static record RawResponse(java.util.List<String> texts, int blockCount, String stopReason,
			long inputTokens, long outputTokens, long cacheCreation, long cacheRead, int thinkingBlocks, int thinkingChars) {
		public RawResponse(java.util.List<String> texts, int blockCount, String stopReason,
				long inputTokens, long outputTokens, long cacheCreation, long cacheRead) {
			this(texts, blockCount, stopReason, inputTokens, outputTokens, cacheCreation, cacheRead, 0, 0);
		}
		/** Total characters of text the model returned in text blocks. */
		public int textChars() { int n = 0; for (String t : texts) n += t.length(); return n; }
	}
	
	LlmCallResult plan(String systemPrompt, String context) throws IOException, InterruptedException;

    /** Minimal real implementation against the Anthropic Messages API. Swap naive JSON handling for a real library beyond a sketch. */
    
    final class AnthropicLlmClient implements LlmClient {
        private final Model model;
        private AnthropicClient client;
        
        public AnthropicLlmClient(Model model) {
        	this.model = model;
            client = AnthropicOkHttpClient.fromEnv();
        }

        @Override
        public LlmCallResult plan(String systemPrompt, String context) throws IOException, InterruptedException {

            // System.out.println("--------------------- CONTEXT: \n"+ context+"\n---------------------\n");
            
            com.anthropic.models.messages.MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens())
                .systemOfTextBlockParams(java.util.List.of(
                        TextBlockParam.builder()
                                .text(systemPrompt)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .addUserMessage(context);
            if (thinkingDisabled()) paramsBuilder.thinking(com.anthropic.models.messages.ThinkingConfigDisabled.builder().build());
            com.anthropic.models.messages.MessageCreateParams params = paramsBuilder.build();
                        
            // Transient server overload is retried indefinitely (as before). A response that comes back with NO text
            // block — observed once in a long run, killing it with "Unexpected response shape" — is retried a few
            // times, with the reason logged, before giving up with a message that says why.
            return callWithRetries(() -> {
                Message message = null;
                while (true) {
                    try {
                        message = client.messages().create(params);
                        break;
                    } catch (com.anthropic.errors.InternalServerException ex) {
                        System.err.println("Claude is overloaded... retrying in few sec");
                        try { Thread.sleep(3000 + (int) (Math.random() * 3000)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw new IllegalStateException(ie); }
                    }
                }
                return adapt(message);
            }, MAX_EMPTY_RESPONSE_ATTEMPTS, ms -> { try { Thread.sleep(ms); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); } });
        }        

        static final int MAX_EMPTY_RESPONSE_ATTEMPTS = 3;

        /** The only SDK-specific step: reduce a Message to plain values. */
        public static RawResponse adapt(Message message) {
            var u = message.usage();
            java.util.List<String> texts = new java.util.ArrayList<>();
            int thinkingBlocks = 0, thinkingChars = 0;
            for (var block : message.content()) {
                if (block.text().isPresent()) texts.add(block.text().get().text());
                else if (block.thinking().isPresent()) { thinkingBlocks++; thinkingChars += block.thinking().get().thinking().length(); }
                else if (block.redactedThinking().isPresent()) thinkingBlocks++;
            }
            String stop = message.stopReason().map(com.anthropic.models.messages.StopReason::asString).orElse("unknown");
            return new RawResponse(texts, message.content().size(), stop, u.inputTokens(), u.outputTokens(),
                    u.cacheCreationInputTokens().orElse(0L), u.cacheReadInputTokens().orElse(0L), thinkingBlocks, thinkingChars);
        }

        /** Max output tokens per call: -Dfaber.llm.maxTokens=N (default 8192). */
        public static long maxTokens() { return Long.getLong("faber.llm.maxTokens", 8192L); }

        /** -Dfaber.llm.thinking=disabled turns model "thinking" off explicitly; anything else leaves the model's own default. */
        public static boolean thinkingDisabled() { return "disabled".equalsIgnoreCase(System.getProperty("faber.llm.thinking", "default")); }

        public static String settingsSummary() { return "maxTokens=" + maxTokens() + ", thinking=" + (thinkingDisabled() ? "disabled (explicit)" : "model default"); }

        /**
         * Calls until a response carries a text block, up to maxAttempts times (waiting 2s, 4s, ... between attempts);
         * each empty response is logged with its stop reason, block count and output tokens. After the last one it
         * throws, with the same details in the message — a failure that explains itself.
         */
        public static LlmCallResult callWithRetries(java.util.function.Supplier<RawResponse> call, int maxAttempts,
                java.util.function.LongConsumer sleepMillis) throws IOException {
            RawResponse last = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                last = call.get();
                if (last.thinkingBlocks() > 0)
                    System.err.println("[LLM] output " + last.outputTokens() + " tokens: visible text " + last.textChars() + " chars; hidden thinking "
                            + last.thinkingBlocks() + " block(s), " + last.thinkingChars() + " chars (thinking counts as output tokens)");
                if (!last.texts().isEmpty())
                    return new LlmCallResult(last.texts().get(0), last.inputTokens(), last.outputTokens(),
                            last.cacheCreation(), last.cacheRead(), last.stopReason());
                System.err.println("[LLM] response without a text block (stop_reason=" + last.stopReason() + ", content blocks="
                        + last.blockCount() + ", thinking blocks=" + last.thinkingBlocks() + ", output_tokens=" + last.outputTokens() + ") — attempt " + attempt + "/" + maxAttempts
                        + (attempt < maxAttempts ? ", retrying" : ", giving up"));
                if (attempt < maxAttempts) sleepMillis.accept(2000L * attempt);
            }
            throw new IOException("Unexpected response shape: no text block after " + maxAttempts + " attempts (last stop_reason="
                    + last.stopReason() + ", content blocks=" + last.blockCount() + ", output_tokens=" + last.outputTokens() + ")");
        }
    }
}
