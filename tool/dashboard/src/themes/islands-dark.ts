/*
 * Shiki / VSCode-format theme translating JetBrains' "Islands Dark"
 * editor scheme to TextMate scopes. Approximate — IntelliJ's PSI-based
 * highlight attributes (e.g. KOTLIN_SMART_CAST_RECEIVER, mutable-variable
 * underline) have no TextMate equivalent and are dropped. Java is the
 * target language; JS/TS/Kotlin/HTML benefit incidentally where scopes
 * overlap.
 *
 * Palette pulled directly from `src/themes/IslandSchemeDark.xml` (the
 * `.theme.json` that ships alongside has no token colours — just IDE
 * chrome — so we source everything from the editor scheme here).
 */

// Editor background / foreground. BG is pinned to our design-system
// `--bg-3` (#18191c) rather than IntelliJ's native #191A1C (one digit
// off) so the diff card renders at the same surface tier as every other
// deep-well panel on the dashboard. FG matches IntelliJ's TEXT /
// DEFAULT_IDENTIFIER and our `--fg-2` — they happen to agree.
const BG = "#18191c"
const FG = "#bcbec4"

// From the <colors> block.
const CARET_ROW = "#1F2024"
const LINE_NUMBERS = "#4B5059"
const LINE_NUMBERS_ACTIVE = "#A1A3AB"
const INDENT_GUIDE = "#323438"
const INDENT_GUIDE_ACTIVE = "#4E5157"
const SELECTION = "#264f78" // editor.selectionBackground from .theme.json

// From <attributes>.
const COMMENT = "#7a7e85"
const DOC_COMMENT = "#5f826b"
const DOC_TAG = "#67a37c"
const KEYWORD = "#cf8e6d"
const STRING = "#6aab73"
const NUMBER = "#2aacb8"
const CONSTANT = "#c77dbb" // constants, instance/static fields, enum members
const ANNOTATION = "#b3ae60"
const FUNCTION_DECL = "#56a8f5"
const INSTANCE_METHOD = "#57aaf7"
const TYPE_PARAM = "#16baac"
const TAG = "#d5b778"
const LABEL = "#32b8af"

const theme = {
  name: "Islands Dark",
  type: "dark" as const,
  colors: {
    "editor.background": BG,
    "editor.foreground": FG,
    foreground: FG,
    "editor.lineHighlightBackground": CARET_ROW,
    "editor.selectionBackground": SELECTION,
    "editorLineNumber.foreground": LINE_NUMBERS,
    "editorLineNumber.activeForeground": LINE_NUMBERS_ACTIVE,
    "editorIndentGuide.background": INDENT_GUIDE,
    "editorIndentGuide.activeBackground": INDENT_GUIDE_ACTIVE,
    "editorCursor.foreground": "#CED0D6",
    "editorWhitespace.foreground": "#6F737A",
    // Diff gutter colours (ADDED_LINES / DELETED_LINES / MODIFIED_LINES).
    "diffEditor.insertedLineBackground": "#54915933",
    "diffEditor.removedLineBackground": "#868A9133",
    "diffEditor.insertedTextBackground": "#54915955",
    "diffEditor.removedTextBackground": "#868A9155",
  },
  tokenColors: [
    // Comments.
    {
      scope: [
        "comment",
        "comment.line",
        "comment.block",
        "punctuation.definition.comment",
      ],
      settings: { foreground: COMMENT, fontStyle: "italic" },
    },
    // Javadoc / docblock. IntelliJ renders these italic + greenish.
    {
      scope: ["comment.block.documentation"],
      settings: { foreground: DOC_COMMENT, fontStyle: "italic" },
    },
    {
      scope: [
        "keyword.other.documentation",
        "storage.type.class.javadoc",
        "punctuation.definition.keyword.javadoc",
        "variable.parameter.javadoc",
      ],
      settings: { foreground: DOC_TAG },
    },

    // Strings.
    {
      scope: [
        "string",
        "string.quoted",
        "string.quoted.double",
        "string.quoted.single",
        "string.template",
      ],
      settings: { foreground: STRING },
    },
    // Escape sequences inside strings (\n, \t, , etc.).
    {
      scope: ["constant.character.escape", "string source"],
      settings: { foreground: KEYWORD },
    },

    // Numbers and language constants (true/false/null).
    {
      scope: [
        "constant.numeric",
        "constant.language",
        "constant.language.boolean",
        "constant.language.null",
      ],
      settings: { foreground: NUMBER },
    },

    // Keywords + storage — all the orange in Java (public/class/if/return…).
    {
      scope: [
        "keyword",
        "keyword.control",
        "keyword.other",
        "keyword.operator.new",
        "keyword.operator.expression",
        "storage",
        "storage.type",
        "storage.type.primitive",
        "storage.type.java",
        "storage.type.generic.java",
        "storage.modifier",
      ],
      settings: { foreground: KEYWORD },
    },

    // Annotations — @Override, @Autowired, etc.
    {
      scope: [
        "meta.annotation",
        "storage.type.annotation",
        "punctuation.definition.annotation",
        "variable.annotation",
        "meta.declaration.annotation",
      ],
      settings: { foreground: ANNOTATION },
    },

    // Method declarations. Java grammar emits `entity.name.function` for
    // both declarations and invocations; we pick the declaration colour
    // which is a touch lighter blue in Islands.
    {
      scope: [
        "entity.name.function",
        "entity.name.function.member",
        "meta.definition.method entity.name.function",
        "meta.method.identifier entity.name.function",
        "support.function",
      ],
      settings: { foreground: FUNCTION_DECL },
    },
    // Method calls specifically — slightly different hue in IntelliJ.
    {
      scope: [
        "meta.function-call entity.name.function",
        "meta.method-call entity.name.function",
      ],
      settings: { foreground: INSTANCE_METHOD },
    },

    // Constants / static fields / instance fields — IntelliJ renders all
    // of these in the same pink/purple. TextMate can't tell instance from
    // static from the grammar alone, so they all share.
    {
      scope: [
        "constant",
        "variable.other.constant",
        "variable.other.enummember",
        "variable.other.property",
        "variable.other.object.property",
        "meta.field.declaration variable.other.object",
      ],
      settings: { foreground: CONSTANT },
    },

    // Type names + generic parameters. In Islands, class refs are plain
    // text — we only highlight type parameters (<T>) and known support
    // types (String, List, etc.) as the distinctive teal.
    {
      scope: [
        "entity.name.type.parameter",
        "storage.type.parameter",
        "support.class",
        "support.type",
      ],
      settings: { foreground: TYPE_PARAM },
    },
    // Regular class references stay at the default text colour — matches
    // the IntelliJ default where `DEFAULT_CLASS_REFERENCE` is `#bcbec4`.
    {
      scope: ["entity.name.type", "entity.name.class", "entity.other.inherited-class"],
      settings: { foreground: FG },
    },

    // Labels (Kotlin @tag, JS labels).
    {
      scope: ["entity.name.label", "keyword.other.label"],
      settings: { foreground: LABEL },
    },

    // HTML / XML / JSX tags.
    {
      scope: [
        "entity.name.tag",
        "meta.tag entity.name.tag",
        "punctuation.definition.tag",
      ],
      settings: { foreground: TAG },
    },
    {
      scope: ["entity.other.attribute-name"],
      settings: { foreground: FG },
    },

    // Parameters — default foreground in Islands, no special colour.
    {
      scope: ["variable.parameter"],
      settings: { foreground: FG },
    },

    // Punctuation falls back to FG — IntelliJ defaults every bracket /
    // brace / dot / comma to `DEFAULT_*` which is all `#bcbec4`.
    {
      scope: [
        "punctuation",
        "punctuation.separator",
        "punctuation.terminator",
        "punctuation.section",
        "keyword.operator",
      ],
      settings: { foreground: FG },
    },
  ],
}

export default theme
