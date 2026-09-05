package faber.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;

public interface LlmClient {
    String plan(String systemPrompt, String context) throws IOException, InterruptedException;

    /** Minimal real implementation against the Anthropic Messages API. Swap naive JSON handling for a real library beyond a sketch. */
    final class AnthropicLlmClient implements LlmClient {
        private final HttpClient http;
        private final Model model;
        
        public AnthropicLlmClient(Model model) {
        	this.model = model;
            http = HttpClient.newHttpClient();
        }

        @Override
        public String plan(String systemPrompt, String context) throws IOException, InterruptedException {
            //String escapedSystem = escape(systemPrompt);
            //String escapedUser = escape(context);
            
            var client = AnthropicOkHttpClient.fromEnv();
            			// AnthropicOkHttpClient.builder().apiKey(apiKey).build();

            System.out.println("--------------------- CONTEXT: \n"+ context+"\n---------------------\n");
            
            com.anthropic.models.messages.MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
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
            		Thread.sleep(3000 + ((int)Math.random()*3000));
            	}
            }
            	
            for (var block : message.content()) {
                if (block.text().isPresent()) {
                	var text = block.text().get().text();
                    System.out.println("--------------------- OUTPUT FROM THE MODEL: \n" + text + "\n---------------------\n");
                	return text; 
                }
            }
            throw new IOException("Unexpected response shape");
            
            /*
            int textStart = json.indexOf("\"text\":\"") + 8;
            int textEnd = json.indexOf("\"", textStart);
            if (textStart < 8 || textEnd < 0) {
                throw new IOException("Unexpected response shape: " + json);
            }
            return unescape(json.substring(textStart, textEnd));
			*/
        
        
        }

        /*
        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        }

        private static String unescape(String s) {
            return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
        }
        */
    }
}
