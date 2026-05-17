package org.library;

public class Member {
    public enum MemberType { STANDARD, PREMIUM, STAFF }

    private final String id;
    private final String name;
    private final MemberType type;
    private final int joinedYear;
    private int t1;

    public Member(String id, String name, MemberType type, int joinedYear) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.joinedYear = joinedYear;
        this.t1 = 0;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public MemberType getType() { return type; }
    public int getJoinedYear() { return joinedYear; }

    public int getT1() { return t1; }
    public void setT1(int v) { this.t1 = v; }

    public void bumpT1() {
        t1 = t1 + 1;
    }

    public boolean isActive() {
        return joinedYear >= 1900 && name != null && !name.isEmpty();
    }
}
