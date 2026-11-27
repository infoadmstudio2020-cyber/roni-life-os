/**
 * EditorPage — wraps EditorModule for the sidebar page view.
 */
import { EditorModule } from '../modules/editor/EditorModule.js';
import { eventBus } from '../core/EventBus.js';

let _editorInstance = null;

export class EditorPage {
  async init(container, params = {}) {
    container.classList.add('page-editor');
    if (!_editorInstance) {
      _editorInstance = new EditorModule();
    }
    _editorInstance.init(container, params);
  }

  destroy() {
    const container = document.getElementById('main-content');
    container?.classList.remove('page-editor');
    if (_editorInstance?.destroy) {
      _editorInstance.destroy();
    }
    // Retain _editorInstance so open tabs, active file, and undo history persist across navigations
  }
}

/** Get or create the shared editor instance. */
export function getEditor() {
  if (!_editorInstance) {
    _editorInstance = new EditorModule();
  }
  return _editorInstance;
}
