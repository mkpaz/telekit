package org.telekit.ui.tools.api_client;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import org.telekit.base.domain.AuthPrincipal;
import org.telekit.base.domain.LineSeparator;
import org.telekit.base.preferences.Proxy;
import org.telekit.base.util.PlaceholderReplacer;
import org.telekit.ui.tools.api_client.HttpClient.Request;
import org.telekit.ui.tools.api_client.HttpClient.Response;

import java.util.*;
import java.util.Map.Entry;

import static org.apache.commons.lang3.StringUtils.*;
import static org.telekit.base.util.CollectionUtils.nullToEmpty;
import static org.telekit.base.util.NumberUtils.ensureRange;
import static org.telekit.base.util.PasswordGenerator.ASCII_LOWER_UPPER_DIGITS;
import static org.telekit.base.util.PasswordGenerator.random;
import static org.telekit.ui.tools.api_client.Param.*;

public class Executor extends Task<ObservableList<CompletedRequest>> {

    public static final int MAX_CSV_SIZE = 100000;

    private final Template template;
    private final String[][] csv;
    private final HttpClient http;
    private int timeoutBetweenRequests = 200;

    private AuthType authType;
    private AuthPrincipal authPrincipal;
    private Proxy proxy;

    public Executor(Template template, String[][] csv) {
        this.template = new Template(template);
        this.csv = csv;
        this.http = new HttpClient(
                template.getWaitTimeout() * 1000,
                template.getWaitTimeout() * 1000
        );
    }

    private ReadOnlyObjectWrapper<ObservableList<CompletedRequest>> partialResults = new ReadOnlyObjectWrapper<>(
            this,
            "partialResults",
            FXCollections.observableArrayList(new ArrayList<>())
    );

    public final ObservableList<CompletedRequest> getPartialResults() { return partialResults.get(); }

    public final ReadOnlyObjectProperty<ObservableList<CompletedRequest>> partialResultsProperty() {
        return partialResults.getReadOnlyProperty();
    }

    @Override
    protected ObservableList<CompletedRequest> call() {
        Map<String, String> replacements = new HashMap<>();
        Set<Param> params = nullToEmpty(template.getParams());

        Map<String, String> headers = parseHeaders(template.getHeaders());
        Entry<String, String> contentType = contentTypeHeader(template.getContentType());
        headers.put(contentType.getKey(), contentType.getValue());

        if (proxy != null) {
            http.setProxy(proxy.getUrl(), proxy.getPrincipal());
        }

        if (authType == AuthType.BASIC) {
            http.setBasicAuth(
                    authPrincipal.getUsername(),
                    authPrincipal.getPassword(),
                    template.getUri().replaceAll(PlaceholderReplacer.PLACEHOLDER_PATTERN, "")
            );
        }

        int batchSize = ensureRange(template.getBatchSize(), 1, csv.length);
        int sequentialIndex = 0;
        for (int rowIndex = 0; rowIndex < csv.length & rowIndex < MAX_CSV_SIZE; rowIndex += batchSize) {
            if (isCancelled()) break;

            putTemplateParams(replacements, params); // autogenerated params need to be updated too

            String uri;
            String body;
            String userData;
            int processedLines;

            if (batchSize == 1) {
                String[] row = csv[rowIndex];
                putCsvParams(replacements, row, rowIndex);
                putIndexParams(replacements, sequentialIndex);

                // uri, headers & body can contain CSV placeholders
                uri = PlaceholderReplacer.format(template.getUri(), replacements);
                replaceHeadersPlaceholders(headers, replacements);
                body = PlaceholderReplacer.format(template.getBody(), replacements);

                userData = Arrays.toString(row) + " | " + uri;
                processedLines = 1;
                sequentialIndex++;
            } else {
                uri = PlaceholderReplacer.format(template.getUri(), replacements);
                replaceHeadersPlaceholders(headers, replacements);

                @SuppressWarnings("UnnecessaryLocalVariable")
                int from = rowIndex;
                int to = Math.min(rowIndex + batchSize, csv.length);

                // in batch mode only body can contain CSV placeholders
                String[][] batchCsv = Arrays.copyOfRange(csv, from, to);
                String[] batchBody = new String[batchCsv.length];
                for (int batchIndex = 0; batchIndex < batchCsv.length; batchIndex++) {
                    String[] row = batchCsv[batchIndex];
                    putCsvParams(replacements, row, rowIndex + batchIndex);
                    putIndexParams(replacements, sequentialIndex);
                    batchBody[batchIndex] = PlaceholderReplacer.format(template.getBody(), replacements);
                    sequentialIndex++;
                }

                body = wrapBatchItems(batchBody, template.getBatchWrapper(), template.getContentType());

                userData = uri;
                processedLines = batchCsv.length;
            }

            final Request request = new Request(template.getMethod().name(), uri, headers, body);
            final Response response = http.execute(request);
            final CompletedRequest result = new CompletedRequest(rowIndex, processedLines, request, response, userData);

            Platform.runLater(() -> getPartialResults().add(result));
            updateProgress(rowIndex, csv.length);

            if (rowIndex < csv.length - 1) {
                sleep(timeoutBetweenRequests);
            }
        }

        return partialResults.get();
    }

    public static List<Warning> validate(Template template, String[][] csv) {
        Set<Param> params = nullToEmpty(template.getParams());
        Map<String, String> replacements = new HashMap<>();
        List<Warning> warnings = new ArrayList<>();

        if (csv.length > MAX_CSV_SIZE) {
            warnings.add(Warning.CSV_THRESHOLD_EXCEEDED);
        }

        // check that all non-autogenerated params values has been specified
        boolean warnBlankValues = putTemplateParams(replacements, params);
        if (warnBlankValues) {
            warnings.add(Warning.BLANK_LINES);
        }

        int firstRowSize = 0;
        int peekRowSize = 0;
        String urlAndBodyFormatted = "";

        for (int rowIndex = 0; rowIndex < csv.length & rowIndex < MAX_CSV_SIZE; rowIndex++) {
            String[] row = csv[rowIndex];
            if (rowIndex == 0) {
                firstRowSize = peekRowSize = row.length;
                putIndexParams(replacements, rowIndex);
                putCsvParams(replacements, row, rowIndex);
                urlAndBodyFormatted = PlaceholderReplacer.format(
                        template.getUri() + template.getBody(),
                        replacements
                );
            } else {
                peekRowSize = row.length;
            }
        }

        // check that all csv table rows has the same columns count
        if (firstRowSize != peekRowSize) {
            warnings.add(Warning.MIXED_CSV_SIZE);
        }

        // check that all placeholders has been replaced
        if (PlaceholderReplacer.containsPlaceholders(urlAndBodyFormatted)) {
            warnings.add(Warning.UNRESOLVED_PLACEHOLDERS);
        }

        return warnings;
    }

    private static Map<String, String> parseHeaders(String text) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (isBlank(text)) return headers;

        for (String line : text.split(LineSeparator.LINE_SPLIT_PATTERN)) {
            if (isBlank(line)) continue;
            String[] keyValue = line.split(":");
            if (keyValue.length == 2) {
                headers.put(trim(keyValue[0]), trim(keyValue[1]));
            }
        }
        return headers;
    }

    private static void replaceHeadersPlaceholders(Map<String, String> headers, Map<String, String> replacements) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            entry.setValue(PlaceholderReplacer.format(entry.getValue(), replacements));
        }
    }

    private static boolean putTemplateParams(Map<String, String> accumulator, Set<Param> params) {
        boolean hasBlankValues = false;
        for (Param param : params) {
            switch (param.getType()) {
                case CONSTANT:
                    // put params into replacements even if param value is empty
                    // because it might not be an error but intended behavior
                    accumulator.put(
                            param.getName(),
                            defaultString(param.getValue(), "")
                    );
                    break;
                case PASSWORD:
                    accumulator.put(
                            param.getName(),
                            generatePassword(param.getLength())
                    );
                    break;
                case PASSWORD_BASE64:
                    accumulator.put(
                            param.getName(),
                            new String(Base64.getEncoder().encode(generatePassword(param.getLength()).getBytes()))
                    );
                    break;
                case UUID:
                    accumulator.put(param.getName(), UUID.randomUUID().toString());
                    break;
            }
            if (!param.isAutoGenerated() && isBlank(param.getValue())) {
                hasBlankValues = true;
            }
        }
        return hasBlankValues;
    }

    private static void putCsvParams(Map<String, String> accumulator, String[] row, int rowIndex) {
        for (int index = 0; index < row.length; index++) {
            accumulator.put("_csv" + index, row[index]);
        }
    }

    private static void putIndexParams(Map<String, String> accumulator, int sequentialIndex) {
        accumulator.put("_index0", String.valueOf(sequentialIndex));
        accumulator.put("_index1", String.valueOf(sequentialIndex + 1));
    }

    private static String generatePassword(int length) {
        return random(
                ensureRange(length, MIN_PASSWORD_LENGTH, MAX_PASSWORD_LENGTH, DEFAULT_PASSWORD_LENGTH),
                ASCII_LOWER_UPPER_DIGITS
        );
    }

    private static Entry<String, String> contentTypeHeader(String contentType) {
        Entry<String, String> defaultContentType = Map.entry(HttpClient.CONTENT_TYPE_HEADER, HttpClient.CONTENT_TYPE_TEXT);
        if (isBlank(contentType)) return defaultContentType;

        switch (contentType) {
            case Template.CONTENT_TYPE_JSON:
            case Template.CONTENT_TYPE_SOAP:
                return Map.entry(HttpClient.CONTENT_TYPE_HEADER, contentType);
            default:
                return defaultContentType;
        }
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    private static String wrapBatchItems(String[] items, String batchWrapper, String contentType) {
        String separator;
        switch (contentType) {
            case Template.CONTENT_TYPE_JSON:
                separator = ",";
                break;
            default:
                separator = "\n";
        }

        return PlaceholderReplacer.format(
                batchWrapper,
                Map.of("batch", String.join(separator, items))
        );
    }

    private static void sleep(int timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    public void setTimeoutBetweenRequests(int timeoutBetweenRequests) {
        this.timeoutBetweenRequests = timeoutBetweenRequests;
    }

    public void setAuthData(AuthType authType, AuthPrincipal authPrincipal) {
        this.authType = authType;
        this.authPrincipal = authPrincipal;
    }

    public void setProxy(Proxy proxy) {
        this.proxy = proxy;
    }

    public enum Warning {
        BLANK_LINES,
        MIXED_CSV_SIZE,
        UNRESOLVED_PLACEHOLDERS,
        CSV_THRESHOLD_EXCEEDED
    }

    public enum AuthType {
        BASIC
    }
}