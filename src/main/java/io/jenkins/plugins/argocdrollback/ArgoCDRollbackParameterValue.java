package io.jenkins.plugins.argocdrollback;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.util.VariableResolver;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.util.Locale;

/**
 * {@link ParameterValue} created from {@link ArgoCDRollbackParameterDefinition}.
 */
public class ArgoCDRollbackParameterValue extends ParameterValue {
    @Exported(visibility = 4)
    @Restricted(NoExternalUse.class)
    public String appName;

    @Exported(visibility = 4)
    @Restricted(NoExternalUse.class)
    public String rollbackVersion;

    @Exported(visibility = 4)
    @Restricted(NoExternalUse.class)
    public String value;

    @DataBoundConstructor
    public ArgoCDRollbackParameterValue(String name, String appName, String rollbackVersion) {
        this(name, appName, rollbackVersion, null);
    }

    public ArgoCDRollbackParameterValue(String name, String appName, String rollbackVersion, String description) {
        super(name, description);
        this.appName = appName;
        this.rollbackVersion = rollbackVersion;
//        this.value = String.format("%s:%s", appName, rollbackVersion);
        this.value = rollbackVersion;
    }

    public String getAppName() {
        return appName;
    }

    public String getRollbackVersion() {
        return rollbackVersion;
    }

    @Override
    public String getValue() {
        return value;
    }

    /**
     * Exposes the name/value as an environment variable.
     */
    @Override
    public void buildEnvironment(Run<?, ?> build, EnvVars env) {
        // exposes value
        env.put(name, value);
        env.put(name.toUpperCase(Locale.ENGLISH), value); // backward compatibility pre 1.345
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return name -> ArgoCDRollbackParameterValue.this.name.equals(name) ? value : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ArgoCDRollbackParameterValue that = (ArgoCDRollbackParameterValue) o;

        return value != null ? value.equals(that.value) : that.value == null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "(ArgoCDRollbackParameterValue) " + getName() + "='" + value + "'";
    }

    @Override
    public String getShortDescription() {
        return name + '=' + value;
    }
}
