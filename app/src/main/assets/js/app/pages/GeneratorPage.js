/**
 * GeneratorPage — wraps GeneratorModule for the sidebar page view.
 */
import { GeneratorModule } from '../modules/generator/GeneratorModule.js';

export class GeneratorPage {
  async init(container, params = {}) {
    const gen = new GeneratorModule();
    gen.init(container, params);
  }
}
