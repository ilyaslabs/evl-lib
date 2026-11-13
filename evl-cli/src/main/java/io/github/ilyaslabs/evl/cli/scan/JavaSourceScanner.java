package io.github.ilyaslabs.evl.cli.scan;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LongLiteralExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.type.Type;
import io.github.ilyaslabs.evl.core.model.Location;
import io.github.ilyaslabs.evl.core.parse.AnnotationRecord;
import io.github.ilyaslabs.evl.core.parse.AnnotationRecord.ElementKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Extracts {@code @EVL.*} annotations from Java sources with JavaParser when no processor output
 * exists (EVL-CLI-003). Produces the same records as the annotation processor.
 */
public final class JavaSourceScanner {

    private static final String EVL = "io.github.ilyaslabs.evl.EVL";

    private final Path projectDir;
    private final JavaParser parser;

    public JavaSourceScanner(Path projectDir) {
        this.projectDir = projectDir.toAbsolutePath().normalize();
        ParserConfiguration cfg = new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        this.parser = new JavaParser(cfg);
    }

    /** Scans every {@code .java} file under the given roots. Files that do not parse are skipped. */
    public Map<String, List<AnnotationRecord>> scan(List<Path> roots) throws IOException {
        Map<String, List<AnnotationRecord>> out = new LinkedHashMap<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> s = Files.walk(root)) {
                files = s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            }
            for (Path f : files) {
                List<AnnotationRecord> records = scanFile(f, Files.readString(f));
                if (!records.isEmpty()) {
                    out.put(relative(f), records);
                }
            }
        }
        return out;
    }

    public List<AnnotationRecord> scanFile(Path file, String content) {
        ParseResult<CompilationUnit> r = parser.parse(content);
        if (r.getResult().isEmpty()) {
            return List.of();
        }
        CompilationUnit cu = r.getResult().get();
        if (!mentionsEvl(cu)) {
            return List.of();
        }
        Ctx ctx = new Ctx(cu, relative(file));
        List<AnnotationRecord> out = new ArrayList<>();
        cu.getPackageDeclaration().ifPresent(p -> {
            if (file.getFileName().toString().equals("package-info.java")) {
                annotations(p, "package:" + ctx.pkg, ElementKind.PACKAGE, ctx, out);
            }
        });
        for (TypeDeclaration<?> t : cu.getTypes()) {
            type(t, ctx.pkg.isEmpty() ? t.getNameAsString() : ctx.pkg + "." + t.getNameAsString(), ctx, out);
        }
        return out;
    }

    private static boolean mentionsEvl(CompilationUnit cu) {
        for (ImportDeclaration i : cu.getImports()) {
            if (i.getNameAsString().startsWith(EVL) || i.getNameAsString().equals("io.github.ilyaslabs.evl")) {
                return true;
            }
        }
        return cu.getPackageDeclaration().map(p -> p.getNameAsString().equals("io.github.ilyaslabs.evl")).orElse(false);
    }

    private void type(TypeDeclaration<?> t, String qn, Ctx ctx, List<AnnotationRecord> out) {
        annotations(t, qn, ElementKind.TYPE, ctx, out);
        if (t instanceof RecordDeclaration rec) {
            for (Parameter p : rec.getParameters()) {
                annotations(p, qn + "#" + p.getNameAsString(), ElementKind.FIELD, ctx, out);
            }
        }
        for (BodyDeclaration<?> m : t.getMembers()) {
            if (m instanceof TypeDeclaration<?> nested) {
                type(nested, qn + "." + nested.getNameAsString(), ctx, out);
            } else if (m instanceof MethodDeclaration md) {
                String ref = qn + "#" + md.getNameAsString() + params(md);
                annotations(md, ref, ElementKind.METHOD, ctx, out);
                for (Parameter p : md.getParameters()) {
                    annotations(p, ref, ElementKind.PARAMETER, ctx, out);
                }
            } else if (m instanceof ConstructorDeclaration cd) {
                annotations(cd, qn + "#<init>" + params(cd), ElementKind.CONSTRUCTOR, ctx, out);
            } else if (m instanceof FieldDeclaration fd) {
                for (VariableDeclarator v : fd.getVariables()) {
                    annotations(fd, qn + "#" + v.getNameAsString(), ElementKind.FIELD, ctx, out);
                }
            }
        }
        if (t instanceof com.github.javaparser.ast.body.EnumDeclaration en) {
            for (EnumConstantDeclaration c : en.getEntries()) {
                annotations(c, qn + "#" + c.getNameAsString(), ElementKind.FIELD, ctx, out);
            }
        }
    }

    private static String params(CallableDeclaration<?> c) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < c.getParameters().size(); i++) {
            Parameter p = c.getParameter(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append(simple(p.getType()));
            if (p.isVarArgs()) {
                sb.append("...");
            }
        }
        return sb.append(')').toString();
    }

    public static String simple(Type t) {
        String s = t.asString().replaceAll("<.*>", "").replaceAll("@\\S+\\s+", "").trim();
        String arrays = "";
        while (s.endsWith("[]")) {
            arrays += "[]";
            s = s.substring(0, s.length() - 2);
        }
        int dot = s.lastIndexOf('.');
        return (dot < 0 ? s : s.substring(dot + 1)) + arrays;
    }

    private void annotations(Node node, String element, ElementKind kind, Ctx ctx, List<AnnotationRecord> out) {
        List<AnnotationExpr> anns;
        if (node instanceof NodeWithAnnotations<?> n) {
            anns = n.getAnnotations();
        } else if (node instanceof PackageDeclaration p) {
            anns = p.getAnnotations();
        } else {
            return;
        }
        for (AnnotationExpr a : anns) {
            String name = evlName(a.getNameAsString(), ctx);
            if (name == null) {
                continue;
            }
            if (name.endsWith(".List")) {
                Expression v = value(a);
                List<Expression> items = v instanceof ArrayInitializerExpr arr ? arr.getValues() : v == null ? List.of() : List.of(v);
                for (Expression item : items) {
                    if (item instanceof AnnotationExpr inner) {
                        String in = evlName(inner.getNameAsString(), ctx);
                        if (in != null) {
                            out.add(record(inner, in, element, kind, ctx));
                        }
                    }
                }
            } else {
                out.add(record(a, name, element, kind, ctx));
            }
        }
    }

    private static Expression value(AnnotationExpr a) {
        if (a instanceof SingleMemberAnnotationExpr s) {
            return s.getMemberValue();
        }
        if (a instanceof NormalAnnotationExpr n) {
            for (MemberValuePair p : n.getPairs()) {
                if (p.getNameAsString().equals("value")) {
                    return p.getValue();
                }
            }
        }
        return null;
    }

    private AnnotationRecord record(AnnotationExpr a, String name, String element, ElementKind kind, Ctx ctx) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        if (a instanceof SingleMemberAnnotationExpr s) {
            put(attrs, "value", s.getMemberValue(), ctx);
        } else if (a instanceof NormalAnnotationExpr n) {
            for (MemberValuePair p : n.getPairs()) {
                put(attrs, p.getNameAsString(), p.getValue(), ctx);
            }
        }
        int line = a.getBegin().map(p -> p.line).orElse(0);
        int col = a.getBegin().map(p -> p.column).orElse(0);
        return new AnnotationRecord(name, element, kind, ctx.pkg, attrs, new Location(ctx.file, line, col));
    }

    /** Array-typed attributes of the EVL annotations; a single value is written without braces in Java. */
    private static final java.util.Set<String> ARRAYS = java.util.Set.of("tags", "responsibilities", "errors", "endpoint",
        "models", "environments", "affects", "see", "requirements", "decisions", "alternatives", "fields");

    private static void put(Map<String, Object> attrs, String key, Expression e, Ctx ctx) {
        Object v = constant(e, ctx);
        if (ARRAYS.contains(key) && v != null && !(v instanceof List<?>)) {
            v = List.of(v);
        }
        if (v == null || (v instanceof String s && s.isEmpty()) || "UNSPECIFIED".equals(v)
            || (v instanceof List<?> l && l.isEmpty())) {
            return;
        }
        attrs.put(key, v);
    }

    private static Object constant(Expression e, Ctx ctx) {
        if (e instanceof StringLiteralExpr s) {
            return s.asString();
        }
        if (e instanceof TextBlockLiteralExpr tb) {
            return tb.asString();
        }
        if (e instanceof BinaryExpr b && b.getOperator() == BinaryExpr.Operator.PLUS) {
            Object l = constant(b.getLeft(), ctx);
            Object r = constant(b.getRight(), ctx);
            return l instanceof String && r instanceof String ? l + (String) r : null;
        }
        if (e instanceof BooleanLiteralExpr b) {
            return b.getValue();
        }
        if (e instanceof IntegerLiteralExpr || e instanceof LongLiteralExpr) {
            return e.toString();
        }
        if (e instanceof FieldAccessExpr f) {
            return f.getNameAsString();
        }
        if (e instanceof NameExpr n) {
            return n.getNameAsString();
        }
        if (e instanceof ClassExpr c) {
            String t = c.getType().asString();
            return t.equals("void") || t.equals("Void") ? null : ctx.qualify(t);
        }
        if (e instanceof ArrayInitializerExpr arr) {
            List<Object> out = new ArrayList<>();
            for (Expression x : arr.getValues()) {
                Object v = constant(x, ctx);
                if (v != null) {
                    out.add(v);
                }
            }
            return out;
        }
        return null;
    }

    /** {@code EVL.Component}, the fully qualified form, or {@code Component} with an import of it. */
    private static String evlName(String n, Ctx ctx) {
        if (n.startsWith(EVL + ".")) {
            return n.substring(EVL.length() + 1);
        }
        if (n.startsWith("EVL.") && ctx.importsEvl) {
            return n.substring(4);
        }
        String head = n.contains(".") ? n.substring(0, n.indexOf('.')) : n;
        if (ctx.nestedImports.contains(head) || ctx.wildcardNested) {
            return n;
        }
        return null;
    }

    private String relative(Path f) {
        Path abs = f.toAbsolutePath().normalize();
        return (abs.startsWith(projectDir) ? projectDir.relativize(abs) : abs).toString().replace('\\', '/');
    }

    private static final class Ctx {
        final String pkg;
        final String file;
        final boolean importsEvl;
        final boolean wildcardNested;
        final java.util.Set<String> nestedImports = new java.util.HashSet<>();
        final Map<String, String> imports = new LinkedHashMap<>();

        Ctx(CompilationUnit cu, String file) {
            this.pkg = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
            this.file = file;
            boolean evl = pkg.equals("io.github.ilyaslabs.evl");
            boolean wild = false;
            for (ImportDeclaration i : cu.getImports()) {
                String n = i.getNameAsString();
                if (i.isAsterisk()) {
                    if (n.equals(EVL)) {
                        wild = true;
                    }
                    if (n.equals("io.github.ilyaslabs.evl")) {
                        evl = true;
                    }
                    continue;
                }
                if (n.equals(EVL)) {
                    evl = true;
                } else if (n.startsWith(EVL + ".")) {
                    nestedImports.add(n.substring(EVL.length() + 1).split("\\.")[0]);
                }
                imports.put(n.substring(n.lastIndexOf('.') + 1), n);
            }
            this.importsEvl = evl;
            this.wildcardNested = wild;
        }

        String qualify(String simple) {
            if (simple.contains(".") && Character.isLowerCase(simple.charAt(0))) {
                return simple;
            }
            String head = simple.contains(".") ? simple.substring(0, simple.indexOf('.')) : simple;
            String imported = imports.get(head);
            if (imported != null) {
                return imported + simple.substring(head.length());
            }
            if (List.of("String", "Object", "Integer", "Long", "Boolean").contains(simple)) {
                return "java.lang." + simple;
            }
            return pkg.isEmpty() ? simple : pkg + "." + simple;
        }
    }
}
