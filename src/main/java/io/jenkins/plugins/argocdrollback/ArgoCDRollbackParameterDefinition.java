package io.jenkins.plugins.argocdrollback;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.ParameterDefinition.ParameterDescriptor;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.argocdrollback.model.Ordering;
import io.jenkins.plugins.argocdrollback.model.ResultContainer;
import io.jenkins.plugins.argocdrollback.util.StringUtil;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;


public class ArgoCDRollbackParameterDefinition extends SimpleParameterDefinition {

    private static final long serialVersionUID = 3938123092372L;
    private static final Logger logger = Logger.getLogger(ArgoCDRollbackParameterDefinition.class.getName());
    private static final ArgoCDRollbackParameterConfiguration config = ArgoCDRollbackParameterConfiguration.get();

    private final String appName;
    private final String argoCDBaseURL;
    private final String credentialId;
    private Ordering ordering;
    private String errorMsg = "";

    @DataBoundConstructor
    public ArgoCDRollbackParameterDefinition(String name, String description, String appName, String argoCDBaseURL, String credentialId, Ordering ordering) {
        super(name, description);
        this.appName = appName;
        this.argoCDBaseURL = argoCDBaseURL;
        this.credentialId = getDefaultOrEmptyCredentialId(credentialId);
        this.ordering = ordering;
    }

    public String getAppName() {
        return appName;
    }

    public String getArgoCDBaseURL() {
        return argoCDBaseURL;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public Ordering getOrdering() {
        return ordering;
    }

    @DataBoundSetter
    @SuppressWarnings("unused")
    public void setTagOrder(Ordering tagOrder) {
        this.ordering = tagOrder;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    private String getDefaultOrEmptyCredentialId(String credentialId) {
        if (!StringUtil.isNotNullOrEmpty(credentialId)) {
            return config.getDefaultCredentialId();
        } else if (StringUtil.isNotNullOrEmpty(credentialId)) {
            return credentialId;
        } else {
            return "";
        }
    }

    public List<String> getRollbackVersions() {
        String token = "";

        StringCredentials credential = findCredential(credentialId);
        if (credential != null) {
            token = credential.getSecret().getPlainText();
        }

        ResultContainer<List<String>> resultContainer = ArgoCDRollbackParameter.getRollbackVersions(appName, argoCDBaseURL, token, ordering);
        Optional<String> optionalErrorMsg = resultContainer.getErrorMsg();
        if (optionalErrorMsg.isPresent()) {
            setErrorMsg(optionalErrorMsg.get());
        } else {
            setErrorMsg("");
        }

        return resultContainer.getValue();
    }

    private StringCredentials findCredential(String credentialId) {
        if (StringUtil.isNotNullOrEmpty(credentialId)) {
            List<Item> items = Jenkins.get().getAllItems();
            for (Item item : items) {
                List<StringCredentials> creds = CredentialsProvider.lookupCredentials(
                    StringCredentials.class,
                    item,
                    ACL.SYSTEM,
                    Collections.emptyList());
                for (StringCredentials cred : creds) {
                    if (cred.getId().equals(credentialId)) {
                        return cred;
                    }
                }
            }
            logger.warning("argocd-rollback-parameter: Cannot find credential for :" + credentialId + ":");
        } else {
            logger.info("argocd-rollback-parameter: CredentialId is empty");
        }
        return null;
    }

    @Override
    public ParameterDefinition copyWithDefaultValue(ParameterValue defaultValue) {
        if (defaultValue instanceof ArgoCDRollbackParameterValue) {
            return new ArgoCDRollbackParameterDefinition(getName(), getDescription(),
                getAppName(), getArgoCDBaseURL(), getCredentialId(), getOrdering());
        }
        return this;
    }

    @Override
    public ParameterValue createValue(String value) {
        return new ArgoCDRollbackParameterValue(getName(), getAppName(), value, getDescription());
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        return req.bindJSON(ArgoCDRollbackParameterValue.class, jo);
    }

    @Symbol("argoCDRollbackParameter")
    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        @Override
        @NonNull
        public String getDisplayName() {
            return "ArgoCD Rollback Parameter";
        }

        @SuppressWarnings("unused")
        public String getDefaultArgoCDBaseURL() {
            return config.getDefaultArgoCDBaseURL();
        }

        @SuppressWarnings("unused")
        public String getDefaultCredentialID() {
            return config.getDefaultCredentialId();
        }

        @SuppressWarnings("unused")
        public Ordering getDefaultTagOrdering() {
            return config.getDefaultTagOrdering();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item context,
                                                    @QueryParameter String credentialId) {
            if (context == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER) ||
                context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                logger.info("argocd-rollback-parameter: No permission to list credential");
                return new StandardListBoxModel().includeCurrentValue(credentialId);
            }
            return new StandardListBoxModel()
                .includeEmptyValue()
                .includeAs(ACL.SYSTEM, context, StringCredentials.class)
                .includeCurrentValue(credentialId);
        }
    }
}