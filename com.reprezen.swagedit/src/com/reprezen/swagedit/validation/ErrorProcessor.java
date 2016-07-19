/*******************************************************************************
 * Copyright (c) 2016 ModelSolv, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    ModelSolv, Inc. - initial API and implementation and/or initial documentation
 *******************************************************************************/
package com.reprezen.swagedit.validation;

import static com.reprezen.swagedit.validation.ValidationUtil.getLine;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.yaml.snakeyaml.nodes.Node;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.google.common.base.Joiner;
import com.reprezen.swagedit.Messages;
import com.reprezen.swagedit.validation.SwaggerError.MultipleSwaggerError;

/**
 * Creates {@link SwaggerError} by processing validation reports generated by the json schema validator.
 */
public class ErrorProcessor {

    private final Node document;

    public ErrorProcessor(Node document) {
        this.document = document;
    }

    /**
     * Returns set of {@link SwaggerError} created from a validation report.
     * 
     * @param report
     *            to process
     * @return set of validation errors
     */
    public Set<SwaggerError> processReport(ProcessingReport report) {
        final Set<SwaggerError> errors = new HashSet<>();
        if (report != null) {
            for (Iterator<ProcessingMessage> it = report.iterator(); it.hasNext();) {
                errors.addAll(processMessage(it.next()));
            }
        }
        return errors;
    }

    /**
     * Returns set of {@link SwaggerError} created from a validation message.
     * 
     * @param message
     *            to process
     * @return set of validation errors
     */
    public Set<SwaggerError> processMessage(ProcessingMessage message) {
        return fromNode(message.asJson(), 0);
    }

    /* package */Set<SwaggerError> processMessageNode(JsonNode value) {
        return fromNode(value, 0);
    }

    private Set<SwaggerError> fromNode(JsonNode error, int indent) {
        final Set<SwaggerError> errors = new HashSet<>();

        if (error.isArray()) {
            for (JsonNode el : error) {
                if (isMultiple(el)) {
                    errors.add(createMultiple(el, indent));
                } else {
                    errors.add(createUnique(el, indent));
                }
            }
        } else if (error.isObject()) {
            if (isMultiple(error)) {
                errors.add(createMultiple(error, indent));
            } else {
                errors.add(createUnique(error, indent));
            }
        }

        return errors;
    }

    /**
     * Returns true if the error matches more than one schema.
     * 
     * @param error
     * @return true if validation concerns more than one schema.
     */
    private boolean isMultiple(JsonNode error) {
        return error.has("nrSchemas") && error.get("nrSchemas").asInt() > 1;
    }

    private SwaggerError createUnique(JsonNode error, int indent) {
        final SwaggerError schemaError = new SwaggerError(getLine(error, document), getLevel(error),
                rewriteError(error));
        schemaError.indent = indent;

        return schemaError;
    }

    private SwaggerError createMultiple(JsonNode error, int indent) {
        final MultipleSwaggerError schemaError = new MultipleSwaggerError(getLine(error, document), getLevel(error));
        schemaError.indent = indent;

        final JsonNode reports = error.get("reports");
        for (Iterator<Entry<String, JsonNode>> it = reports.fields(); it.hasNext();) {
            Entry<String, JsonNode> next = it.next();
            schemaError.put(next.getKey(), fromNode(next.getValue(), indent + 1));
        }

        return schemaError;
    }

    protected String rewriteError(JsonNode error) {
        if (error == null || !error.has("keyword")) {
            return "";
        }

        switch (error.get("keyword").asText()) {
        case "type":
            return rewriteTypeError(error);
        case "enum":
            return rewriteEnumError(error);
        case "additionalProperties":
            return rewriteAdditionalProperties(error);
        case "required":
            return rewriteRequiredProperties(error);
        default:
            return error.get("message").asText();
        }
    }

    protected String rewriteRequiredProperties(JsonNode error) {
        JsonNode missing = error.get("missing");

        return String.format(Messages.error_required_properties, Joiner.on(", ").join(missing));
    }

    protected String rewriteAdditionalProperties(JsonNode error) {
        final JsonNode unwanted = error.get("unwanted");

        return String.format(Messages.error_additional_properties_not_allowed, Joiner.on(", ").join(unwanted));
    }

    protected String rewriteTypeError(JsonNode error) {
        final JsonNode found = error.get("found");
        final JsonNode expected = error.get("expected");

        String expect;
        if (expected.isArray()) {
            expect = expected.get(0).asText();
        } else {
            expect = expected.asText();
        }

        return String.format(Messages.error_typeNoMatch, found.asText(), expect);
    }

    protected String rewriteEnumError(JsonNode error) {
        final JsonNode value = error.get("value");
        final JsonNode enums = error.get("enum");
        final String enumString = Joiner.on(", ").join(enums);

        return String.format(Messages.error_notInEnum, value.asText(), enumString);
    }

    protected int getLevel(JsonNode message) {
        if (message == null || !message.has("level")) {
            return IMarker.SEVERITY_INFO;
        }

        switch (message.get("level").asText()) {
        case "error":
        case "fatal":
            return IMarker.SEVERITY_ERROR;
        case "warning":
            return IMarker.SEVERITY_WARNING;
        default:
            return IMarker.SEVERITY_INFO;
        }
    }

}
