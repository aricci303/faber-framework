package faber.devl;

import java.util.List;

/**
 * The pilot's hand-written queues, one per stage. Hand-written rather than random so that every
 * rule, boundary and interaction is exercised on purpose, and so a stage's difficulty is a design
 * decision. Stage 1 teaches the system on trivial claims; stage 2 covers every rule; stage 3 uses
 * new claims on the boundaries of those rules; the assessments follow: A1, clean claims under the learned policy, and A2a, a queue
 * under the changed policy.
 */
public final class ExpenseCases {
    private ExpenseCases() {}

    static ExpenseCase c(String id, String emp, String cat, int amt, boolean receipt, String date, String desc, String dupOf, Object... kv) {
        return new ExpenseCase(id, emp, cat, amt, receipt, date, desc, dupOf, kv);
    }

    public static List<ExpenseCase> stage1() { return List.of(
        c("E101", "Ines",  "meals", 18, true,  "2026-09-01", "team coffee", null, "attendees", 1),
        c("E102", "Tomas", "meals", 32, true,  "2026-09-01", "working lunch", null, "attendees", 1),
        c("E103", "Ines",  "meals", 12, false, "2026-09-02", "sandwich", null, "attendees", 1),
        c("E104", "Pavel", "meals", 27, true,  "2026-09-02", "lunch", null, "attendees", 1),
        c("E105", "Lena",  "meals", 55, true,  "2026-09-03", "client lunch", null, "attendees", 3),
        c("E106", "Tomas", "meals", 38, true,  "2026-09-03", "dinner on a trip", null, "attendees", 1)); }

    public static List<ExpenseCase> stage2() { return List.of(
        c("E201", "Marta", "meals", 62, true, "2026-09-08", "client dinner", null, "attendees", 4),
        c("E202", "Jonas", "meals", 74, true, "2026-09-08", "dinner", null, "attendees", 1),
        c("E203", "Marta", "flight", 640, true, "2026-09-09", "Vienna return", null, "cabin", "economy", "flight_hours", 3),
        c("E204", "Ravi",  "flight", 1150, true, "2026-09-09", "Lisbon return", null, "cabin", "economy", "flight_hours", 5),
        c("E205", "Ravi",  "flight", 2400, true, "2026-09-10", "Singapore", null, "cabin", "business", "flight_hours", 9),
        c("E206", "Jonas", "flight", 1800, true, "2026-09-10", "Rome return", null, "cabin", "business", "flight_hours", 4),
        c("E207", "Lena",  "lodging", 420, true, "2026-09-11", "conference hotel", null, "nights", 3),
        c("E208", "Pavel", "lodging", 640, true, "2026-09-11", "client-site hotel", null, "nights", 3),
        c("E209", "Marta", "software", 150, true, "2026-09-12", "design tool licence", null, "vendor", "Linden"),
        c("E210", "Ravi",  "software", 90, true, "2026-09-12", "plugin", null, "vendor", "Zephyr"),
        c("E211", "Lena",  "equipment", 1300, true, "2026-09-13", "monitor and dock", null),
        c("E212", "Jonas", "training", 900, true, "2026-09-13", "negotiation course", null, "manager_approved", false),
        c("E213", "Marta", "flight", 640, true, "2026-09-09", "Vienna return", "E203", "cabin", "economy", "flight_hours", 3),
        c("E214", "Lena",  "training", 800, true, "2026-09-14", "data course", null, "manager_approved", true)); }

    public static List<ExpenseCase> stage3() { return List.of(
        c("E301", "Pavel", "meals", 40, true, "2026-09-22", "lunch", null, "attendees", 1),
        c("E302", "Lena",  "meals", 41, true, "2026-09-22", "lunch", null, "attendees", 1),
        c("E303", "Tomas", "meals", 90, true, "2026-09-22", "team dinner", null, "attendees", 2),
        c("E304", "Ravi",  "flight", 900, true, "2026-09-23", "Prague return", null, "cabin", "economy", "flight_hours", 4),
        c("E305", "Marta", "flight", 905, true, "2026-09-23", "Madrid return", null, "cabin", "economy", "flight_hours", 2),
        c("E306", "Jonas", "flight", 3100, true, "2026-09-24", "Tokyo", null, "cabin", "business", "flight_hours", 7),
        c("E307", "Pavel", "lodging", 540, true, "2026-09-24", "hotel", null, "nights", 3),
        c("E308", "Ines",  "lodging", 570, true, "2026-09-24", "hotel", null, "nights", 3),
        c("E309", "Tomas", "software", 200, true, "2026-09-25", "licence", null, "vendor", "Quark"),
        c("E310", "Lena",  "software", 260, true, "2026-09-25", "licence", null, "vendor", "Linden"),
        c("E311", "Marta", "equipment", 500, true, "2026-09-26", "laptop stand and keyboard", null),
        c("E312", "Jonas", "training", 1500, true, "2026-09-26", "leadership programme", null, "manager_approved", true),
        c("E313", "Pavel", "meals", 40, true, "2026-09-22", "lunch", "E301", "attendees", 1),
        c("E314", "Ravi",  "equipment", 2100, true, "2026-09-27", "workstation", null)); }

    /** A2a: run under policy v2 — a few thresholds and the approved-vendor list have moved since development. */
    public static List<ExpenseCase> policyChange() { return List.of(
        c("E401", "Lena",  "meals", 38, true, "2026-10-05", "lunch", null, "attendees", 1),
        c("E402", "Tomas", "meals", 60, true, "2026-10-05", "team lunch", null, "attendees", 3),
        c("E403", "Pavel", "meals", 80, true, "2026-10-05", "team dinner", null, "attendees", 3),
        c("E404", "Marta", "software", 150, true, "2026-10-06", "licence", null, "vendor", "Quark"),
        c("E405", "Ravi",  "software", 120, true, "2026-10-06", "licence", null, "vendor", "Orbit"),
        c("E406", "Jonas", "equipment", 450, true, "2026-10-06", "monitor", null),
        c("E407", "Ines",  "flight", 700, true, "2026-10-07", "Berlin return", null, "cabin", "economy", "flight_hours", 3),
        c("E408", "Lena",  "lodging", 700, true, "2026-10-07", "hotel", null, "nights", 4),
        c("E409", "Marta", "training", 1000, true, "2026-10-07", "course", null, "manager_approved", true),
        c("E410", "Pavel", "meals", 30, true, "2026-10-07", "lunch", null, "attendees", 1)); }

    // ------------------------------------------------------------------------------------------------------
    // Size: the same pilot at two scales. FULL is what the first complete run used (44 claims). LEAN keeps
    // what each stage is FOR and drops the redundancy (28 claims): in these agents a claim costs about two
    // cycles (one to fetch it, one to decide it), so claims are what the cycle count is made of.
    //
    //  stage 1  learn the system, meet one claim that needs the tutor, one that needs the receipt rule
    //  stage 2  meet EVERY category once (the stage message promises "every kind of claim") plus the duplicate
    //           rule: what the tutor explains is complete regardless, so one claim per topic is enough to trigger it
    //  stage 3  retention, no feedback: one claim per rule family, at the boundary that tells the rules apart
    //  deploy.  the v2 changes (5 claims) plus controls (3) that should NOT change — to catch over-correction;
    //           the first queued claim is a changed one, so an agent that commits a few and reads what comes back
    //           sees the first audit flag early
    // ------------------------------------------------------------------------------------------------------
    public enum Size { FULL, LEAN }

    private static List<ExpenseCase> pick(List<ExpenseCase> from, String... ids) {
        java.util.List<ExpenseCase> out = new java.util.ArrayList<>();
        for (String id : ids) for (ExpenseCase c : from) if (c.id.equals(id)) out.add(c);
        if (out.size() != ids.length) throw new IllegalStateException("unknown claim id among " + java.util.Arrays.toString(ids));
        return out;
    }

    public static List<ExpenseCase> stage1(Size size) {
        return size == Size.FULL ? stage1() : pick(stage1(), "E101", "E103", "E105", "E106");
    }

    public static List<ExpenseCase> stage2(Size size) {
        // meals (the single-attendee REJECT branch), flight x3 (economy, its duplicate, business >6h), lodging, software, equipment, training
        return size == Size.FULL ? stage2() : pick(stage2(), "E202", "E203", "E213", "E205", "E208", "E209", "E211", "E212");
    }

    public static List<ExpenseCase> stage3(Size size) {
        if (size == Size.FULL) return stage3();
        java.util.List<ExpenseCase> l = new java.util.ArrayList<>(pick(stage3(), "E302", "E303", "E305", "E308", "E309", "E311", "E312"));
        // the duplicate in the lean set duplicates E303 (the full set's E313 duplicates E301, which the lean set drops)
        l.add(c("E313", "Tomas", "meals", 90, true, "2026-09-22", "team dinner", "E303", "attendees", 2));
        return l;
    }

    public static List<ExpenseCase> policyChange(Size size) {
        return size == Size.FULL ? policyChange() : pick(policyChange(), "E401", "E402", "E403", "E404", "E405", "E406", "E407", "E410");
    }

    /**
     * A1, the clean assessment: held-out claims under the learned policy, with no change, no boundary values and no
     * ambiguity. All six categories, and every kind of outcome (three approvals, a no-receipt rejection, an escalation
     * to the manager, an escalation to the director). Every outcome is the same under policy v1 and v2, so this stage
     * cannot be confounded by the change A2a introduces. Measures baseline competence without any support, false
     * alarms, and what a routine run costs. The same six claims at both sizes.
     */
    public static List<ExpenseCase> cleanAssessment() { return List.of(
        c("E501", "Ines",  "meals", 25, true,  "2026-10-12", "coffee and a sandwich", null, "attendees", 1),
        c("E502", "Ravi",  "flight", 420, true, "2026-10-12", "Milan return", null, "cabin", "economy", "flight_hours", 2),
        c("E503", "Marta", "software", 85, true, "2026-10-13", "design plugin licence", null, "vendor", "Linden"),
        c("E504", "Lena",  "lodging", 300, false, "2026-10-13", "hotel, receipt lost", null, "nights", 2),
        c("E505", "Pavel", "equipment", 1300, true, "2026-10-14", "monitor and dock", null),
        c("E506", "Jonas", "training", 1500, true, "2026-10-14", "leadership programme", null, "manager_approved", true)); }

    public static List<ExpenseCase> cleanAssessment(Size size) { return cleanAssessment(); }
}
