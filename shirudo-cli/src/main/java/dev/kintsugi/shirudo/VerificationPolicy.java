package dev.kintsugi.shirudo;

import java.util.Map;

/** Operator-owned references. Boot PCR expectations must reflect the deployment's enrollment profile. */
public record VerificationPolicy(String akName, String expectedChain, String bindPcr9,
                                 String bindPcr11, String configRoot, int configPcr,
                                 Map<Integer, String> bootPcrs) {
    public VerificationPolicy {
        hex(akName, 68);
        hex(expectedChain, 64);
        hex(bindPcr9, 64);
        hex(bindPcr11, 64);
        hex(configRoot, 64);
        if (configPcr < 0 || configPcr > 15 || configPcr == 9 || configPcr == 11) {
            throw new IllegalArgumentException("Unsupported config PCR");
        }
        bootPcrs = Map.copyOf(bootPcrs);
        bootPcrs.forEach((pcr, digest) -> {
            if (pcr < 0 || pcr > 23) {
                throw new IllegalArgumentException("Invalid boot PCR");
            }
            hex(digest, 64);
        });
    }

    static void hex(String value, int size) {
        if (value == null || !value.matches("[0-9a-fA-F]{" + size + "}")) {
            throw new IllegalArgumentException("Expected hexadecimal value of length " + size);
        }
    }
}
