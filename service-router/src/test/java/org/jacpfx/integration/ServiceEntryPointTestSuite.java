package org.jacpfx.integration;

/**
 * Created by amo on 14.11.14.
 */
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
@RunWith(Suite.class)
@Suite.SuiteClasses({
        ServiceEntryPointTest.class,
        ServiceEntryPointTestPathParam.class,
        ServiceEntryPointTestQueryParam.class
        })

public class ServiceEntryPointTestSuite {
}
