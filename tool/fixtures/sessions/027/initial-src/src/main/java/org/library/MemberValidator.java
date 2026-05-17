package org.library;

import java.time.LocalDate;

public class MemberValidator {

    private int t1 = 0;

    public boolean validate(Member m) {
        if (m == null) {
            return false;
        }
        String n = m.getName();
        if (n == null) return false;
        if (n.trim().length() == 0) return false;
        int y = m.getJoinedYear();
        if (y < 1900) return false;
        if (y > LocalDate.now().getYear()) return false;
        t1 = t1 + 1;
        return true;
    }

    public int getT1() { return t1; }
}
