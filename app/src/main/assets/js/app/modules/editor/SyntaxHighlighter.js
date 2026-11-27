/**
 * SyntaxHighlighter — tokenizes source code and returns HTML with span classes.
 * Optimized with single-pass tokenization, pre-compiled RegExp singletons, and fast LRU memoization.
 * Supports: java, kotlin, xml, gradle, json, yaml, markdown, properties, shell, batch
 */

// ── Pre-compiled regular expressions ──────────────────────────────────────────
const RE_JAVA_TOKEN = /(\/\*[\s\S]*?\*\/|\/\/.*)|("([^"\\]|\\.)*"|'([^'\\]|\\.)*')|(@\w+)|(\b(?:abstract|assert|boolean|break|byte|case|catch|char|class|const|continue|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|native|new|null|package|private|protected|public|return|short|static|strictfp|super|switch|synchronized|this|throw|throws|transient|try|void|volatile|while|true|false)\b)|(\b(?:String|Integer|Boolean|Double|Float|Long|Short|Byte|Character|Object|List|Map|Set|ArrayList|HashMap|HashSet|Optional|Builder|Context|View|Activity|Fragment|Intent|Bundle)\b)|(\b\d+[lLfFdD]?\b)/g;

const RE_KOTLIN_TOKEN = /(\/\*[\s\S]*?\*\/|\/\/.*)|("([^"\\]|\\.)*"|'([^'\\]|\\.)*')|(@\w+)|(\b(?:abstract|actual|annotation|as|break|by|catch|class|companion|const|constructor|continue|crossinline|data|do|else|enum|expect|external|false|final|finally|for|fun|get|if|import|in|infix|init|inline|inner|interface|internal|is|it|lateinit|noinline|null|object|open|operator|out|override|package|private|protected|public|reified|return|sealed|set|super|suspend|tailrec|this|throw|true|try|typealias|typeof|val|var|vararg|when|where|while)\b)|(\b(?:String|Integer|Boolean|Double|Float|Long|Short|Byte|Character|Object|List|Map|Set|ArrayList|HashMap|HashSet|Optional|Builder|Context|View|Activity|Fragment|Intent|Bundle)\b)|(\b\d+[lLfFdD]?\b)/g;

const RE_GRADLE_TOKEN = /(\/\/.*)|("([^"\\]|\\.)*"|'([^'\\]|\\.)*')|(\b(?:apply|plugin|android|dependencies|implementation|api|testImplementation|androidTestImplementation|compile|defaultConfig|buildTypes|release|debug|minifyEnabled|proguardFiles|compileSdkVersion|minSdkVersion|targetSdkVersion|versionCode|versionName|buildscript|repositories|classpath|allprojects|ext|task|def|if|else|for|return|true|false|null)\b)|(\b\d[\d.]*\b)/g;

const RE_SHELL_TOKEN = /(#.*)|("([^"\\]|\\.)*"|'([^'\\]|\\.)*')|(\$[\w{][^}\s]*)|(\b(?:echo|cd|ls|mkdir|chmod|export|source|if|then|else|fi|for|do|done|while|case|esac|exit|return|function|local|set|unset)\b)/g;

const RE_XML_COMMENT = /&lt;!--[\s\S]*?--&gt;/g;
const RE_XML_TAG = /&lt;\/?([a-zA-Z][\w.-]*)/g;
const RE_XML_ATTR_NAME = /([a-zA-Z_:][\w.-]*)(?==)/g;
const RE_XML_ATTR_VAL = /"([^"]*)"/g;

const RE_JSON_KEY = /"(\\.|[^"\\])*"(?=\s*:)/g;
const RE_JSON_STR_VAL = /:\s*"(\\.|[^"\\])*"/g;
const RE_JSON_BOOL = /\b(true|false|null)\b/g;
const RE_JSON_NUM = /\b-?\d+(\.\d+)?\b/g;

const RE_HASH_COMMENT = /#.*/g;
const RE_YAML_KEY = /^(\s*[\w-]+):/gm;
const RE_YAML_VAL = /: .+/g;
const RE_DECIMAL_NUMS = /\b\d[\d.]*\b/g;

const RE_MD_H = /^#{1,6} .+/gm;
const RE_MD_CODE = /`[^`]+`/g;
const RE_MD_BOLD = /\*\*([^*]+)\*\*/g;
const RE_MD_ITALIC = /\*([^*]+)\*/g;
const RE_MD_BULLET = /^[-*+] /gm;
const RE_MD_LINK = /\[([^\]]+)\]\(([^)]+)\)/g;

const RE_PROP_LINE = /^#.*/gm;
const RE_PROP_KEY = /^([^=\n]+)=/gm;
const RE_PROP_VAL = /=(.+)/g;

export class SyntaxHighlighter {
  static #cache = new Map();
  static #MAX_CACHE_SIZE = 64;

  /**
   * Highlight source code with LRU memoization.
   * @param {string} code
   * @param {string} lang
   * @returns {string} HTML string
   */
  static highlight(code, lang) {
    if (!code) return '';
    const cacheKey = `${lang}:${code.length}:${code.slice(0, 40)}:${code.slice(-40)}`;
    if (code.length < 80000 && SyntaxHighlighter.#cache.has(cacheKey)) {
      return SyntaxHighlighter.#cache.get(cacheKey);
    }

    const escaped = SyntaxHighlighter.#escape(code);
    let result = '';
    switch (lang) {
      case 'java':       result = SyntaxHighlighter.#java(escaped); break;
      case 'kotlin':     result = SyntaxHighlighter.#kotlin(escaped); break;
      case 'xml':        result = SyntaxHighlighter.#xml(escaped); break;
      case 'gradle':     result = SyntaxHighlighter.#gradle(escaped); break;
      case 'json':       result = SyntaxHighlighter.#json(escaped); break;
      case 'yaml':
      case 'yml':        result = SyntaxHighlighter.#yaml(escaped); break;
      case 'md':
      case 'markdown':   result = SyntaxHighlighter.#markdown(escaped); break;
      case 'properties': result = SyntaxHighlighter.#properties(escaped); break;
      case 'sh':
      case 'bash':       result = SyntaxHighlighter.#shell(escaped); break;
      default:           result = escaped; break;
    }

    if (code.length < 80000) {
      if (SyntaxHighlighter.#cache.size >= SyntaxHighlighter.#MAX_CACHE_SIZE) {
        const firstKey = SyntaxHighlighter.#cache.keys().next().value;
        SyntaxHighlighter.#cache.delete(firstKey);
      }
      SyntaxHighlighter.#cache.set(cacheKey, result);
    }
    return result;
  }

  /** Detect language from file extension. */
  static detectLang(filename) {
    const ext = filename.split('.').pop().toLowerCase();
    const map = {
      java: 'java', kt: 'kotlin', kts: 'kotlin',
      xml: 'xml', gradle: 'gradle', json: 'json',
      yml: 'yaml', yaml: 'yaml', md: 'markdown',
      properties: 'properties', sh: 'shell', bat: 'batch',
      txt: 'text', gitignore: 'text',
    };
    if (filename === 'gradlew' || filename === 'gradlew.bat') return 'shell';
    return map[ext] || 'text';
  }

  static #escape(s) {
    return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  }

  static #java(code) {
    return code.replace(RE_JAVA_TOKEN, (m, comment, str, _s1, _s2, anno, kw, type, num) => {
      if (comment) return `<span class="hl-comment">${comment}</span>`;
      if (str) return `<span class="hl-string">${str}</span>`;
      if (anno) return `<span class="hl-annotation">${anno}</span>`;
      if (kw) return `<span class="hl-keyword">${kw}</span>`;
      if (type) return `<span class="hl-type">${type}</span>`;
      if (num) return `<span class="hl-number">${num}</span>`;
      return m;
    });
  }

  static #kotlin(code) {
    return code.replace(RE_KOTLIN_TOKEN, (m, comment, str, _s1, _s2, anno, kw, type, num) => {
      if (comment) return `<span class="hl-comment">${comment}</span>`;
      if (str) return `<span class="hl-string">${str}</span>`;
      if (anno) return `<span class="hl-annotation">${anno}</span>`;
      if (kw) return `<span class="hl-keyword">${kw}</span>`;
      if (type) return `<span class="hl-type">${type}</span>`;
      if (num) return `<span class="hl-number">${num}</span>`;
      return m;
    });
  }

  static #xml(code) {
    return code
      .replace(RE_XML_COMMENT,   m => `<span class="hl-comment">${m}</span>`)
      .replace(RE_XML_TAG, (_, tag) => `&lt;<span class="hl-tag">${tag}</span>`)
      .replace(RE_XML_ATTR_NAME, m => `<span class="hl-attr-name">${m}</span>`)
      .replace(RE_XML_ATTR_VAL,  m => `<span class="hl-attr-value">${m}</span>`);
  }

  static #gradle(code) {
    return code.replace(RE_GRADLE_TOKEN, (m, comment, str, _s1, kw, num) => {
      if (comment) return `<span class="hl-comment">${comment}</span>`;
      if (str) return `<span class="hl-string">${str}</span>`;
      if (kw) return `<span class="hl-keyword">${kw}</span>`;
      if (num) return `<span class="hl-number">${num}</span>`;
      return m;
    });
  }

  static #json(code) {
    return code
      .replace(RE_JSON_KEY,     m => `<span class="hl-attr-name">${m}</span>`)
      .replace(RE_JSON_STR_VAL, m => ': ' + `<span class="hl-string">${m.slice(m.indexOf('"'))}</span>`)
      .replace(RE_JSON_BOOL,    m => `<span class="hl-boolean">${m}</span>`)
      .replace(RE_JSON_NUM,     m => `<span class="hl-number">${m}</span>`);
  }

  static #yaml(code) {
    return code
      .replace(RE_HASH_COMMENT, m => `<span class="hl-comment">${m}</span>`)
      .replace(RE_YAML_KEY,     (_, k) => `<span class="hl-attr-name">${k}</span>:`)
      .replace(RE_YAML_VAL,     m => {
        const val = m.slice(2);
        return ': ' + `<span class="hl-string">${val}</span>`;
      })
      .replace(RE_JSON_BOOL,    m => `<span class="hl-boolean">${m}</span>`)
      .replace(RE_DECIMAL_NUMS, m => `<span class="hl-number">${m}</span>`);
  }

  static #markdown(code) {
    return code
      .replace(RE_MD_H,         m => `<span class="hl-section">${m}</span>`)
      .replace(RE_MD_CODE,      m => `<span class="hl-code-inline">${m}</span>`)
      .replace(RE_MD_BOLD,      (_, t) => `<strong>${t}</strong>`)
      .replace(RE_MD_ITALIC,    (_, t) => `<em>${t}</em>`)
      .replace(RE_MD_BULLET,    m => `<span class="hl-bullet">${m}</span>`)
      .replace(RE_MD_LINK,      (_, text, url) => `<span class="hl-method">[${text}]</span>(<span class="hl-string">${url}</span>)`);
  }

  static #properties(code) {
    return code
      .replace(RE_PROP_LINE,    m => `<span class="hl-comment">${m}</span>`)
      .replace(RE_PROP_KEY,     (_, k) => `<span class="hl-attr-name">${k}</span>=`)
      .replace(RE_PROP_VAL,     (_, v) => `=<span class="hl-string">${v}</span>`);
  }

  static #shell(code) {
    return code.replace(RE_SHELL_TOKEN, (m, comment, str, _s1, variable, kw) => {
      if (comment) return `<span class="hl-comment">${comment}</span>`;
      if (str) return `<span class="hl-string">${str}</span>`;
      if (variable) return `<span class="hl-variable">${variable}</span>`;
      if (kw) return `<span class="hl-keyword">${kw}</span>`;
      return m;
    });
  }
}
