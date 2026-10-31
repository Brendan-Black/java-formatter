import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Checks that a profile never takes away a line break. Each file is formatted twice: as it is, and with extra
 * line breaks put in at random where people break lines (after {@code , = ( ->}, before {@code . ) && +} and so
 * on). Every line break or blank line the formatter removed is reported, and so is a file whose formatted form
 * changes when formatted again. Nothing is written.
 *
 * <pre>
 * java -cp "JARS" tools/Check.java java-formatter.xml SEED File.java...
 * </pre>
 */
@SuppressWarnings("deprecation") // JDT's token constants (ITerminalSymbols) are deprecated but still the public way to scan
public class Check {
	/** Where people break lines: after these tokens, or before those. */
	static final Set<String> AFTER = Set.of(",", "=", "->", "(", "{", ";", "&&", "||", "+", "?", ":", "extends", "implements", "throws");
	static final Set<String> BEFORE = Set.of(".", "&&", "||", "+", "?", ":", "}", "->", "throws", "extends", "implements", ")");

	/** A token and the number of line breaks just before it. */
	record Tok(String text, int breaksBefore) {}

	public static void main(String[] args) throws Exception {
		CodeFormatter f = ToolFactory.createCodeFormatter(options(Path.of(args[0])));
		Random random = new Random(Long.parseLong(args[1]));
		int problems = 0;
		for (int i = 2; i < args.length; i++) {
			String original = Files.readString(Path.of(args[i])).replace("\r\n", "\n");
			for (String src : List.of(original, sprinkle(original, random))) {
				String label = args[i] + (src == original ? "" : " (with extra breaks)");
				String out = format(f, src);
				if (out == null) {
					System.out.println(label + ": can't format");
					problems++;
					continue;
				}
				List<Tok> a = tokens(src), b = tokens(out);
				if (a.size() != b.size()) {
					System.out.println(label + ": tokens changed");
					problems++;
					continue;
				}
				for (int t = 0; t < a.size(); t++) {
					if (b.get(t).breaksBefore() < a.get(t).breaksBefore() || lines(b.get(t).text()) < lines(a.get(t).text())) {
						problems++;
						String before = t == 0 ? "" : a.get(t - 1).text().strip().lines().reduce((x, y) -> y).orElse("");
						System.out.println(label + ": break lost between  " + before + "  and  " + a.get(t).text().lines().findFirst().orElse(""));
					}
				}
				if (src == original && !out.equals(format(f, out))) {
					System.out.println(label + ": not stable (formatting again changes it)");
					problems++;
				}
			}
		}
		System.out.println(problems + " problems");
	}

	static Map<String, String> options(Path profile) throws Exception {
		Map<String, String> options = new HashMap<>(DefaultCodeFormatterConstants.getEclipseDefaultSettings());
		options.put(JavaCore.COMPILER_SOURCE, "25");
		options.put(JavaCore.COMPILER_COMPLIANCE, "25");
		NodeList settings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(profile.toFile()).getElementsByTagName("setting");
		for (int i = 0; i < settings.getLength(); i++) {
			Element e = (Element) settings.item(i);
			options.put(e.getAttribute("id"), e.getAttribute("value"));
		}
		return options;
	}

	static String format(CodeFormatter f, String src) throws Exception {
		var edit = f.format(CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS, src, 0, src.length(), 0, "\n");
		if (edit == null) return null;
		Document d = new Document(src);
		edit.apply(d);
		return d.get();
	}

	static long lines(String s) {
		return s.chars().filter(c -> c == '\n').count();
	}

	/** The source with a line break put in at about half the places people would break a line. */
	static String sprinkle(String src, Random random) throws Exception {
		IScanner s = ToolFactory.createScanner(true, true, false, "25", "25");
		s.setSource(src.toCharArray());
		StringBuilder sb = new StringBuilder();
		int last = 0;
		String prev = "\n";
		for (int t; (t = s.getNextToken()) != ITerminalSymbols.TokenNameEOF;) {
			if (t == ITerminalSymbols.TokenNameWHITESPACE) continue;
			int start = s.getCurrentTokenStartPosition();
			String next = new String(s.getCurrentTokenSource());
			boolean plausible = AFTER.contains(prev) || BEFORE.contains(next) || prev.startsWith("@") && t == ITerminalSymbols.TokenNameIdentifier;
			sb.append(src, last, start);
			if (plausible && random.nextInt(2) == 0 && src.charAt(start - 1) != '\n') sb.append('\n');
			last = start;
			prev = t == ITerminalSymbols.TokenNameCOMMENT_LINE ? "\n" : next;
		}
		return sb.append(src.substring(last)).toString();
	}

	/** The tokens, comments included, each with the line breaks before it. */
	static List<Tok> tokens(String src) throws Exception {
		IScanner s = ToolFactory.createScanner(true, true, false, "25", "25");
		s.setSource(src.toCharArray());
		List<Tok> out = new ArrayList<>();
		int breaks = 0;
		for (int t; (t = s.getNextToken()) != ITerminalSymbols.TokenNameEOF;) {
			String text = new String(s.getCurrentTokenSource());
			if (t == ITerminalSymbols.TokenNameWHITESPACE) {
				breaks += lines(text);
				continue;
			}
			if (t == ITerminalSymbols.TokenNameCOMMENT_LINE && text.endsWith("\n")) { // a // comment owns its line break
				out.add(new Tok(text.substring(0, text.length() - 1), breaks));
				breaks = 1;
				continue;
			}
			out.add(new Tok(text, breaks));
			breaks = 0;
		}
		return out;
	}
}
