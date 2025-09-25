package io.jenkins.plugins.argocdrollback;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import hudson.Extension;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.argocdrollback.model.Ordering;
import io.jenkins.plugins.argocdrollback.util.StringUtil;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.logging.Logger;

@Extension
public class ArgoCDRollbackParameterConfiguration extends GlobalConfiguration {

    private static final Logger logger = Logger.getLogger(ArgoCDRollbackParameterConfiguration.class.getName());
    private static final String DEFAULT_ARGOCD_BASE_URL = "";

    public static ArgoCDRollbackParameterConfiguration get() {
        return GlobalConfiguration.all().get(ArgoCDRollbackParameterConfiguration.class);
    }

    private String defaultArgoCDBaseURL = DEFAULT_ARGOCD_BASE_URL;
    private String defaultCredentialId = "";
    private Ordering defaultOrdering = Ordering.DESCENDING;

    public ArgoCDRollbackParameterConfiguration() {
        load();
    }

    public String getDefaultArgoCDBaseURL() {
        return StringUtil.isNotNullOrEmpty(defaultArgoCDBaseURL) ? defaultArgoCDBaseURL : DEFAULT_ARGOCD_BASE_URL;
    }

    public String getDefaultCredentialId() {
        return StringUtil.isNotNullOrEmpty(defaultCredentialId) ? defaultCredentialId : "";
    }

    public Ordering getDefaultTagOrdering() {
        return defaultOrdering != null ? defaultOrdering : Ordering.DESCENDING;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) {
        if (json.has("defaultArgoCDBaseURL")) {
            this.defaultArgoCDBaseURL = json.getString("defaultArgoCDBaseURL");
            logger.fine("argocd-rollback-parameter: Changed default ArgoCD Base URL to: " + defaultArgoCDBaseURL);
        }
        if (json.has("defaultCredentialId")) {
            this.defaultCredentialId = json.getString("defaultCredentialId");
            logger.fine("argocd-rollback-parameter: Changed default credentialsId to: " + defaultCredentialId);
        }
        if (json.has("defaultOrdering")) {
            this.defaultOrdering = Ordering.valueOf(json.getString("defaultOrdering"));
            logger.fine("argocd-rollback-parameter: Changed default ordering to: " + defaultOrdering);
        }
        save();
        return true;
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setDefaultRegistry(String defaultRegistry) {
        logger.info("argocd-rollback-parameter: Changing default ArgoCD Base URL to: " + defaultRegistry);
        this.defaultArgoCDBaseURL = defaultRegistry;
        save();
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setDefaultCredentialId(String defaultCredentialId) {
        logger.info("argocd-rollback-parameter: Changing default credentialsId to: " + defaultCredentialId);
        this.defaultCredentialId = defaultCredentialId;
        save();
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setDefaultTagOrdering(Ordering defaultTagOrdering) {
        logger.info("argocd-rollback-parameter: Changing default ordering to: " + defaultTagOrdering);
        this.defaultOrdering = defaultTagOrdering;
        save();
    }

    @SuppressWarnings("unused")
    public ListBoxModel doFillDefaultCredentialIdItems(@QueryParameter String credentialsId) {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            logger.info("argocd-rollback-parameter: No permission to list credential");
            return new StandardListBoxModel().includeCurrentValue(defaultCredentialId);
        }
        return new StandardListBoxModel()
            .includeEmptyValue()
            .includeAs(ACL.SYSTEM, Jenkins.get(), StringCredentials.class)
            .includeCurrentValue(defaultCredentialId);
    }

}