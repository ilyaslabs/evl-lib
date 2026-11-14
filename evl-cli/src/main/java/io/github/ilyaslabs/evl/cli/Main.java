package io.github.ilyaslabs.evl.cli;

import io.github.ilyaslabs.evl.core.FlowModel;
import io.github.ilyaslabs.evl.core.FlowNode;
import io.github.ilyaslabs.evl.core.Problem;
import io.github.ilyaslabs.evl.core.Resource;
import io.github.ilyaslabs.evl.core.Scanner;
import io.github.ilyaslabs.evl.core.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** {@code evl [dir] [--json] [--out file]}: prints the flow tree (or the JSON model) of a source tree. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Path dir = Path.of(".");
        boolean json = false;
        Path file = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--json" -> json = true;
                case "--out", "-o" -> {
                    if (++i >= args.length) return usage(err);
                    file = Path.of(args[i]);
                }
                case "--version", "-V" -> {
                    out.println("evl " + version());
                    return 0;
                }
                case "--help", "-h" -> {
                    usage(out);
                    return 0;
                }
                case "scan" -> { }
                default -> {
                    if (args[i].startsWith("-")) return usage(err);
                    dir = Path.of(args[i]);
                }
            }
        }
        if (!Files.isDirectory(dir)) {
            err.println("Not a directory: " + dir);
            return 2;
        }
        FlowModel model = Scanner.scan(dir);
        String text = json ? Json.write(model.toJson()) : tree(model);
        if (file != null) {
            try {
                Files.writeString(file, text, StandardCharsets.UTF_8);
            } catch (IOException e) {
                err.println("Cannot write " + file + ": " + e.getMessage());
                return 2;
            }
        } else {
            out.print(text);
        }
        for (Problem p : model.problems) err.println(p.level() + ": " + p.file() + ":" + p.line() + ": " + p.message());
        return model.problems.stream().anyMatch(p -> p.level().equals("error")) ? 1 : 0;
    }

    static String tree(FlowModel model) {
        StringBuilder b = new StringBuilder();
        if (model.flows.isEmpty()) b.append("No @Flow paths found.\n");
        for (FlowNode n : model.flows) node(b, n, 0);
        if (!model.resources.isEmpty()) {
            b.append("\nResources\n");
            for (Resource r : model.resources) {
                b.append("  ").append(r.kind).append(' ').append(r.name);
                if (r.code != null) b.append("  ").append(r.code.file()).append(':').append(r.code.line());
                b.append('\n');
            }
        }
        return b.toString();
    }

    private static void node(StringBuilder b, FlowNode n, int depth) {
        b.append("  ".repeat(depth));
        if (n.seq > 0) b.append(n.seq).append(". ");
        else if (n.async) b.append("~ ");
        b.append(n.segment());
        if (!n.name.equals(n.segment())) b.append(" \"").append(n.name).append('"');
        b.append(" [").append(n.kind).append(']');
        if (!n.subtitle.isEmpty()) b.append(' ').append(n.subtitle);
        if (!n.after.isEmpty()) b.append(" (after ").append(n.after).append(')');
        if (!n.uses.isEmpty()) b.append(" uses ").append(String.join(", ", n.uses));
        if (n.code != null) b.append("  ").append(n.code.file()).append(':').append(n.code.line());
        b.append('\n');
        for (FlowNode c : n.children) node(b, c, depth + 1);
    }

    private static int usage(PrintStream s) {
        s.println("Usage: evl [scan] [dir] [--json] [--out file]");
        s.println("Prints the flow tree built from @Flow, // @flow and @EVL.* in the Java and Kotlin sources under dir.");
        return 2;
    }

    static String version() {
        try (InputStream in = Main.class.getResourceAsStream("evl-version.properties")) {
            Properties p = new Properties();
            if (in != null) p.load(in);
            return p.getProperty("version", "dev");
        } catch (IOException e) {
            return "dev";
        }
    }
}
