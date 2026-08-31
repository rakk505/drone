package com.modernity.drone;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Trivial GameTests for Modernity sandbox validation.
 * These tests do not require world structures and serve as a sanity check
 * that the GameTest harness is wired correctly via runGameTestServer.
 *
 * NeoForge 26+ uses registry-based GameTest registration, not annotations.
 * See drone.java for DeferredRegister and RegisterGameTestsEvent wiring.
 */
public class ExampleGameTests {

    public static void trivialAddition(GameTestHelper helper) {
        // 1 + 1 = 2 sanity check
        helper.assertTrue(1 + 1 == 2, "expected 1+1 to equal 2");
        helper.succeed();
    }

    public static void trivialMultiplication(GameTestHelper helper) {
        // 2 * 3 = 6 sanity check
        helper.assertTrue(2 * 3 == 6, "expected 2*3 to equal 6");
        helper.assertFalse(2 * 2 == 5, "expected 2*2 not to equal 5");
        helper.succeed();
    }
}
