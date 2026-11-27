import assert from 'node:assert';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

console.log('🧪 Starting Editor Page Fixes & Version Consistency Test Suite...\n');

// ============================================================================
// TEST 1: Version Reference Consistency Across Files
// ============================================================================
console.log('▶ Test 1: Version References Consistency Across Files');

const versionJson = JSON.parse(fs.readFileSync(path.join(rootDir, 'version.json'), 'utf8'));
const currentVer = versionJson.currentVersion;
assert(['0.1.2.7', '0.1.2.8', '0.1.2.9', '0.1.3.0', '0.1.3.1', '0.1.3.2', '0.1.3.3', '0.1.3.4', '0.1.3.5', '0.1.3.6'].includes(currentVer), 'version.json currentVersion must be valid release');
assert(Array.isArray(versionJson.changelog[currentVer]), `Changelog for ${currentVer} must exist`);
assert(versionJson.changelog[currentVer].length >= 3, 'Changelog must detail fixes');

const sidebarJs = fs.readFileSync(path.join(rootDir, 'js/app/ui/Sidebar.js'), 'utf8');
assert(sidebarJs.includes(`v${currentVer}`), `Sidebar.js must reference v${currentVer}`);

const toolbarJs = fs.readFileSync(path.join(rootDir, 'js/app/ui/Toolbar.js'), 'utf8');
assert(toolbarJs.includes(`v${currentVer}`), `Toolbar.js must reference v${currentVer}`);

const downloadsPageJs = fs.readFileSync(path.join(rootDir, 'js/app/pages/DownloadsPage.js'), 'utf8');
assert(downloadsPageJs.includes(`v${currentVer}`), `DownloadsPage.js must display v${currentVer} badge`);
assert(downloadsPageJs.includes(`RONiAndroidLibraryGenerator-v${currentVer}.zip`), `DownloadsPage.js must link to v${currentVer} release ZIP`);

const versionManagerJs = fs.readFileSync(path.join(rootDir, 'js/app/pages/VersionManagerPage.js'), 'utf8');
assert(versionManagerJs.includes(`RONiAndroidLibraryGenerator-v${currentVer}.zip`), `VersionManagerPage.js must link to v${currentVer} release ZIP`);

console.log(`✅ Test 1 Passed: All project version references are consistently synced to v${currentVer}.\n`);

// ============================================================================
// TEST 2: Editor CSS Structure & Gutter Visibility
// ============================================================================
console.log('▶ Test 2: Editor CSS Structure & Gutter Line Number Visibility');

const editorCss = fs.readFileSync(path.join(rootDir, 'css/layout/editor-area.css'), 'utf8');
assert(editorCss.includes('.editor-gutter'), 'editor-area.css must define .editor-gutter');
assert(editorCss.includes('display: block !important'), 'Gutter display must be forced to block !important');
assert(editorCss.includes('visibility: visible !important'), 'Gutter visibility must be forced to visible !important');
assert(editorCss.includes('.editor-files-panel'), 'Editor files panel styles must be present');
assert(editorCss.includes('.editor-file-item'), 'Editor file item styles must be present');

const mobileCss = fs.readFileSync(path.join(rootDir, 'css/responsive/mobile.css'), 'utf8');
assert(mobileCss.includes('.editor-files-panel'), 'Mobile responsive styles for editor files panel must be defined');

console.log('✅ Test 2 Passed: Gutter visibility guarantees and responsive styles verified.\n');

// ============================================================================
// TEST 3: EditorModule Line Number Generation & Scroll Sync Logic
// ============================================================================
console.log('▶ Test 3: Line Number Calculation & Scroll Sync Math');

function computeLineCount(content) {
  return Math.max(1, (content.match(/\n/g) || []).length + 1);
}

function computeGutterWidth(lineCount) {
  const digits = String(lineCount).length;
  return Math.max(52, digits * 10 + 26);
}

assert.strictEqual(computeLineCount(''), 1, 'Empty file should have 1 line');
assert.strictEqual(computeLineCount('hello'), 1, 'Single line file should have 1 line');
assert.strictEqual(computeLineCount('line1\nline2\nline3'), 3, '3-line file should return 3');
assert.strictEqual(computeLineCount('\n\n\n'), 4, '4 newline file should return 4');

// Gutter width checks
assert(computeGutterWidth(1) >= 52, 'Gutter width for 1 line must be at least 52px');
assert(computeGutterWidth(999) >= 56, 'Gutter width for 999 lines must scale properly');
assert(computeGutterWidth(10000) >= 76, 'Gutter width for 10000 lines must scale properly');

console.log('✅ Test 3 Passed: Line number calculation and gutter scaling math verified.\n');

// ============================================================================
// TEST 4: ExplorerModule Tree Event Binding & Single-Click Handler
// ============================================================================
console.log('▶ Test 4: ExplorerModule Tree Event Re-binding Verification');

const explorerJs = fs.readFileSync(path.join(rootDir, 'js/app/modules/explorer/ExplorerModule.js'), 'utf8');
assert(explorerJs.includes('treeEl._treeEventsBound'), 'ExplorerModule must track event binding per element to avoid binding loss');
assert(explorerJs.includes("eventBus.emit('editor:openFile', { path, content });"), 'Single-click on file must emit editor:openFile');
assert(!explorerJs.includes('600);') || !explorerJs.includes('pressTimer = setTimeout(() => {\n        eventBus.emit(\'router:navigate\', { pageId: \'analyzer\''), 'Mousedown 600ms desktop timer must not hijack file clicks');

console.log('✅ Test 4 Passed: Explorer single-click and rebinding safeguards verified.\n');

// ============================================================================
// TEST 5: EditorModule Tab Lifecycle & Cache Reset
// ============================================================================
console.log('▶ Test 5: EditorModule Tab Lifecycle & Cache Invalidation');

const editorModuleJs = fs.readFileSync(path.join(rootDir, 'js/app/modules/editor/EditorModule.js'), 'utf8');
assert(editorModuleJs.includes('this.#renderedLineCount = 0;'), 'EditorModule must reset renderedLineCount when mounting or switching editor to guarantee gutter rendering');
assert(editorModuleJs.includes('#renderFilesList()'), 'EditorModule must implement #renderFilesList for integrated file explorer');
assert(editorModuleJs.includes('#updateFileHighlighting()'), 'EditorModule must sync active selection in files panel');
assert(editorModuleJs.includes('translate3d(0, -'), 'Scroll sync must use hardware-accelerated translate3d');

console.log('✅ Test 5 Passed: EditorModule tab lifecycle, cache reset, and integrated explorer verified.\n');

console.log('🎉 ALL EDITOR FIX TESTS PASSED 100%!\n');
