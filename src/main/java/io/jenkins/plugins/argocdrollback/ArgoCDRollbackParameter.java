package io.jenkins.plugins.argocdrollback;

import io.jenkins.plugins.argocdrollback.model.Ordering;
import io.jenkins.plugins.argocdrollback.model.ResultContainer;
import kong.unirest.*;
import kong.unirest.json.JSONObject;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import kong.unirest.json.JSONArray;


public class ArgoCDRollbackParameter {

    private static final Logger logger = Logger.getLogger(ArgoCDRollbackParameter.class.getName());
    private static final Interceptor errorInterceptor = new ErrorInterceptor();

    private ArgoCDRollbackParameter() {
        throw new IllegalStateException("Utility class");
    }

    public static ResultContainer<List<String>> getRollbackVersions(String appName, String argoCDBaseURL,
                                                        String token, Ordering ordering) {
        ResultContainer<List<String>> container = new ResultContainer<>(Collections.emptyList());

        ResultContainer<List<String>> tags = getRollbackVersionsFromArgoCD(appName, argoCDBaseURL, token);

        if (tags.getErrorMsg().isPresent()) {
            container.setErrorMsg(tags.getErrorMsg().get());
            return container;
        }

        ResultContainer<List<String>> filterTags = sortRollbackVersion(tags.getValue(), ordering);
        filterTags.getErrorMsg().ifPresent(container::setErrorMsg);
        container.setValue(filterTags.getValue());
        return container;
    }

    private static ResultContainer<List<String>> sortRollbackVersion(List<String> tags, Ordering ordering) {
        ResultContainer<List<String>> container = new ResultContainer<>(Collections.emptyList());

        if (tags.isEmpty()) {
            return container;
        }

        try {
            Comparator<String> rollbackVersionComparator = Comparator.comparing(item -> {
                try {
                    String revisionId = item.split("\\|")[0].trim();
                    return Integer.parseInt(revisionId);
                } catch (NumberFormatException e) {
                    // If revision ID is not numeric, fall back to string comparison
                    logger.warning("argocd-rollback-parameter: Non-numeric revision ID found: " + item.split("\\|")[0].trim() + ", using string comparison");
                    return item.split("\\|")[0].trim().hashCode();
                }
            });

            container.setValue(tags.stream()
                .sorted(ordering == Ordering.DESCENDING ? rollbackVersionComparator.reversed() : rollbackVersionComparator)
                .collect(Collectors.toList()));

        } catch (Exception e) {
            logger.severe("argocd-rollback-parameter: Error sorting rollback versions: " + e.getMessage());
            // Fallback: return unsorted list
            container.setValue(new ArrayList<>(tags));
            container.setErrorMsg("Could not sort versions: " + e.getMessage());
        }

        return container;
    }

    private static String getJakartaTime(String input) {
        DateFormat formatInput = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        DateFormat formatOutput = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
        formatOutput.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));
        Date date;
        String formatted = "";
        try {
            date = formatInput.parse(input);
            formatted = formatOutput.format(date);
        } catch (ParseException ex) {
            Logger.getLogger(ArgoCDRollbackParameter.class.getName()).log(Level.SEVERE, null, ex);
        }

        return formatted;
    }

    private static ResultContainer<List<String>> getRollbackVersionsFromArgoCD(String appName, String argoCDBaseURL, String token) {
        ResultContainer<List<String>> resultContainer = new ResultContainer<>(new ArrayList<>());
        String url = argoCDBaseURL + "/api/v1/applications/" + appName;

        Unirest.config().reset();
        Unirest.config().enableCookieManagement(false).interceptor(errorInterceptor);
        Unirest.config().verifySsl(false);
        HttpResponse<JsonNode> response;
        if (!token.isEmpty()) {
             response= Unirest.get(url).header("Cookie", "argocd.token=" + token).asJson();
        }
        else {
            response= Unirest.get(url).asJson();
        }
        if (response.isSuccess()) {
            try {
                JSONArray history = response.getBody().getObject()
                    .getJSONObject("status")
                    .getJSONArray("history");

                for (int i = 0; i < history.length(); i++) {
                    JSONObject rollbackVersionJSON = history.getJSONObject(i);
                    String revisionId = rollbackVersionJSON.getString("id");
                    String deployedAt = getJakartaTime(rollbackVersionJSON.getString("deployedAt"));

                    // Build version info with safe JSON parsing
                    String sourceInfo = "";
                    try {
                        JSONObject source = rollbackVersionJSON.getJSONObject("source");
                        if (source.has("kustomize")) {
                            JSONObject kustomize = source.getJSONObject("kustomize");
                            if (kustomize.has("images")) {
                                sourceInfo = " | Images: " + kustomize.getJSONArray("images").toList().toString();
                            } else {
                                sourceInfo = " | Kustomize (no images)";
                            }
                        } else if (source.has("helm")) {
                            JSONObject helm = source.getJSONObject("helm");
                            sourceInfo = " | Helm: " + (helm.has("chart") ? helm.getString("chart") : "unknown");
                        } else if (source.has("path")) {
                            sourceInfo = " | Path: " + source.getString("path");
                        } else {
                            sourceInfo = " | Source: " + source.toString();
                        }
                    } catch (Exception e) {
                        logger.warning("argocd-rollback-parameter: Could not parse source info for revision " + revisionId + ": " + e.getMessage());
                        sourceInfo = " | Source: unknown";
                    }

                    String rollbackVersion = revisionId + " | " + deployedAt + sourceInfo;
                    resultContainer.getValue().add(rollbackVersion);
                }

                if (history.length() == 0) {
                    logger.warning("argocd-rollback-parameter: No rollback history found for app: " + appName);
                    resultContainer.setErrorMsg("No rollback history available for application: " + appName);
                }

            } catch (Exception e) {
                logger.severe("argocd-rollback-parameter: Error parsing ArgoCD response: " + e.getMessage());
                resultContainer.setErrorMsg("Failed to parse ArgoCD response: " + e.getMessage() + 
                    ". Please check if the application exists and you have proper permissions.");
            }
        } else {
            logger.warning("argocd-rollback-parameter: Failed to connect to ArgoCD - HTTP " + response.getStatus() + ": " + response.getStatusText());
            resultContainer.setErrorMsg("Connection failed - HTTP " + response.getStatus() + ": " + response.getStatusText() + 
                ". Please check ArgoCD URL and credentials.");
        }
        Unirest.shutDown();

        return resultContainer;
    }
}
