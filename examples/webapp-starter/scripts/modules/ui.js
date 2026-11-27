/** ui.* — toast, confirm, loading। */
import { toast, confirm, loading } from '../native-runtime.js';

export { toast, confirm, loading };

export async function setStatusBar(color, lightIcons = true) {
  const { available, call } = await import('../native-runtime.js');
  if (!available()) return false;
  try {
    await call('ui.setStatusBar', { color, lightIcons });
    return true;
  } catch (error) {
    return false;
  }
}
