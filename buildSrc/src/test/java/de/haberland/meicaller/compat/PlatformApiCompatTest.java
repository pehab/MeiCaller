package de.haberland.meicaller.compat;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PlatformApiCompatTest {
    public static class ConfigurationWithWeight {
        public int fontWeightAdjustment = 300;
    }
    public static class OldConfiguration {}
    public static class OverlayType {
        public static int systemOverlays() { return 512; }
    }
    public static class OldOverlayType {}
    public static class BrokenOverlayType {
        public static int systemOverlays() { throw new IllegalStateException("unexpected failure"); }
    }

    @Test public void retainsUserFontWeightOnSupportedPlatforms() {
        ConfigurationWithWeight config = new ConfigurationWithWeight();
        assertEquals(300, PlatformApiCompat.fontWeightAdjustment(config));
        config.fontWeightAdjustment = -100;
        assertEquals(-100, PlatformApiCompat.fontWeightAdjustment(config));
    }

    @Test public void retainsUndefinedFontWeightSentinel() {
        ConfigurationWithWeight config = new ConfigurationWithWeight();
        config.fontWeightAdjustment = Integer.MAX_VALUE;
        assertEquals(Integer.MAX_VALUE, PlatformApiCompat.fontWeightAdjustment(config));
    }

    @Test public void missingFontFieldUsesNeutralAdjustment() {
        assertEquals(0, PlatformApiCompat.fontWeightAdjustment(new OldConfiguration()));
    }

    @Test public void supportedOverlayMaskIsUnchanged() {
        assertEquals(512, PlatformApiCompat.systemOverlays(OverlayType.class));
    }

    @Test public void missingOverlayMethodContributesNoBits() {
        assertEquals(0, PlatformApiCompat.systemOverlays(OldOverlayType.class));
    }

    @Test public void missingOverlayClassContributesNoBits() {
        assertEquals(0, PlatformApiCompat.systemOverlays(null));
    }

    @Test(expected = IllegalStateException.class)
    public void unrelatedPlatformErrorsAreNotSwallowed() {
        PlatformApiCompat.systemOverlays(BrokenOverlayType.class);
    }
}
