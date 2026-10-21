package faber.devl.cases.toy;

import org.json.JSONObject;

import faber.devl.WorkItem;

/** One meeting-room booking request. Focus rooms allow 2 hours in the first world and 1 hour after the change; workshop rooms allow 4 hours in both. */
public final class BookingRequest implements WorkItem {
    public final String id, person, room; public final int hours;

    public BookingRequest(String id, String person, String room, int hours) { this.id = id; this.person = person; this.room = room; this.hours = hours; }

    static int maxHours(String room, int world) { return room.equals("focus") ? (world == 1 ? 2 : 1) : 4; }
    String rule(int world) { return room + " rooms: a booking of up to " + maxHours(room, world) + (maxHours(room, world) == 1 ? " hour" : " hours") + " is granted, a longer one is refused"; }

    @Override public String id() { return id; }
    @Override public String group() { return room; }
    @Override public String expected(int world) { return hours <= maxHours(room, world) ? "grant" : "refuse"; }

    public JSONObject toJson() { return new JSONObject().put("request_id", id).put("person", person).put("room", room).put("hours", hours); }
    public String summary() { return id + ": " + person + ", " + room + " room, " + hours + " h"; }
}
