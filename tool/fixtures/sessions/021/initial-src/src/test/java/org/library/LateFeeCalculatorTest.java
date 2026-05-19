package org.library;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.library.Member.MemberType;

import static org.junit.jupiter.api.Assertions.*;

public class LateFeeCalculatorTest {

    private final LateFeeCalculator calc = new LateFeeCalculator();
    private final LocalDate today = LocalDate.of(2024, 6, 30);

    private Loan overdueBy(long days) {
        return new Loan("l", "b", "m", today.minusDays(days + 10), today.minusDays(days));
    }

    private Member mem(MemberType t) {
        return new Member("m", "n", t, 2020);
    }

    @Test
    void notOverdueZero() {
        Loan l = new Loan("l", "b", "m", today.minusDays(5), today.plusDays(2));
        assertEquals(0.0, calc.calculateFee(l, mem(MemberType.STANDARD), today));
    }

    @Test
    void standardSmallBucket() {
        double f = calc.calculateFee(overdueBy(2), mem(MemberType.STANDARD), today);
        assertTrue(f > 0.0);
    }

    @Test
    void premiumDiscounted() {
        double std = calc.calculateFee(overdueBy(5), mem(MemberType.STANDARD), today);
        double prem = calc.calculateFee(overdueBy(5), mem(MemberType.PREMIUM), today);
        assertTrue(prem < std);
    }

    @Test
    void staffDiscounted() {
        double std = calc.calculateFee(overdueBy(10), mem(MemberType.STANDARD), today);
        double staff = calc.calculateFee(overdueBy(10), mem(MemberType.STAFF), today);
        assertTrue(staff < std);
    }

    @Test
    void longOverdueBigBucket() {
        double f = calc.calculateFee(overdueBy(40), mem(MemberType.STANDARD), today);
        assertTrue(f > 5.0);
    }

    @Test
    void helperIsIdentity() {
        assertEquals(1.5, calc.helper(1.5), 0.0001);
    }
}
