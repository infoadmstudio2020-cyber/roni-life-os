/** device.* — শুধু যাচাইকৃত action। */
import { available, call } from '../native-runtime.js';

export async function getInfo() {
  if (!available()) return null;
  try {
    return await call('device.getInfo', {});
  } catch (error) {
    return null;
  }
}

export async function getBattery() {
  if (!available()) return null;
  try {
    return await call('device.getBattery', {});
  } catch (error) {
    return null;
  }
}
