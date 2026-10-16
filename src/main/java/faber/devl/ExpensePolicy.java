package faber.devl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The hidden ground-truth approval policy of the invented company — deliberately idiosyncratic, so
 * that nothing about it can be derived from general knowledge: whatever the agent gets right, it
 * learned (from the tutor, from practice feedback, from its own notes), not from pretraining.
 * v2 is the change introduced in the policy-change assessment (A2a): a few thresholds and the approved-vendor list move, to test
 * whether the agent notices that knowledge acquired in development has gone stale.
 */
public final class ExpensePolicy {

    public static final class Verdict {
        public final String kind;      // "approve" | "reject" | "escalate"
        public final String to;        // escalation target, or null
        public final String clauseId;
        public final String explanation;
        Verdict(String kind, String to, String clauseId, String explanation) {
            this.kind = kind; this.to = to; this.clauseId = clauseId; this.explanation = explanation;
        }
        public String label() { return kind.equals("escalate") ? "escalate:" + to : kind; }
    }

    public final String name;
    final int mealsAuto, mealsGroupMax, equipAuto, equipMax, economyAuto, lodgingPerNight, softwareAuto, trainingAuto;
    final Set<String> approvedVendors;

    private ExpensePolicy(String name, int mealsAuto, int mealsGroupMax, Set<String> vendors, int equipAuto) {
        this.name = name; this.mealsAuto = mealsAuto; this.mealsGroupMax = mealsGroupMax;
        this.approvedVendors = vendors; this.equipAuto = equipAuto;
        this.equipMax = 2000; this.economyAuto = 900; this.lodgingPerNight = 180;
        this.softwareAuto = 200; this.trainingAuto = 1200;
    }

    public static ExpensePolicy v1() { return new ExpensePolicy("v1", 40, 90, Set.of("Linden", "Quark"), 500); }
    public static ExpensePolicy v2() { return new ExpensePolicy("v2", 35, 75, Set.of("Linden", "Orbit"), 400); }

    public Verdict decide(ExpenseCase c) {
        if (!c.receipt)
            return new Verdict("reject", null, "receipt", describe("receipts"));
        if (c.duplicateOf != null)
            return new Verdict("reject", null, "duplicate", describe("duplicates"));
        switch (c.category) {
            case "meals": {
                int attendees = ((Number) c.attrs.getOrDefault("attendees", 1)).intValue();
                if (c.amount <= mealsAuto) return new Verdict("approve", null, "meals", describe("meals"));
                if (c.amount <= mealsGroupMax && attendees >= 2) return new Verdict("approve", null, "meals", describe("meals"));
                return new Verdict("reject", null, "meals", describe("meals"));
            }
            case "flight": {
                String cabin = String.valueOf(c.attrs.getOrDefault("cabin", "economy"));
                double hours = ((Number) c.attrs.getOrDefault("flight_hours", 0)).doubleValue();
                if (cabin.equals("business")) {
                    if (hours > 6) return new Verdict("escalate", "director", "flight", describe("flights"));
                    return new Verdict("reject", null, "flight", describe("flights"));
                }
                if (c.amount <= economyAuto) return new Verdict("approve", null, "flight", describe("flights"));
                return new Verdict("escalate", "manager", "flight", describe("flights"));
            }
            case "lodging": {
                int nights = Math.max(1, ((Number) c.attrs.getOrDefault("nights", 1)).intValue());
                if ((double) c.amount / nights <= lodgingPerNight) return new Verdict("approve", null, "lodging", describe("lodging"));
                return new Verdict("escalate", "manager", "lodging", describe("lodging"));
            }
            case "software": {
                String vendor = String.valueOf(c.attrs.getOrDefault("vendor", ""));
                if (!approvedVendors.contains(vendor)) return new Verdict("reject", null, "software", describe("software"));
                if (c.amount <= softwareAuto) return new Verdict("approve", null, "software", describe("software"));
                return new Verdict("escalate", "it", "software", describe("software"));
            }
            case "equipment": {
                if (c.amount <= equipAuto) return new Verdict("approve", null, "equipment", describe("equipment"));
                if (c.amount <= equipMax) return new Verdict("escalate", "manager", "equipment", describe("equipment"));
                return new Verdict("reject", null, "equipment", describe("equipment"));
            }
            case "training": {
                boolean ok = Boolean.TRUE.equals(c.attrs.get("manager_approved"));
                if (!ok) return new Verdict("reject", null, "training", describe("training"));
                if (c.amount <= trainingAuto) return new Verdict("approve", null, "training", describe("training"));
                return new Verdict("escalate", "director", "training", describe("training"));
            }
            default:
                return new Verdict("reject", null, "unknown", "Unknown expense category.");
        }
    }

    /** The company's rule for one topic, in prose — what the tutor teaches and what practice feedback quotes. */
    /**
     * A worked example: a sentence for the tutor to say, the expense it describes, and the outcome the sentence STATES. The outcome is written by hand,
     * not computed, on purpose: DevlPilotCheck runs decide() on every example, under both policies, so the prose and the logic cannot disagree unnoticed.
     * Amounts are expressed relative to the policy's own limits, so the examples stay true when the limits move; none coincides with a claim in any queue.
     */
    public static final class Example {
        public final String text, expected; public final ExpenseCase expense;
        Example(String text, ExpenseCase expense, String expected) { this.text = text; this.expense = expense; this.expected = expected; }
    }

    public List<Example> examples(String topic) {
        List<Example> out = new ArrayList<>();
        String d = "2026-01-05";
        String vendor = new java.util.TreeSet<>(approvedVendors).first();
        switch (topic) {
            case "receipts":
                out.add(new Example("a " + (mealsAuto - 15) + " EUR meal with no receipt attached is rejected",
                        ExpenseCases.c("X1", "Alex", "meals", mealsAuto - 15, false, d, "example", null, "attendees", 1), "reject")); break;
            case "duplicates":
                out.add(new Example("if the same employee submits two expenses for the same amount on the same date, the second is rejected",
                        ExpenseCases.c("X2", "Alex", "meals", mealsAuto - 15, true, d, "example", "X1", "attendees", 1), "reject")); break;
            case "meals":
                out.add(new Example("a " + (mealsAuto + 17) + " EUR team dinner with 5 attendees is approved",
                        ExpenseCases.c("X3", "Alex", "meals", mealsAuto + 17, true, d, "example", null, "attendees", 5), "approve"));
                out.add(new Example("the same " + (mealsAuto + 17) + " EUR for a single attendee is rejected",
                        ExpenseCases.c("X4", "Alex", "meals", mealsAuto + 17, true, d, "example", null, "attendees", 1), "reject"));
                out.add(new Example((mealsGroupMax + 18) + " EUR is rejected even with 5 attendees",
                        ExpenseCases.c("X5", "Alex", "meals", mealsGroupMax + 18, true, d, "example", null, "attendees", 5), "reject")); break;
            case "flights":
                out.add(new Example("an economy flight of " + (economyAuto - 120) + " EUR is approved",
                        ExpenseCases.c("X6", "Alex", "flight", economyAuto - 120, true, d, "example", null, "cabin", "economy", "flight_hours", 3), "approve"));
                out.add(new Example("an economy flight of " + (economyAuto + 130) + " EUR is escalated to the manager",
                        ExpenseCases.c("X7", "Alex", "flight", economyAuto + 130, true, d, "example", null, "cabin", "economy", "flight_hours", 3), "escalate:manager"));
                out.add(new Example("a 3-hour business-class flight is rejected",
                        ExpenseCases.c("X8", "Alex", "flight", 1500, true, d, "example", null, "cabin", "business", "flight_hours", 3), "reject"));
                out.add(new Example("an 8-hour business-class flight is escalated to the director",
                        ExpenseCases.c("X9", "Alex", "flight", 2400, true, d, "example", null, "cabin", "business", "flight_hours", 8), "escalate:director")); break;
            case "lodging":
                out.add(new Example("3 nights at " + (lodgingPerNight - 30) + " EUR a night is approved",
                        ExpenseCases.c("X10", "Alex", "lodging", (lodgingPerNight - 30) * 3, true, d, "example", null, "nights", 3), "approve"));
                out.add(new Example("3 nights at " + (lodgingPerNight + 30) + " EUR a night is escalated to the manager",
                        ExpenseCases.c("X11", "Alex", "lodging", (lodgingPerNight + 30) * 3, true, d, "example", null, "nights", 3), "escalate:manager")); break;
            case "software":
                out.add(new Example("a " + (softwareAuto - 60) + " EUR licence from " + vendor + " is approved",
                        ExpenseCases.c("X12", "Alex", "software", softwareAuto - 60, true, d, "example", null, "vendor", vendor), "approve"));
                out.add(new Example("a 60 EUR licence from a vendor that is not on the list is rejected",
                        ExpenseCases.c("X13", "Alex", "software", 60, true, d, "example", null, "vendor", "Zenith"), "reject"));
                out.add(new Example("a " + (softwareAuto + 150) + " EUR licence from " + vendor + " is escalated to IT",
                        ExpenseCases.c("X14", "Alex", "software", softwareAuto + 150, true, d, "example", null, "vendor", vendor), "escalate:it")); break;
            case "equipment":
                out.add(new Example("equipment of " + (equipAuto - 150) + " EUR is approved",
                        ExpenseCases.c("X15", "Alex", "equipment", equipAuto - 150, true, d, "example", null), "approve"));
                out.add(new Example("equipment of " + (equipAuto + 400) + " EUR is escalated to the manager",
                        ExpenseCases.c("X16", "Alex", "equipment", equipAuto + 400, true, d, "example", null), "escalate:manager"));
                out.add(new Example("equipment of " + (equipMax + 500) + " EUR is rejected",
                        ExpenseCases.c("X17", "Alex", "equipment", equipMax + 500, true, d, "example", null), "reject")); break;
            case "training":
                out.add(new Example("manager-approved training of " + (trainingAuto - 350) + " EUR is approved",
                        ExpenseCases.c("X18", "Alex", "training", trainingAuto - 350, true, d, "example", null, "manager_approved", true), "approve"));
                out.add(new Example("manager-approved training of " + (trainingAuto + 450) + " EUR is escalated to the director",
                        ExpenseCases.c("X19", "Alex", "training", trainingAuto + 450, true, d, "example", null, "manager_approved", true), "escalate:director"));
                out.add(new Example("training of 500 EUR without manager approval is rejected",
                        ExpenseCases.c("X20", "Alex", "training", 500, true, d, "example", null, "manager_approved", false), "reject")); break;
            default: break;
        }
        return out;
    }

    /**
     * What the tutor says when it explains a topic in full: the rule, then worked examples. The examples are NOT part of describe(), which is also what
     * practice feedback and audit flags quote after a decision — those stay short.
     */
    public String explain(String topic) {
        String rule = describe(topic);
        if (rule == null) return null;
        List<Example> ex = examples(topic);
        if (ex.isEmpty()) return rule;
        StringBuilder sb = new StringBuilder(rule).append(" For example: ");
        for (int i = 0; i < ex.size(); i++) sb.append(i > 0 ? "; " : "").append(ex.get(i).text);
        return sb.append(".").toString();
    }

    public String describe(String topic) {
        switch (topic) {
            case "procedure": return "How to decide an expense, in this order: 1. no receipt attached: reject. 2. duplicate of an earlier expense "
                    + "(same employee, amount and date): reject. 3. otherwise apply the rule for its category (meals, flights, lodging, software, "
                    + "equipment, training). The first check that applies decides it.";
            case "receipts": return "Any expense without a receipt attached is rejected, whatever the category. This is the first check, before anything else.";
            case "duplicates": return "An expense that duplicates an earlier one (same employee, amount and date) is rejected. This check comes after the "
                    + "receipt check and before the category rules.";
            case "meals": return "Meals: up to " + mealsAuto + " EUR is approved outright. Above " + mealsAuto + " and up to "
                    + mealsGroupMax + " EUR it is approved only for a team or client meal, with two or more attendees. Above "
                    + mealsGroupMax + " EUR, or above " + mealsAuto + " EUR for a single attendee, it is rejected.";
            case "flights": return "Flights: economy up to " + economyAuto + " EUR is approved; economy above that is escalated to the "
                    + "employee's manager. Business class is rejected unless the flight is longer than 6 hours, in which case it is "
                    + "escalated to the director.";
            case "lodging": return "Lodging: up to " + lodgingPerNight + " EUR per night is approved; above that per-night rate, escalate to the manager.";
            case "software": return "Software: only vendors on the approved list (" + String.join(", ", new java.util.TreeSet<>(approvedVendors))
                    + ") can be approved, and only up to " + softwareAuto + " EUR; above that from an approved vendor, escalate to IT. "
                    + "A vendor not on the list is rejected.";
            case "equipment": return "Equipment: up to " + equipAuto + " EUR is approved; above " + equipAuto + " and up to " + equipMax
                    + " EUR escalate to the manager; above " + equipMax + " EUR reject — it must go through procurement.";
            case "training": return "Training: needs the manager_approved flag. With it, up to " + trainingAuto + " EUR is approved and "
                    + "above that escalated to the director; without it, rejected.";
            default: return null;
        }
    }

    public static final List<String> TOPICS = List.of("receipts", "duplicates", "meals", "flights", "lodging", "software", "equipment", "training");
}
