package net.metalmc.metal;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Test suite for MetalMC optimization components.
 * Runs all unit and performance tests under the net.metalmc.metal package.
 */
@Suite(failIfNoTests = false)
@SuiteDisplayName("MetalMC Optimization Benchmark Suite")
@SelectPackages("net.metalmc.metal")
public class MetalMCTestSuite {
}
