package com.vackosar.gitflowincrementalbuild.boundary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableMap;
import com.vackosar.gitflowincrementalbuild.control.Property;

/**
 * Tests the system properties parsing logic in {@link Configuration}.
 *
 * @author famod
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class ConfigurationTest {

    private final ExpectedException thrown = ExpectedException.none();

    @Rule
    public final RuleChain ruleChain = RuleChain
            // properly reset system properties after each test
            .outerRule((base, description) -> new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    final Properties backup = (Properties) System.getProperties().clone();
                    try {
                        base.evaluate();
                    } finally {
                        System.setProperties(backup);
                    }
                }
            })
            .around(thrown);

    @Mock
    private MavenExecutionRequest mavenExecutionRequestMock;

    @Mock
    private MavenSession mavenSessionMock;

    private final Properties projectProperties = new Properties();

    @Before
    public void setup() {
        when(mavenSessionMock.getRequest()).thenReturn(mavenExecutionRequestMock);

        MavenProject mockTLProject = mock(MavenProject.class);
        when(mockTLProject.getProperties()).thenReturn(projectProperties);
        when(mavenSessionMock.getTopLevelProject()).thenReturn(mockTLProject);
    }

    @Test
    public void invalidProperty() {
        String invalidProperty = Property.PREFIX + "invalid";
        System.setProperty(invalidProperty, "invalid");
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(invalidProperty);
        thrown.expectMessage(Property.disableBranchComparison.fullName());  // just one of those valid ones

        new Configuration.Provider(mavenSessionMock).get();
    }

    @Test
    public void enabled() {
        System.setProperty(Property.enabled.fullName(), "false");

        assertFalse(Configuration.isEnabled(mavenSessionMock));
    }

    @Test
    public void enabled_projectProperties() {
        projectProperties.put(Property.enabled.fullName(), "false");

        assertFalse(Configuration.isEnabled(mavenSessionMock));
    }

    @Test
    public void enabled_projectProperties_overriddenBySystemProperty() {
        projectProperties.put(Property.enabled.fullName(), "true");
        System.setProperty(Property.enabled.fullName(), "false");

        assertFalse(Configuration.isEnabled(mavenSessionMock));
    }

    @Test
    public void argsForNotImpactedModules() {
        System.setProperty(Property.argsForNotImpactedModules.fullName(), "x=true a=false");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals(ImmutableMap.of("x", "true", "a", "false"), configuration.argsForNotImpactedModules);
    }

    @Test
    public void excludeTransitiveModulesPackagedAs() {
        System.setProperty(Property.excludeTransitiveModulesPackagedAs.fullName(), "ear,war");

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertEquals(Arrays.asList("ear", "war"), configuration.excludeTransitiveModulesPackagedAs);
    }

    @Test
    public void forceBuildModules_pattern() {
        String expectedPatternString = ".*-some-artifact";
        System.setProperty(Property.forceBuildModules.fullName(), expectedPatternString);

        Configuration configuration = new Configuration.Provider(mavenSessionMock).get();

        assertNotNull("Field forceBuildModules is null", configuration.forceBuildModules);
        assertEquals("Unexpected number of Patterns in forceBuildModules", 1, configuration.forceBuildModules.size());
        Pattern pattern = configuration.forceBuildModules.get(0);
        assertNotNull("Pattern form forceBuildModules is null", pattern);
        assertEquals("Unexpected pattern string of Pattern from forceBuildModules", expectedPatternString, pattern.pattern());
    }

    @Test
    public void forceBuildModules_patternInvalid() {
        System.setProperty(Property.forceBuildModules.fullName(), "*-some-artifact");   // pattern is missing the dot
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(Property.forceBuildModules.fullName());
        thrown.expectCause(IsInstanceOf.instanceOf(PatternSyntaxException.class));

        new Configuration.Provider(mavenSessionMock).get();
    }
}
