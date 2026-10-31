# java-formatter

My Java formatting rules, as an Eclipse JDT formatter profile: [`java-formatter.xml`](java-formatter.xml).
JDT is the formatter VS Code's Java extension (Red Hat) uses, and Eclipse and IntelliJ can import the same file.

## The rules

- **Tabs.** One tab per indentation level, and one more for a wrapped line, not two.
- **`case` is indented inside `switch`.**
- **Empty bodies stay on one line:** `private Foo() {}`, `record Point(int x, int y) {}`.
- **Line breaks you put in stay.** The formatter never joins wrapped lines, never joins comment lines, keeps
  blank lines (up to 99 in a row), and leaves parentheses where you put them. So how a long call is laid out,
  for example `(` ending the line, one argument per line and `)` on its own line, is your choice, made per call.
- **Braces on every `if`/`else`/`for`/`while`/`do` body.** A short block may stay on one line as you wrote it:
  `if (x == null) { return; }`, `for (...) { total += n; }`, and the same for a lambda's block or a `switch`
  case's. JDT can't add a missing brace, but it never leaves a braceless body on its `if`'s line:
  `if (x == null) return;` gets split in two, so the missing braces stand out.
- **A labelled statement starts on the line after its label.**
- **Lines up to 160 wide**, comments included. HTML in Javadoc is left as written.
- **Array initializers as `{1, 2}`.**

Everything else is Eclipse's built-in default. The file lists only the settings that differ from it, plus the
three tab settings, which match the default but are spelled out because tabs are the point.

## Using it

### VS Code

Put this in `.vscode/settings.json` for one project, or in your user settings for every project:

```json
{
	"java.format.settings.url": "https://raw.githubusercontent.com/Brendan-Black/java-formatter/main/java-formatter.xml",
	"java.format.settings.profile": "Brendan",
	"[java]": {
		"editor.defaultFormatter": "redhat.java",
		"editor.insertSpaces": false
	}
}
```

`java.format.settings.url` also takes a file path, absolute or relative to the workspace folder, if you'd
rather keep a copy in the project. After changing it, run **Developer: Reload Window**.

`editor.insertSpaces` matters: the Java extension takes tabs-or-spaces from the editor, not from the profile.

### Eclipse

Preferences → Java → Code Style → Formatter → Import…, and pick `java-formatter.xml`.

### IntelliJ IDEA

Settings → Editor → Code Style → Java → ⚙ → Import Scheme → Eclipse XML Profile. IntelliJ only maps some
Eclipse settings onto its own, so check the result. For exact results, the "Adapter for Eclipse Code
Formatter" plugin runs JDT itself with this file.

## What JDT can't do

Checked against JDT 3.48 (VS Code Java extension 1.58):

- **A space inside empty braces.** JDT writes `{}`. The only setting for `{ }` is for array initializers.
- **Adding braces.** A formatter only moves code around; putting in a missing `{ }` is a code change. JDT does
  it outside the formatter, as an Eclipse clean-up: "Use blocks in if/while/for/do statements".
- **A one-line block in an `if` that has an `else`.** `if (a) { x(); } else { y(); }` and `} else { y(); }` are
  always spread over three lines each: the one-line setting covers only an `if` without an `else`.
  `} else if (b) { y(); }` stays, because that block belongs to the inner `if`.
- **Keeping every line break.** Breaks are kept wherever JDT is allowed to wrap. A few spots have no wrap
  point, and a break there gets joined: right after a lambda's `->` (`s ->⏎ s.strip()`), before `->`, after
  `throws`, before the `:` of a for-each, between a name and its `.` or `(` (`System⏎.out`, `foo⏎(x)`), a
  `{` on its own line (always moved up), and an empty body split over two lines (`{⏎}` becomes `{}`).
- **A blank line only before documented fields.** `blank_lines_before_field` applies to every field, so it's
  left at 0 and those blank lines are added by hand.

## Gotchas

- **Some keys are misspelled on purpose.** `parentheses_positions_in_for_statment` and
  `parentheses_positions_in_method_delcaration` are how JDT spells them (so is `keep_imple_if_on_one_line`, if you
  add it). Spelled correctly, they are ignored.
- **Not every key takes every value.** Given an unsupported value, some keys make JDT throw (the tools below
  stop with an error) and others are silently ignored. VS Code's XML extension flags some of the silent ones as
  you edit the file.
- **VS Code adds `// ` when you press Enter after `https?://`.** That's not the formatter: the Java extension
  continues `//` comments on Enter. Its rule skips `http://` but not `?://`, so it fires inside regexes like
  `"^https?://example\\.com"`. Ctrl+Z straight after the Enter takes the `// ` back out.

## Tools

Two single-file programs that run JDT from the command line, using the jars VS Code's Java extension already
ships. `JARS` is its `server/plugins` folder plus `*`, for example
`%USERPROFILE%\.vscode\extensions\redhat.java-1.58.0-win32-x64\server\plugins\*` (quote it). Needs Java 25.

- **`tools/Fmt.java`** formats files in place, the way VS Code would, keeping each file's line endings:

  ```sh
  java -cp "JARS" tools/Fmt.java java-formatter.xml $(git ls-files '*.java')
  ```

- **`tools/Check.java`** checks that a profile never takes away a line break. Each file is formatted as it
  is, and again with extra line breaks put in at random where people break lines. It reports every line break
  or blank line that was lost, and any file that changes when formatted a second time. It writes nothing.

  ```sh
  java -cp "JARS" tools/Check.java java-formatter.xml 1 $(git ls-files '*.java')
  ```

  The `1` is the random seed. Files as written should come through with nothing lost. The extra-breaks pass
  only ever loses the kinds listed under [What JDT can't do](#what-jdt-cant-do).

## License

[MIT](LICENSE)
