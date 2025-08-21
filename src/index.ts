import WG from './wireguard';

export default WG;

export const ping = () => WG.ping();
export const connect = (config: string) => WG.connect(config);
export const disconnect = () => WG.disconnect();
export const getState = () => WG.getState();

export type { State } from '../specs/NativeWireGuard';
