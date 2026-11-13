package io.github.ilyaslabs.evl.cli.code;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import io.github.ilyaslabs.evl.cli.scan.JavaSourceScanner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Declarations found in code, from class files (ASM) or Java sources (JavaParser). Type names use
 * dots for nesting. Used to verify source references (EVL-CLI-002).
 */
public final class CodeIndex {

    /** A method or constructor. */
    public record Method(String name, List<String> params, int line) {
    }

    /** A type and its members. */
    public static final class TypeInfo {
        public final String name;
        public final String file;
        public final int line;
        public final List<Method> methods = new ArrayList<>();
        public final Map<String, Integer> fields = new LinkedHashMap<>();
        /** Simple and qualified names this type's code mentions (imports, constant pool). */
        public final Set<String> references = new HashSet<>();

        TypeInfo(String name, String file, int line) {
            this.name = name;
            this.file = file;
            this.line = line;
        }
    }

    final Map<String, TypeInfo> types = new HashMap<>();
    final Set<String> packages = new HashSet<>();
    boolean hasKotlinSources;
    boolean hasClasses;

    public boolean isEmpty() {
        return types.isEmpty();
    }

    public TypeInfo type(String name) {
        return types.get(name.replace('$', '.'));
    }

    // ------------------------------------------------------------------ sources

    public void addSources(Path projectDir, List<Path> roots) throws IOException {
        JavaParser parser = new JavaParser(new ParserConfiguration()
            .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE));
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> s = Files.walk(root)) {
                files = s.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".kt")).sorted().toList();
            }
            for (Path f : files) {
                if (f.toString().endsWith(".kt")) {
                    hasKotlinSources = true;
                    continue;
                }
                var r = parser.parse(Files.readString(f));
                if (r.getResult().isEmpty()) {
                    continue;
                }
                CompilationUnit cu = r.getResult().get();
                String pkg = cu.getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
                packages.add(pkg);
                String file = rel(projectDir, f);
                Set<String> refs = new HashSet<>();
                for (ImportDeclaration i : cu.getImports()) {
                    refs.add(i.getNameAsString());
                }
                cu.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class).forEach(t -> refs.add(t.getNameAsString()));
                cu.findAll(com.github.javaparser.ast.expr.NameExpr.class).forEach(n -> refs.add(n.getNameAsString()));
                for (TypeDeclaration<?> t : cu.getTypes()) {
                    source(t, pkg.isEmpty() ? t.getNameAsString() : pkg + "." + t.getNameAsString(), file, refs);
                }
            }
        }
    }

    private void source(TypeDeclaration<?> t, String qn, String file, Set<String> refs) {
        TypeInfo info = new TypeInfo(qn, file, t.getBegin().map(p -> p.line).orElse(0));
        info.references.addAll(refs);
        types.put(qn, info);
        if (t instanceof RecordDeclaration rec) {
            for (Parameter p : rec.getParameters()) {
                int line = p.getBegin().map(x -> x.line).orElse(0);
                info.fields.put(p.getNameAsString(), line);
                info.methods.add(new Method(p.getNameAsString(), List.of(), line));
            }
        }
        for (BodyDeclaration<?> m : t.getMembers()) {
            int line = m.getBegin().map(p -> p.line).orElse(0);
            if (m instanceof TypeDeclaration<?> nested) {
                source(nested, qn + "." + nested.getNameAsString(), file, refs);
            } else if (m instanceof MethodDeclaration md) {
                info.methods.add(new Method(md.getNameAsString(), params(md), line));
            } else if (m instanceof ConstructorDeclaration cd) {
                info.methods.add(new Method("<init>", params(cd), line));
            } else if (m instanceof FieldDeclaration fd) {
                for (VariableDeclarator v : fd.getVariables()) {
                    info.fields.put(v.getNameAsString(), line);
                }
            }
        }
        if (t instanceof com.github.javaparser.ast.body.EnumDeclaration en) {
            en.getEntries().forEach(c -> info.fields.put(c.getNameAsString(), c.getBegin().map(p -> p.line).orElse(0)));
        }
    }

    private static List<String> params(CallableDeclaration<?> c) {
        List<String> out = new ArrayList<>();
        for (Parameter p : c.getParameters()) {
            out.add(JavaSourceScanner.simple(p.getType()) + (p.isVarArgs() ? "..." : ""));
        }
        return out;
    }

    // ------------------------------------------------------------------ class files

    /** Adds compiled classes from a directory or jar. */
    public void addClasses(Path dirOrJar) throws IOException {
        if (Files.isDirectory(dirOrJar)) {
            try (Stream<Path> s = Files.walk(dirOrJar)) {
                for (Path p : s.filter(x -> x.toString().endsWith(".class")).toList()) {
                    try (InputStream in = Files.newInputStream(p)) {
                        clazz(in, rel(dirOrJar, p));
                    }
                }
            }
        } else if (Files.isRegularFile(dirOrJar) && dirOrJar.toString().endsWith(".jar")) {
            try (ZipFile zip = new ZipFile(dirOrJar.toFile())) {
                for (Enumeration<? extends ZipEntry> en = zip.entries(); en.hasMoreElements(); ) {
                    ZipEntry e = en.nextElement();
                    if (e.getName().endsWith(".class") && !e.getName().startsWith("META-INF/")) {
                        try (InputStream in = zip.getInputStream(e)) {
                            clazz(in, dirOrJar.getFileName() + "!/" + e.getName());
                        }
                    }
                }
            }
        }
    }

    private void clazz(InputStream in, String file) throws IOException {
        ClassReader reader;
        try {
            reader = new ClassReader(in);
        } catch (RuntimeException e) {
            return;
        }
        String internal = reader.getClassName();
        if (internal.endsWith("module-info") || internal.endsWith("package-info")) {
            String pkg = internal.contains("/") ? internal.substring(0, internal.lastIndexOf('/')).replace('/', '.') : "";
            packages.add(pkg);
            return;
        }
        hasClasses = true;
        String name = internal.replace('/', '.').replace('$', '.');
        TypeInfo info = new TypeInfo(name, file, 0);
        String pkg = internal.contains("/") ? internal.substring(0, internal.lastIndexOf('/')).replace('/', '.') : "";
        packages.add(pkg);
        char[] buf = new char[reader.getMaxStringLength()];
        for (int i = 1; i < reader.getItemCount(); i++) {
            try {
                Object c = reader.readConst(i, buf);
                if (c instanceof org.objectweb.asm.Type t && t.getSort() == org.objectweb.asm.Type.OBJECT) {
                    info.references.add(t.getClassName().replace('$', '.'));
                }
            } catch (RuntimeException ignored) {
                // tolerate unusual constant pools
            }
        }
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String n, String desc, String sig, String[] ex) {
                if (n.equals("<clinit>") || (access & Opcodes.ACC_SYNTHETIC) != 0) {
                    return null;
                }
                List<String> ps = new ArrayList<>();
                org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(desc);
                for (int i = 0; i < args.length; i++) {
                    String s = simple(args[i]);
                    if ((access & Opcodes.ACC_VARARGS) != 0 && i == args.length - 1 && s.endsWith("[]")) {
                        s = s.substring(0, s.length() - 2) + "...";
                    }
                    ps.add(s);
                }
                info.methods.add(new Method(n, ps, 0));
                return null;
            }

            @Override
            public FieldVisitor visitField(int access, String n, String desc, String sig, Object v) {
                if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
                    info.fields.put(n, 0);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        types.put(name, info);
    }

    private static String simple(org.objectweb.asm.Type t) {
        String s = t.getClassName();
        String arrays = "";
        while (s.endsWith("[]")) {
            arrays += "[]";
            s = s.substring(0, s.length() - 2);
        }
        int cut = Math.max(s.lastIndexOf('.'), s.lastIndexOf('$'));
        return (cut < 0 ? s : s.substring(cut + 1)) + arrays;
    }

    private static String rel(Path base, Path p) {
        Path a = base.toAbsolutePath().normalize();
        Path b = p.toAbsolutePath().normalize();
        return (b.startsWith(a) ? a.relativize(b) : b).toString().replace('\\', '/');
    }
}
