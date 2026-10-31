import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Formats Java files in place with a profile, the way VS Code's Java extension would, keeping each file's line
 * endings.
 *
 * <pre>
 * java -cp "JARS" tools/Fmt.java java-formatter.xml File.java...
 * </pre>
 */
public class Fmt {
	public static void main(String[] args) throws Exception {
		CodeFormatter f = ToolFactory.createCodeFormatter(options(Path.of(args[0])));
		for (int i = 1; i < args.length; i++) {
			Path p = Path.of(args[i]);
			String raw = Files.readString(p);
			boolean crlf = raw.contains("\r\n");
			String src = raw.replace("\r\n", "\n");
			var edit = f.format(CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS, src, 0, src.length(), 0, "\n");
			if (edit == null) {
				System.err.println("can't format (doesn't parse?): " + p);
				continue;
			}
			Document d = new Document(src);
			edit.apply(d);
			Files.writeString(p, crlf ? d.get().replace("\n", "\r\n") : d.get());
		}
	}

	/** Eclipse's defaults, Java 25, then the profile's settings on top. */
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
}
