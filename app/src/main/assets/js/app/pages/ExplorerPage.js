/**
 * ExplorerPage — wraps ExplorerModule for the sidebar page view.
 */
import { ExplorerModule } from '../modules/explorer/ExplorerModule.js';

let _explorerInstance = null;

export class ExplorerPage {
  async init(container) {
    if (!_explorerInstance) {
      _explorerInstance = new ExplorerModule();
    }
    _explorerInstance.init(container);
  }

  destroy() {
    _explorerInstance?.destroy?.();
  }
}

export function getExplorer() {
  if (!_explorerInstance) {
    _explorerInstance = new ExplorerModule();
  }
  return _explorerInstance;
}
