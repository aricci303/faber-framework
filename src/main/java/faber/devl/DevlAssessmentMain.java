package faber.devl;

import java.util.LinkedHashSet;

import faber.devl.DevlRunner.Options;

/**
 * THE ASSESSMENT TOOL. Makes FRESH instances of the agent from a playbook and measures what they do: A1 (a clean queue, the world of learning) and A2a (a queue in the changed world, audit flags only),
 * with the model published at version 2 in step with the change, or left behind (--model=stale: the drift probe). The playbook is frozen while the instance is assessed. With --consolidate, one instance then
 * revises the playbook and the run writes a release candidate (devl-playbook-<stamp>-c<round>.json) with its lineage and an advisory gate.
 *
 *   faber.devl.DevlAssessmentMain --project=<folder> --playbook=FILE [--only=a1|a2a|a1,a2a] [--model=in-step|stale] [--consolidate] [--max-cycles=90] [--log-dir=devl-logs] [--watchdog-seconds=90] [--llm-model=ID]
 *
 * --llm-model  the Anthropic model of the agent (default: the one the architecture asks for; any model id the API knows, e.g. claude-haiku-5-5); recorded in the log and in every snapshot. -Dfaber.llm.model=ID does the same.
 * --project    the project's folder in resources/faber/devl/projects (e.g. expense-approval); a playbook learned for another project is refused
 * --playbook   the playbook snapshot (devl-playbook-<stamp>.json, from DevlLearningMain or from a consolidation) the instance is made from
 */
public final class DevlAssessmentMain {
    private DevlAssessmentMain() {}
    static final String USAGE = "DevlAssessmentMain --project=<folder> --playbook=FILE [--only=a1|a2a] [--model=in-step|stale] [--consolidate] [--max-cycles=N] [--log-dir=DIR] [--watchdog-seconds=N] [--llm-model=ID]";

    public static Options parse(String[] args) {
        Options o = new Options(); o.phase = "assessment";
        for (String a : args) {
            if (DevlRunner.commonArgument(o, a)) continue;
            if (a.startsWith("--playbook=")) o.playbook = a.substring(11);
            else if (a.startsWith("--model=")) o.model = DevlRunner.modelOf(a.substring(8));
            else if (a.equals("--consolidate")) o.consolidate = true;
            else if (a.startsWith("--only=")) {
                o.only = new LinkedHashSet<>();
                for (String t : a.substring(7).toLowerCase().split("[,\\s]+")) {
                    if (t.isBlank()) continue;
                    if (!t.equals("a1") && !t.equals("a2a")) throw new IllegalArgumentException("unknown assessment '" + t + "' in --only (known: a1, a2a)");
                    o.only.add(t);
                }
                if (o.only.isEmpty()) throw new IllegalArgumentException("--only names no assessment (known: a1, a2a)");
            }
            else if (a.startsWith("--tutor=")) throw new IllegalArgumentException("--tutor applies to DevlLearningMain: there is no tutor in an assessment");
            else throw DevlRunner.unknownArgument("DevlAssessmentMain", a);
        }
        DevlRunner.requireProject(o, USAGE);
        if (o.playbook == null) throw new IllegalArgumentException("DevlAssessmentMain needs --playbook=FILE: a fresh instance is made from a playbook\nusage: " + USAGE);
        return o;
    }

    public static void main(String[] args) throws Exception { DevlRunner.launch("DevlAssessmentMain", USAGE, DevlAssessmentMain::parse, args); }
}
