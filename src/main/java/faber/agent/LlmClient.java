package faber.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;

public interface LlmClient {
	
	/**
	 * numInputTokens is only the non-cached portion of a call — with
	 * prompt caching enabled (see cacheControl below), the bulk of a
	 * large, mostly-static system prompt is typically served from
	 * cache after the first call, and Anthropic's own Usage object
	 * reports that separately from plain input tokens. Reporting only
	 * numInputTokens understated real cost substantially in practice
	 * (observed: per-cycle counts near 1, while the Claude console's
	 * own dashboard showed ~103k real input tokens across an 11-cycle
	 * run) — cacheCreationInputTokens and cacheReadInputTokens are
	 * included for a complete, honest total.
	 */
	public static record LlmCallResult(String output, long numInputTokens, long numOutputTokens,
			long cacheCreationInputTokens, long cacheReadInputTokens) {
		public long totalInputTokens() {
			return numInputTokens + cacheCreationInputTokens + cacheReadInputTokens;
		}
	}
	
	LlmCallResult plan(String systemPrompt, String context) throws IOException, InterruptedException;

    /** Minimal real implementation against the Anthropic Messages API. Swap naive JSON handling for a real library beyond a sketch. */
    
    final class AnthropicLlmClient implements LlmClient {
        private final Model model;
        
        public AnthropicLlmClient(Model model) {
        	this.model = model;
        }

        @Override
        public LlmCallResult plan(String systemPrompt, String context) throws IOException, InterruptedException {
            var client = AnthropicOkHttpClient.fromEnv();

            // System.out.println("--------------------- CONTEXT: \n"+ context+"\n---------------------\n");
            
            com.anthropic.models.messages.MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .cacheControl(CacheControlEphemeral.builder().build())
                .maxTokens(8192)
                .system(systemPrompt)
                .addUserMessage(context)
                .build();            
                        
            Message message = null;            
            
            while (true) {
            	try {
            		message = client.messages().create(params);
            		break;
            	} catch (com.anthropic.errors.InternalServerException ex) {
            		System.err.println("Claude is overloaded... retrying in few sec");
            		Thread.sleep(3000 + (int)(Math.random()*3000));
            	}
            }
            var u = message.usage();
            for (var block : message.content()) {
                if (block.text().isPresent()) {
                	var text = block.text().get().text();
                    // System.out.println("--------------------- OUTPUT FROM THE MODEL: \n" + text + "\n---------------------\n");
                	long cacheCreation = u.cacheCreationInputTokens().orElse(0L);
                	long cacheRead = u.cacheReadInputTokens().orElse(0L);
                	return new LlmCallResult(text, u.inputTokens(), u.outputTokens(), cacheCreation, cacheRead); 
                }
            }
            throw new IOException("Unexpected response shape");
        }        
    }
}
