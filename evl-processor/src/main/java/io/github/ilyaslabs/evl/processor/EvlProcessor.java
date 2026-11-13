package io.github.ilyaslabs.evl.processor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * Extracts {@code @EVL.*} annotations into {@code META-INF/evl/index.json} (EVL-PROC-001..006).
 * Never generates sources and never fails the build for EVL problems unless
 * {@code -Aevl.failOnError=true}.
 *
 * <p>Options: {@code evl.manifest} (path to evl.yaml, writes manifest.json), {@code evl.artifact}
 * ({@code group:name:version}), {@code evl.projectDir} (makes file paths relative),
 * {@code evl.failOnError}.
 */
public final class EvlProcessor extends AbstractProcessor {

    static final String EVL = "io.github.ilyaslabs.evl.EVL";
    static final String GENERATOR = "evl-processor";

    private final List<Record> records = new ArrayList<Record>();
    private final Set<String> seen = new HashSet<String>();
    private Messager messager;
    private Elements elements;
    private Types types;
    private Object trees;
    private String projectDir;
    private boolean failOnError;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        messager = env.getMessager();
        elements = env.getElementUtils();
        types = env.getTypeUtils();
        trees = treesOf(env);
        String pd = env.getOptions().get("evl.projectDir");
        projectDir = pd == null ? null : new File(pd).getAbsoluteFile().toURI().getPath();
        failOnError = "true".equals(env.getOptions().get("evl.failOnError"));
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(EVL + ".*");
    }

    @Override
    public Set<String> getSupportedOptions() {
        Set<String> s = new HashSet<String>();
        Collections.addAll(s, "evl.manifest", "evl.artifact", "evl.projectDir", "evl.failOnError");
        return s;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        try {
            for (TypeElement a : annotations) {
                for (Element e : round.getElementsAnnotatedWith(a)) {
                    extract(e, a);
                }
            }
            if (round.processingOver()) {
                write();
            }
        } catch (RuntimeException ex) {
            messager.printMessage(Diagnostic.Kind.WARNING, "EVL: annotation extraction failed: " + ex);
        }
        return false;
    }

    private void extract(Element e, TypeElement annotationType) {
        for (AnnotationMirror m : e.getAnnotationMirrors()) {
            TypeElement t = (TypeElement) m.getAnnotationType().asElement();
            if (!t.equals(annotationType)) {
                continue;
            }
            String name = annotationName(t);
            if (name == null) {
                continue;
            }
            if (name.endsWith(".List")) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> v : m.getElementValues().entrySet()) {
                    if (v.getKey().getSimpleName().contentEquals("value") && v.getValue().getValue() instanceof List) {
                        for (Object o : (List<?>) v.getValue().getValue()) {
                            AnnotationMirror inner = (AnnotationMirror) ((AnnotationValue) o).getValue();
                            add(e, annotationName((TypeElement) inner.getAnnotationType().asElement()), inner, m);
                        }
                    }
                }
            } else {
                add(e, name, m, m);
            }
        }
    }

    private void add(Element e, String name, AnnotationMirror m, AnnotationMirror positioned) {
        Map<String, Object> attrs = new LinkedHashMap<String, Object>();
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> v : m.getElementValues().entrySet()) {
            Object value = value(v.getValue());
            if (value != null) {
                attrs.put(v.getKey().getSimpleName().toString(), value);
            }
        }
        int[] pos = new int[2];
        String file = position(e, positioned, pos);
        Record r = new Record(name, elementRef(e), kindOf(e), packageOf(e), attrs, file, pos[0], pos[1]);
        String key = r.annotation + "|" + r.element + "|" + r.line + "|" + r.column + "|" + attrs;
        if (!seen.add(key)) {
            return;
        }
        records.add(r);
        for (LocalChecks.Problem p : LocalChecks.check(r)) {
            Diagnostic.Kind kind = failOnError ? Diagnostic.Kind.ERROR : Diagnostic.Kind.WARNING;
            messager.printMessage(kind, p.code + " " + p.message, e, positioned);
        }
    }

    private String annotationName(TypeElement t) {
        String q = t.getQualifiedName().toString();
        if (!q.startsWith(EVL + ".")) {
            return null;
        }
        return q.substring(EVL.length() + 1);
    }

    private Object value(AnnotationValue av) {
        Object v = av.getValue();
        if (v instanceof String) {
            return ((String) v).isEmpty() ? null : v;
        }
        if (v instanceof Boolean) {
            return Boolean.TRUE.equals(v) ? v : Boolean.FALSE;
        }
        if (v instanceof VariableElement) {
            String n = ((VariableElement) v).getSimpleName().toString();
            return "UNSPECIFIED".equals(n) ? null : n;
        }
        if (v instanceof TypeMirror) {
            TypeMirror tm = (TypeMirror) v;
            if (tm.getKind() == TypeKind.VOID) {
                return null;
            }
            Element te = types.asElement(tm);
            return te instanceof TypeElement ? ((TypeElement) te).getQualifiedName().toString() : tm.toString();
        }
        if (v instanceof List) {
            List<Object> out = new ArrayList<Object>();
            for (Object o : (List<?>) v) {
                Object x = value((AnnotationValue) o);
                if (x != null) {
                    out.add(x);
                }
            }
            return out.isEmpty() ? null : out;
        }
        if (v instanceof Number || v instanceof Character) {
            return v.toString();
        }
        return null;
    }

    static String kindOf(Element e) {
        ElementKind k = e.getKind();
        if (k == ElementKind.PACKAGE) {
            return "PACKAGE";
        }
        if (k.isClass() || k.isInterface()) {
            return "TYPE";
        }
        if (k == ElementKind.METHOD) {
            return "METHOD";
        }
        if (k == ElementKind.CONSTRUCTOR) {
            return "CONSTRUCTOR";
        }
        if (k == ElementKind.PARAMETER) {
            return "PARAMETER";
        }
        return "FIELD"; // fields, enum constants, record components
    }

    private String packageOf(Element e) {
        PackageElement p = elements.getPackageOf(e);
        return p == null || p.isUnnamed() ? "" : p.getQualifiedName().toString();
    }

    /** The declaration as a source reference: {@code a.B}, {@code a.B#m(String,int)}, {@code a.B#f}, {@code package:a}. */
    String elementRef(Element e) {
        switch (e.getKind()) {
            case PACKAGE:
                return "package:" + ((PackageElement) e).getQualifiedName();
            case METHOD:
            case CONSTRUCTOR: {
                ExecutableElement m = (ExecutableElement) e;
                StringBuilder sb = new StringBuilder(typeName(e.getEnclosingElement())).append('#');
                sb.append(e.getKind() == ElementKind.CONSTRUCTOR ? "<init>" : m.getSimpleName().toString()).append('(');
                List<? extends VariableElement> params = m.getParameters();
                for (int i = 0; i < params.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(simpleType(params.get(i).asType(), m.isVarArgs() && i == params.size() - 1));
                }
                return sb.append(')').toString();
            }
            case PARAMETER:
                return elementRef(e.getEnclosingElement());
            default:
                if (e instanceof TypeElement) {
                    return typeName(e);
                }
                return typeName(e.getEnclosingElement()) + "#" + e.getSimpleName();
        }
    }

    private String typeName(Element e) {
        if (e instanceof TypeElement) {
            return ((TypeElement) e).getQualifiedName().toString();
        }
        return e.toString();
    }

    private String simpleType(TypeMirror t, boolean varargs) {
        TypeMirror erased = types.erasure(t);
        if (varargs && erased.getKind() == TypeKind.ARRAY) {
            return simpleType(((ArrayType) erased).getComponentType(), false) + "...";
        }
        if (erased.getKind() == TypeKind.ARRAY) {
            return simpleType(((ArrayType) erased).getComponentType(), false) + "[]";
        }
        if (erased.getKind() == TypeKind.DECLARED) {
            return ((DeclaredType) erased).asElement().getSimpleName().toString();
        }
        return erased.toString();
    }

    /** File (relative when possible) and line/column of the annotation, using the javac Trees API when present. */
    private String position(Element e, AnnotationMirror m, int[] pos) {
        String file = null;
        if (trees != null) {
            try {
                ClassLoader cl = trees.getClass().getClassLoader();
                Class<?> treesType = Class.forName("com.sun.source.util.Trees", false, cl);
                Class<?> pathType = Class.forName("com.sun.source.util.TreePath", false, cl);
                Class<?> cuType = Class.forName("com.sun.source.tree.CompilationUnitTree", false, cl);
                Class<?> treeType = Class.forName("com.sun.source.tree.Tree", false, cl);
                Class<?> spType = Class.forName("com.sun.source.util.SourcePositions", false, cl);
                Class<?> lmType = Class.forName("com.sun.source.tree.LineMap", false, cl);
                Object path = treesType.getMethod("getPath", Element.class, AnnotationMirror.class).invoke(trees, e, m);
                if (path == null) {
                    path = treesType.getMethod("getPath", Element.class).invoke(trees, e);
                }
                if (path != null) {
                    Object cu = pathType.getMethod("getCompilationUnit").invoke(path);
                    Object leaf = pathType.getMethod("getLeaf").invoke(path);
                    Object sp = treesType.getMethod("getSourcePositions").invoke(trees);
                    long p = (Long) spType.getMethod("getStartPosition", cuType, treeType).invoke(sp, cu, leaf);
                    Object lineMap = cuType.getMethod("getLineMap").invoke(cu);
                    pos[0] = (int) (long) (Long) lmType.getMethod("getLineNumber", long.class).invoke(lineMap, p);
                    pos[1] = (int) (long) (Long) lmType.getMethod("getColumnNumber", long.class).invoke(lineMap, p);
                    Object src = cuType.getMethod("getSourceFile").invoke(cu);
                    java.net.URI uri = ((javax.tools.FileObject) src).toUri();
                    file = relative(uri.getPath());
                }
            } catch (Exception | LinkageError ignored) {
                // Positions are best effort; the index stays valid without them.
            }
        }
        if (file == null) {
            Element top = e;
            while (top.getEnclosingElement() != null && !(top.getEnclosingElement() instanceof PackageElement)) {
                top = top.getEnclosingElement();
            }
            String pkg = packageOf(e);
            String base = top instanceof PackageElement ? "package-info" : top.getSimpleName().toString();
            file = (pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/") + base + ".java";
        }
        return file;
    }

    private String relative(String path) {
        if (path == null) {
            return null;
        }
        if (projectDir != null && path.startsWith(projectDir)) {
            return path.substring(projectDir.length());
        }
        for (String root : new String[] {"/src/main/java/", "/src/main/kotlin/", "/src/test/java/", "/src/"}) {
            int i = path.lastIndexOf(root);
            if (i >= 0) {
                return path.substring(i + 1);
            }
        }
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static Object treesOf(ProcessingEnvironment env) {
        try {
            Class<?> c = Class.forName("com.sun.source.util.Trees", false, env.getClass().getClassLoader());
            return c.getMethod("instance", ProcessingEnvironment.class).invoke(null, unwrap(env));
        } catch (Exception | LinkageError e) {
            return null; // not javac (e.g. ECJ): positions fall back to file names only
        }
    }

    /** Gradle and IntelliJ wrap the processing environment; javac's Trees needs the original. */
    private static ProcessingEnvironment unwrap(ProcessingEnvironment env) {
        for (Class<?> c = env.getClass(); c != null; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (ProcessingEnvironment.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object inner = f.get(env);
                        if (inner != null && inner != env) {
                            return unwrap((ProcessingEnvironment) inner);
                        }
                    } catch (Exception | LinkageError ignored) {
                        // keep env
                    }
                }
            }
        }
        return env;
    }

    private void write() {
        Filer filer = processingEnv.getFiler();
        try {
            if (!records.isEmpty()) {
                FileObject f = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/evl/index.json");
                writeTo(f, IndexWriter.index(records, GENERATOR));
            }
            String manifestPath = processingEnv.getOptions().get("evl.manifest");
            if (manifestPath != null && new File(manifestPath).isFile()) {
                Map<String, String> fields = readManifest(new File(manifestPath));
                if (fields.get("id") != null) {
                    FileObject f = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/evl/manifest.json");
                    writeTo(f, IndexWriter.manifest(fields, processingEnv.getOptions().get("evl.artifact")));
                }
            }
        } catch (IOException ex) {
            messager.printMessage(Diagnostic.Kind.WARNING, "EVL: cannot write META-INF/evl: " + ex.getMessage());
        }
    }

    private static void writeTo(FileObject f, String content) throws IOException {
        OutputStream out = f.openOutputStream();
        try {
            out.write(content.getBytes(Charset.forName("UTF-8")));
        } finally {
            out.close();
        }
    }

    /** Reads the top-level scalar fields of evl.yaml (id, name, description, kind, evl) without a YAML library. */
    static Map<String, String> readManifest(File file) throws IOException {
        Map<String, String> out = new LinkedHashMap<String, String>();
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), Charset.forName("UTF-8")));
        try {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || Character.isWhitespace(line.charAt(0)) || line.startsWith("#")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                int hash = value.indexOf(" #");
                if (hash >= 0) {
                    value = value.substring(0, hash).trim();
                }
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!value.isEmpty() && (key.equals("evl") || key.equals("id") || key.equals("name")
                    || key.equals("description") || key.equals("kind"))) {
                    out.put(key, value);
                }
            }
        } finally {
            r.close();
        }
        return out;
    }
}
