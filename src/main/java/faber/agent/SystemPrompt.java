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
 *
 * The text is ONE core for every model. A model that needs something said that the core does not say gets an
 * overlay, named by its LlmProfile and composed here (see compose): the difference between two agents' prompts is
 * then exactly the overlay, which is short, versioned and recorded.
 */
public class SystemPrompt {

	static final String RESOURCE = "system-prompt.md";

	/** The id of the prompt as it is: the core text, no overlay. A compile-time constant, so that naming it loads nothing. */
	public static final String CORE = "core";

	private static final String CORE_TEXT = load(RESOURCE);

	/** The manuals of the four artifacts every agent is spawned with, generated from the artifact classes. */
	private static final String MANUALS = faber.environment.WorkspaceArtifact.manual().toJson() + "\n"
		+ faber.environment.UserConsoleArtifact.manual().toJson() + "\n"
		+ faber.environment.AlarmArtifact.manual().toJson() + "\n"
		+ faber.environment.NotebookArtifact.manual().toJson();

	/** The prompt as it has always been: the core text, then the manuals. Byte for byte what a profile with no overlay composes. */
	public static final String systemPrompt = CORE_TEXT + "\n" + MANUALS;

	private static final java.util.Map<String, String> composed = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * The prompt of a profile: the core text, an OVERLAY if one is named, then the manuals. An overlay is a short, additive text for a particular model — what that model needs said that the core does not say —
	 * kept apart from the core so that the core stays one text for every model and the difference between two prompts is exactly the overlay. It is found as the resource faber/agent/prompt-overlays/&lt;id&gt;.md,
	 * or as &lt;id&gt;.md in the directory named by -Dfaber.prompt.overlays=DIR (for an experiment, without a rebuild). An overlay that cannot be found, or is empty, fails loudly, here and not in the middle of a run.
	 */
	public static String compose(String overlay) {
		if (overlay == null || overlay.isBlank() || CORE.equals(overlay.trim())) return systemPrompt;
		return composed.computeIfAbsent(overlay.trim(), id -> CORE_TEXT + "\n" + overlayText(id) + "\n" + MANUALS);
	}

	/** What a record says about the prompt: "core", or the overlay's id and the first 12 hex digits of its hash. */
	public static String identity(String overlay) {
		if (overlay == null || overlay.isBlank() || CORE.equals(overlay.trim())) return CORE;
		return overlay.trim() + "@" + sha12(overlayText(overlay.trim()));
	}

	static String overlayText(String id) {
		if (!id.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("a prompt overlay is named by letters, digits, dots, dashes and underscores (got '" + id + "')");
		String text = null; String where = "faber/agent/prompt-overlays/" + id + ".md";
		try (InputStream in = SystemPrompt.class.getResourceAsStream("prompt-overlays/" + id + ".md")) {
			if (in != null) text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) { throw new IllegalStateException("could not read the prompt overlay " + where, e); }
		String dir = System.getProperty("faber.prompt.overlays");
		if (text == null && dir != null && !dir.isBlank()) {
			java.nio.file.Path f = java.nio.file.Path.of(dir, id + ".md");
			where += " or " + f;
			try { if (java.nio.file.Files.isRegularFile(f)) text = java.nio.file.Files.readString(f, StandardCharsets.UTF_8); } catch (IOException e) { throw new IllegalStateException("could not read the prompt overlay " + f, e); }
		}
		if (text == null) throw new IllegalArgumentException("no prompt overlay '" + id + "': looked for " + where + (dir == null ? " (a directory of overlays can be named with -Dfaber.prompt.overlays=DIR)" : ""));
		if (text.isBlank()) throw new IllegalStateException("the prompt overlay '" + id + "' is empty");
		return text.stripTrailing();
	}

	static String sha12(String text) {
		try {
			byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(); for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i]));
			return sb.toString();
		} catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}

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
