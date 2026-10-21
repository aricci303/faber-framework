package faber.devl.cases.expense;

import java.util.List;

/**
 * The pilot's hand-written queues, one per stage. Hand-written rather than random so that every rule, boundary and interaction is exercised on purpose, and so a stage's
 * difficulty is a design decision.
 *
 *   LEARNING    stage 1  learn the system, meet one claim that needs the tutor, one that needs the receipt rule
 *               stage 2  meet EVERY category once (the stage message promises "every kind of claim") plus the duplicate rule
 *               stage 3  retention, no feedback: one claim per rule family, at the boundary that tells the rules apart
 *   ASSESSMENT  A1       clean claims under the policy of learning: baseline competence, false alarms, what a routine run costs
 *               A2a      claims under the changed policy: the v2 changes (5 claims) plus controls (3) that should NOT change, to catch over-correction
 *   OPERATION   the real job, after the change: claims under the policy in force then (v2), four of six of which a reader of version 1 decides wrongly
 *
 * A claim costs about two cycles (one to fetch it, one to decide it), so claims are what the cycle count is made of: the queues are the lean ones, 28 claims in all before operation.
 */
public final class ExpenseQueues {
    private ExpenseQueues() {}

    static ExpenseClaim c(String id, String emp, String cat, int amt, boolean receipt, String date, String desc, String dupOf, Object... kv) {
        return new ExpenseClaim(id, emp, cat, amt, receipt, date, desc, dupOf, kv);
    }

    public static List<ExpenseClaim> stage1() { return List.of(
        c("E101", "Ines",  "meals", 18, true,  "2026-09-01", "team coffee", null, "attendees", 1),
        c("E103", "Ines",  "meals", 12, false, "2026-09-02", "sandwich", null, "attendees", 1),
        c("E105", "Lena",  "meals", 55, true,  "2026-09-03", "client lunch", null, "attendees", 3),
        c("E106", "Tomas", "meals", 38, true,  "2026-09-03", "dinner on a trip", null, "attendees", 1)); }

    /** meals (the single-attendee REJECT branch), flight x3 (economy, its duplicate, business >6h), lodging, software, equipment, training */
    public static List<ExpenseClaim> stage2() { return List.of(
        c("E202", "Jonas", "meals", 74, true, "2026-09-08", "dinner", null, "attendees", 1),
        c("E203", "Marta", "flight", 640, true, "2026-09-09", "Vienna return", null, "cabin", "economy", "flight_hours", 3),
        c("E213", "Marta", "flight", 640, true, "2026-09-09", "Vienna return", "E203", "cabin", "economy", "flight_hours", 3),
        c("E205", "Ravi",  "flight", 2400, true, "2026-09-10", "Singapore", null, "cabin", "business", "flight_hours", 9),
        c("E208", "Pavel", "lodging", 640, true, "2026-09-11", "client-site hotel", null, "nights", 3),
        c("E209", "Marta", "software", 150, true, "2026-09-12", "design tool licence", null, "vendor", "Linden"),
        c("E211", "Lena",  "equipment", 1300, true, "2026-09-13", "monitor and dock", null),
        c("E212", "Jonas", "training", 900, true, "2026-09-13", "negotiation course", null, "manager_approved", false)); }

    /** one claim per rule family at the boundary that tells the rules apart; the duplicate duplicates E303 */
    public static List<ExpenseClaim> stage3() { return List.of(
        c("E302", "Lena",  "meals", 41, true, "2026-09-22", "lunch", null, "attendees", 1),
        c("E303", "Tomas", "meals", 90, true, "2026-09-22", "team dinner", null, "attendees", 2),
        c("E305", "Marta", "flight", 905, true, "2026-09-23", "Madrid return", null, "cabin", "economy", "flight_hours", 2),
        c("E308", "Ines",  "lodging", 570, true, "2026-09-24", "hotel", null, "nights", 3),
        c("E309", "Tomas", "software", 200, true, "2026-09-25", "licence", null, "vendor", "Quark"),
        c("E311", "Marta", "equipment", 500, true, "2026-09-26", "laptop stand and keyboard", null),
        c("E312", "Jonas", "training", 1500, true, "2026-09-26", "leadership programme", null, "manager_approved", true),
        c("E313", "Tomas", "meals", 90, true, "2026-09-22", "team dinner", "E303", "attendees", 2)); }

    /**
     * A1, the clean assessment: held-out claims under the policy of learning, with no change, no boundary values and no ambiguity. All six categories, and every kind of outcome
     * (three approvals, a no-receipt rejection, an escalation to the manager, an escalation to the director). Every outcome is the same under policy v1 and v2, so this stage cannot be
     * confounded by the change A2a introduces.
     */
    public static List<ExpenseClaim> cleanAssessment() { return List.of(
        c("E501", "Ines",  "meals", 25, true,  "2026-10-12", "coffee and a sandwich", null, "attendees", 1),
        c("E502", "Ravi",  "flight", 420, true, "2026-10-12", "Milan return", null, "cabin", "economy", "flight_hours", 2),
        c("E503", "Marta", "software", 85, true, "2026-10-13", "design plugin licence", null, "vendor", "Linden"),
        c("E504", "Lena",  "lodging", 300, false, "2026-10-13", "hotel, receipt lost", null, "nights", 2),
        c("E505", "Pavel", "equipment", 1300, true, "2026-10-14", "monitor and dock", null),
        c("E506", "Jonas", "training", 1500, true, "2026-10-14", "leadership programme", null, "manager_approved", true)); }

    /** A2a: run under policy v2 — a few thresholds and the approved-vendor list have moved since learning. */
    public static List<ExpenseClaim> policyChange() { return List.of(
        c("E401", "Lena",  "meals", 38, true, "2026-10-05", "lunch", null, "attendees", 1),
        c("E402", "Tomas", "meals", 60, true, "2026-10-05", "team lunch", null, "attendees", 3),
        c("E403", "Pavel", "meals", 80, true, "2026-10-05", "team dinner", null, "attendees", 3),
        c("E404", "Marta", "software", 150, true, "2026-10-06", "licence", null, "vendor", "Quark"),
        c("E405", "Ravi",  "software", 120, true, "2026-10-06", "licence", null, "vendor", "Orbit"),
        c("E406", "Jonas", "equipment", 450, true, "2026-10-06", "monitor", null),
        c("E407", "Ines",  "flight", 700, true, "2026-10-07", "Berlin return", null, "cabin", "economy", "flight_hours", 3),
        c("E410", "Pavel", "meals", 30, true, "2026-10-07", "lunch", null, "attendees", 1)); }

    /**
     * OPERATION: the real job, after the policy change. New claims, judged under the policy in force (v2). Four of the six are decided differently under version 1 — a solo meal
     * of 37 EUR (rejected now), a licence from Quark (no longer approved), one from Orbit (now approved), a 420 EUR monitor (now escalated) — and two are not (an economy flight, a
     * training course approved by the manager), so that a reader who does not check the version is wrong on four, and one who distrusts everything is wrong on two.
     */
    public static List<ExpenseClaim> operation() { return List.of(
        c("E601", "Tomas", "meals", 37, true, "2026-10-20", "lunch", null, "attendees", 1),
        c("E602", "Lena",  "software", 110, true, "2026-10-20", "licence", null, "vendor", "Quark"),
        c("E603", "Marta", "software", 95, true, "2026-10-21", "plugin licence", null, "vendor", "Orbit"),
        c("E604", "Ravi",  "equipment", 420, true, "2026-10-21", "monitor", null),
        c("E605", "Ines",  "flight", 540, true, "2026-10-22", "Zurich return", null, "cabin", "economy", "flight_hours", 2),
        c("E606", "Pavel", "training", 1000, true, "2026-10-22", "course", null, "manager_approved", true)); }
}
