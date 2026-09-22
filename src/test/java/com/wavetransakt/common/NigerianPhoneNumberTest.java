package com.wavetransakt.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NigerianPhoneNumberTest {

    @Test
    void canonicalizesCommonNigerianFormatsToLocalElevenDigits() {
        assertEquals("08012345678", NigerianPhoneNumber.toLocal("08012345678"));
        assertEquals("08012345678", NigerianPhoneNumber.toLocal("+2348012345678"));
        assertEquals("08012345678", NigerianPhoneNumber.toLocal("2348012345678"));
        assertEquals("08012345678", NigerianPhoneNumber.toLocal("8012345678"));
        assertEquals("08012345678", NigerianPhoneNumber.toLocal("+234 (801) 234-5678"));
    }

    @Test
    void producesProviderFriendlyInternationalDigitsWithoutChangingWaveIdentity() {
        assertEquals("2348012345678", NigerianPhoneNumber.toInternationalDigits("08012345678"));
    }

    @Test
    void rejectsUnsupportedOrAmbiguousNumbers() {
        assertThrows(IllegalArgumentException.class, () -> NigerianPhoneNumber.toLocal(""));
        assertThrows(IllegalArgumentException.class, () -> NigerianPhoneNumber.toLocal("12345"));
        assertThrows(IllegalArgumentException.class, () -> NigerianPhoneNumber.toLocal("+447700900123"));
        assertThrows(IllegalArgumentException.class, () -> NigerianPhoneNumber.toLocal("phone08012345678"));
        assertTrue(NigerianPhoneNumber.tryToLocal("+447700900123").isEmpty());
    }
}
