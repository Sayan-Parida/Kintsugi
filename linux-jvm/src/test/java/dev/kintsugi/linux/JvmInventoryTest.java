package dev.kintsugi.linux;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JvmInventoryTest {
    @Test
    void readsField22WithSpacesAndParenthesesInCommandName() {
        String stat = "123 (my java ) launcher) S" + " 0".repeat(18) + " 47582 0";
        assertEquals(47582, JvmInventory.startTicks(stat));
    }

    @Test
    void rejectsMissingStartTime() {
        assertThrows(IllegalArgumentException.class, () -> JvmInventory.startTicks("123 (java) S"));
    }
}
