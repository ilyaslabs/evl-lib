package io.github.ilyaslabs.evl.core.spec;

import io.github.ilyaslabs.evl.core.model.Level;
import java.util.Optional;

/**
 * The EVL 1.0 validation rule catalogue. The published {@code rules.yaml}, the plugin inspections
 * and the docs are generated from this enum.
 */
public enum Rule {
    E001("EVL-E001", Level.ERROR, "Invalid manifest", "evl.yaml is missing required fields (evl, id, name) or has invalid values."),
    E002("EVL-E002", Level.ERROR, "Overlapping manifests", "More than one evl.yaml claims the same source root."),
    E010("EVL-E010", Level.ERROR, "Duplicate id", "Two declarations use the same id (an annotation plus a YAML element with the same id merge instead)."),
    E011("EVL-E011", Level.ERROR, "Malformed id", "Ids are dotted lowercase segments: [a-z][a-z0-9-]* separated by dots, at most 128 characters."),
    E012("EVL-E012", Level.ERROR, "Missing id", "Elements declared in documents must have an id."),
    E020("EVL-E020", Level.ERROR, "Conflicting source", "An annotation and a YAML element with the same id point at different source locations."),
    E021("EVL-E021", Level.ERROR, "Reference kind mismatch", "The kind prefix of a reference does not match the target element's kind."),
    E022("EVL-E022", Level.ERROR, "Library not found", "A reference names an EVL library (app-id:) that is not on the classpath."),
    E023("EVL-E023", Level.ERROR, "Wrong reference target", "The attribute does not allow references to elements of this kind."),
    E024("EVL-E024", Level.ERROR, "Library element override", "A consuming application cannot redeclare an element of a library."),
    E025("EVL-E025", Level.ERROR, "Unresolved reference", "The referenced element does not exist."),
    E030("EVL-E030", Level.ERROR, "Unresolved source reference", "The source reference does not resolve to code."),
    E031("EVL-E031", Level.ERROR, "Ambiguous source reference", "The source reference matches several members; add parameter types."),
    E032("EVL-E032", Level.ERROR, "Duplicate library", "Two libraries with the same EVL id are on the classpath."),
    E033("EVL-E033", Level.ERROR, "Malformed source reference", "The source reference does not follow the syntax type[#member[(params)]]."),
    E050("EVL-E050", Level.ERROR, "Major version mismatch", "A document declares a different major spec version than the manifest."),
    E052("EVL-E052", Level.ERROR, "Unsupported spec version", "The manifest declares a major spec version this tool does not support."),
    E060("EVL-E060", Level.ERROR, "Unknown step type", "The step type is not one of the official step types."),
    E061("EVL-E061", Level.ERROR, "Duplicate step id", "Step ids must be unique within their flow."),
    E062("EVL-E062", Level.ERROR, "Unknown goto target", "A GOTO step targets a step id that does not exist in the same flow."),
    E063("EVL-E063", Level.ERROR, "Decision needs two branches", "A DECISION step needs at least two branches (counting otherwise)."),
    E064("EVL-E064", Level.ERROR, "Parallel needs two branches", "A PARALLEL step needs at least two branches."),
    E065("EVL-E065", Level.ERROR, "Call target is not a flow", "A CALL_FLOW step must reference a flow."),
    E066("EVL-E066", Level.ERROR, "Field not allowed for step type", "The step declares a field that its type does not accept."),
    E067("EVL-E067", Level.ERROR, "Missing step field", "The step type requires a field that is missing (e.g. flow for CALL_FLOW, target for GOTO)."),
    E068("EVL-E068", Level.ERROR, "Unknown flow input", "A CALL_FLOW input name is not declared by the called flow."),
    E070("EVL-E070", Level.ERROR, "Sensitive default value", "A sensitive config property must not declare a default value."),
    E071("EVL-E071", Level.ERROR, "Possible secret", "Text looks like a secret (private key, access key, password or high-entropy token)."),
    E100("EVL-E100", Level.ERROR, "YAML tag not allowed", "Custom YAML tags are not allowed in EVL documents."),
    E101("EVL-E101", Level.ERROR, "Duplicate YAML key", "A mapping declares the same key twice."),
    E102("EVL-E102", Level.ERROR, "Unknown field", "The field is not defined for this element kind (use an x- prefix for extensions)."),
    E103("EVL-E103", Level.ERROR, "Wrong value type", "The value does not have the type the schema requires."),
    E104("EVL-E104", Level.ERROR, "YAML syntax error", "The document is not valid YAML."),
    E105("EVL-E105", Level.ERROR, "Document too large", "The document exceeds a size, depth or alias limit."),
    E106("EVL-E106", Level.ERROR, "Missing required field", "A required field is missing."),
    E107("EVL-E107", Level.ERROR, "Invalid enum value", "The value is not one of the allowed values."),
    W001("EVL-W001", Level.WARNING, "Missing manifest", "No evl.yaml was found; an application id was derived."),
    W002("EVL-W002", Level.WEAK_WARNING, "Derived id", "The annotation has no explicit id; the derived id changes when the class is renamed."),
    W021("EVL-W021", Level.WARNING, "Recursive flow", "A flow calls itself directly or indirectly."),
    W051("EVL-W051", Level.WARNING, "Newer minor version", "The document uses a newer minor spec version than this tool supports."),
    W060("EVL-W060", Level.WEAK_WARNING, "Step order from annotations", "Steps declared only by @EVL.Step are ordered by source position, which is not guaranteed across files."),
    W061("EVL-W061", Level.WEAK_WARNING, "Default category", "The flow has no category; APPLICATION is assumed."),
    W066("EVL-W066", Level.WARNING, "Unreachable steps", "Steps after FAIL, END or an unconditional GOTO never run."),
    W067("EVL-W067", Level.WARNING, "Unconditional loop", "A GOTO jumps backwards without a when condition."),
    W071("EVL-W071", Level.WARNING, "Orphan doc", "@EVL.Doc is not attached to any EVL element."),
    W080("EVL-W080", Level.WARNING, "Outside module packages", "The element's source is outside the packages of its module."),
    I020("EVL-I020", Level.INFO, "Overridden by YAML", "Fields of an annotation are overridden by a YAML declaration."),
    I030("EVL-I030", Level.INFO, "Unrealized business step", "A BUSINESS flow step has neither realizedBy nor source."),
    I040("EVL-I040", Level.INFO, "Dependency not seen in code", "No code reference supports this declared dependency (it may be indirect)."),
    I041("EVL-I041", Level.INFO, "Source not checked", "The source reference could not be checked (no compiled classes or sources available).");

    private final String code;
    private final Level level;
    private final String title;
    private final String description;

    Rule(String code, Level level, String title, String description) {
        this.code = code;
        this.level = level;
        this.title = title;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public Level level() {
        return level;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    /** Rules whose level cannot be lowered by project configuration. */
    public boolean isLocked() {
        if (code.startsWith("EVL-E00")) {
            return true;
        }
        return code.startsWith("EVL-E10") && code.charAt(code.length() - 1) <= '4';
    }

    public static Optional<Rule> byCode(String code) {
        for (Rule r : values()) {
            if (r.code.equals(code)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
