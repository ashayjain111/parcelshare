package com.parcelshare.identity.idp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NameMatchTest {

    @Test
    void ignoresCaseSpacingPunctuationAndAccents() {
        assertTrue(NameMatch.matches("rahul verma", "RAHUL  VERMA"));
        assertTrue(NameMatch.matches("Rahul Verma", "Rahul, Verma."));
        assertTrue(NameMatch.matches("José Álvarez", "Jose Alvarez"));
    }

    @Test
    void allowsMiddleNamesOnEitherSide() {
        assertTrue(NameMatch.matches("Rahul Verma", "RAHUL KUMAR VERMA"));
        assertTrue(NameMatch.matches("Rahul Kumar Verma", "Rahul Verma"));
    }

    @Test
    void rejectsDifferentPeopleAndSharedSurnamesOnly() {
        assertFalse(NameMatch.matches("Ananya Gupta", "Priya Singh"));
        assertFalse(NameMatch.matches("Rahul Verma", "Amit Verma"));
        assertFalse(NameMatch.matches("Rahul Verma", "Rahul Sharma"));
    }

    @Test
    void aSingleWordIsTooWeakToMatchAnythingButItself() {
        assertFalse(NameMatch.matches("Rahul", "Rahul Verma"));
        assertTrue(NameMatch.matches("Rahul", "rahul"));
    }

    @Test
    void emptyAndNullNeverMatch() {
        assertFalse(NameMatch.matches("", "Rahul Verma"));
        assertFalse(NameMatch.matches(null, "Rahul Verma"));
        assertFalse(NameMatch.matches("Rahul Verma", "   "));
    }
}
