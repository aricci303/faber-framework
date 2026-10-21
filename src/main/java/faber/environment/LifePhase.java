package faber.environment;

/**
 * The coarse-grained phase of an agent's life:
 *   LEARNING    — being prepared for a job in a safe, tutor-supported learning environment (the first phase of developmental learning);
 *   ASSESSMENT  — being measured on what it can now do, on cases it has not seen, without help; simulated, nothing
 *                 real is affected, and the agent is told so truthfully (the analogue of testing and validation);
 *   OPERATION   — doing the real job in the real environment.
 * A workspace fact, not a prompt fact: the system prompt is static and cached, so it can explain what each phase
 * means in general but cannot say which one is current — that belongs in harness-guaranteed state the model is
 * shown every cycle. Unset (null) means "not specified" and renders nothing, so every pre-existing scenario
 * behaves exactly as before.
 *
 * The names pair with what is done in each: an agent is prepared (LEARNING), validated (ASSESSMENT) and then put to work (OPERATION); the first
 * and third were called "development" and "deployment" in the first materials versions, and the third is now "operation" so that none of the three
 * names borrows from the vocabulary of building software.
 */
public enum LifePhase {
    LEARNING, ASSESSMENT, OPERATION;

    public String toJsonValue() { return name().toLowerCase(); }
}
