package faber.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The agent's system prompt.
 *
 * The prompt text lives in the resource {@code faber/agent/system-prompt.md} (plain Markdown, no Java
 * escaping), not in a string literal: a Java string constant is limited to 65,535 bytes in the class
 * file, and the prompt outgrew that. The text is loaded once, at class initialisation.
 *
 * What follows the text is not static: the manuals of the four artifacts every agent is spawned with
 * are generated from the artifact classes, so they cannot drift from the operations that actually exist.
 */
public class SystemPrompt {

	static final String RESOURCE = "system-prompt.md";

	public static final String systemPrompt = load(RESOURCE) + "\n"
		+ faber.environment.WorkspaceArtifact.manual().toJson() + "\n"
		+ faber.environment.UserConsoleArtifact.manual().toJson() + "\n"
		+ faber.environment.AlarmArtifact.manual().toJson() + "\n"
		+ faber.environment.NotebookArtifact.manual().toJson();

	/** Fails loudly: an agent running with an empty or missing prompt would misbehave in ways that are hard to trace. */
	static String load(String name) {
		try (InputStream in = SystemPrompt.class.getResourceAsStream(name)) {
			if (in == null)
				throw new IllegalStateException("system prompt resource not found: " + SystemPrompt.class.getPackageName().replace('.', '/')
						+ "/" + name + " (is src/main/resources on the classpath?)");
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			if (text.isBlank())
				throw new IllegalStateException("system prompt resource is empty: " + name);
			return text;
		} catch (IOException e) {
			throw new IllegalStateException("could not read system prompt resource: " + name, e);
		}
	}
}
